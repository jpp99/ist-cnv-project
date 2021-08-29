# ist-cnv-project

This project consists on:
- Creation of a system in the cloud containing web servers running CPU-intensive tasks, and a load balancer.
- Implementation of a Java Tool to calculate the metrics of each
request and send them to DynamoDB.
- Development of a Python-based Load Balancer responsible to
gather the metrics inside DynamoDB to predict the load of the
incoming requests and take care of their distribution based on
an algorithm to decrease the wait time of the response.