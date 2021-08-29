import requests
from flask import Flask, redirect
from flask import request
from flask import jsonify
from flask import Response
import boto3
from boto3 import ec2
from boto3 import dynamodb
from sys import maxsize
import random
import apscheduler
from uuid import uuid4
import time

def getStats(id):
    dynamodb = boto3.resource('dynamodb')
    table = dynamodb.Table('cnv-stats')
    response = table.get_item(Key={'id': id})
    item = response.get('Item')
    return int(item.get('value'))

def writeStats(id, value):
    dynamodb = boto3.resource('dynamodb')
    table = dynamodb.Table('cnv-stats')
    response = table.put_item(
       Item={
            'id': id,
            'value': value
        })
    item = response.get('stats')
    return item 


app = Flask(__name__)

instanceData = {}
queue = []
threshold = 0.7

if(getStats('minLoadCount') == None):
    writeStats('minLoadCount', maxsize)

if(getStats('maxLoadCount') == None):
    writeStats('maxLoadCount', 0)

if(getStats('minStoreCount') == None):
    writeStats('minStoreCount', maxsize)

if(getStats('maxStoreCount') == None):
    writeStats('maxStoreCount', 0)

@app.route('/scan')
def requestHandler():
    global instanceData, queue, threshold
    query = request.query_string.decode("utf-8")
    params = dict([i.split("=") for i in query.split("&")])

   
    requestId = params.get('w') + params.get('h') + params.get('s')
    item = dynamoDbHas(requestId)
    if item == None :
        res = getNoneRes(params)
    else:
        load = eval(item.get('loadCount'))
        store = eval(item.get('storeCount'))
        res = calculateLoad(load, store)
    
    retries = 5
    while(retries != 0):
        instanceRes = processRequest(query, res)
        ip = instanceRes.public_ip_address
        try:
            print("*******************************")
            print("Request sent to: id -> " + instanceRes.id + " | ip -> " + ip)
            print("*******************************")
            print(instanceData)
            response = requests.get(buildRequestUrl(ip, query))
            if response.status_code != 200:
                print("Instance error: instance id -> " + instanceRes.id + " | status code -> " + str(response.status_code))
                instanceData[instanceRes.id] -= res
                retries -= 1
                time.sleep(5)
                continue
            instanceData[instanceRes.id] -= res
            break
        except Exception as e:
            print("Exception in instance id: " + instanceRes.id)
            print(type(e))
            print(e.args)
            instanceData[instanceRes.id] -= res
            retries -= 1
            time.sleep(5)
            response = Response()
            response.status_code = 500
            response.content = "Unexpected Error Occurred"
    
    headers = [(name, value) for (name, value) in response.raw.headers.items()
               if name.lower()]
    return Response(response.content, response.status_code, headers)


def processRequest(query, res):
    global instanceData, threshold, queue
    availableInstances = getAvailableInstances()
    lowestInstance = getInstanceWithLowestLoad(availableInstances)
    instanceRes = lowestInstance[0]
    instancesWithoutRequests = lowestInstance[1]
    inQueue = False
    uuid = 0
    if len(instancesWithoutRequests) > 0:
        instanceRes = random.choice(instancesWithoutRequests)
    else:
        if instanceRes == None or instanceData.get(instanceRes.id) + res > threshold:
            uuid = uuid4()
            queue.append((uuid, query, res))
            print("Request sent to the queue")
            inQueue = True

    if(inQueue):
        while True:
            time.sleep(3)
            if queue[0][0] != uuid:
                continue
            else:
                instanceRes = getInstanceForQueue(queue[0][2])
                if(instanceRes == None):
                    continue
                queue.pop(0)
                break

    if instanceRes.id not in instanceData.keys():
        instanceData[instanceRes.id] = 0
    
    instanceData[instanceRes.id] += res
    return instanceRes
    

def getInstanceForQueue(load):
    global instanceData
    if len(queue) == 0:
        return
    instances = getAvailableInstances()
    if len(instances) == 0:
        return
    lowestInstance = getInstanceWithLowestLoad(instances)
    instanceRes = lowestInstance[0]
    instancesWithoutRequests = lowestInstance[1]
    if len(instancesWithoutRequests) > 0:
        return random.choice(instancesWithoutRequests)
    else:
        if instanceData.get(instanceRes.id) + load <= threshold:
            return instanceRes
    return None 



def getInstanceWithLowestLoad(availableInstances):
    global instanceData
    min = maxsize
    instanceRes = None
    instancesWithoutRequests = []
    for instance in availableInstances:
        load = instanceData.get(instance.id)
        if(load == None or load < 0.05):
            instancesWithoutRequests.append(instance)
        else:
            if load < min:
                min = load
                instanceRes = instance
    return (instanceRes, instancesWithoutRequests)

def buildRequestUrl(ip, query):
    return 'http://' + ip + ':8000/scan?' + query

def dynamoDbHas(requestId):
    dynamodb = boto3.resource('dynamodb')
    table = dynamodb.Table('cnv-metrics')
    response = table.get_item(Key={'requestId': requestId})
    item = response.get('Item')
    return item

def getAvailableInstances():
    ec2 = boto3.resource('ec2')
    instances = []
    ec2.instances.all()
    for i in ec2.instances.all():
        if i.tags == None or i.state.get('Code') != 16 or i.public_ip_address == None:
            continue
        for j in i.tags:
            if j.get('Key') == 'cnv-web':
                instances.append(i)
    return instances

def getNoneRes(params):
    scan_weight = {'GREEDY_RANGE_SCAN': 0.1, 'GRID_SCAN': 1, 'PROGRESSIVE_SCAN': 0.5}
    return (eval(params.get('h')) - 512) / (2048 - 512) * 0.2 + (eval(params.get('w')) - 512) / (2048 - 512) * 0.2 + scan_weight[params.get('s')] * 0.6

def calculateLoad(load, store):
    minLoadCount = getStats('minLoadCount')
    maxLoadCount = getStats('maxLoadCount')
    minStoreCount = getStats('minStoreCount')
    maxStoreCount = getStats('maxStoreCount')

    if load < 0:
        load = maxLoadCount
    if store < 0:
        store = minLoadCount
        
    if(load < minLoadCount):
        minLoadCount = load
        writeStats('minLoadCount', load)
    if(load > maxLoadCount):
        maxLoadCount = load
        writeStats('maxLoadCount', load)

    if(store < minStoreCount):
        minStoreCount = store
        writeStats('minStoreCount', store)
    if(store > maxStoreCount):
        maxStoreCount = store
        writeStats('maxStoreCount', store)

    if(maxLoadCount == minLoadCount):
        return (load / maxLoadCount + store / maxStoreCount) / 2 + 0.2
    
    loadRes = (load - minLoadCount) / (maxLoadCount - minLoadCount)
    storeRes = (store - minStoreCount) / (maxStoreCount - minStoreCount)
    return (loadRes + storeRes) / 2 + 0.2
