package pt.ulisboa.tecnico.cnv.server;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.*;

import BIT.*;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.regions.Regions;

import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.cnv.solver.Solver;
import pt.ulisboa.tecnico.cnv.solver.SolverFactory;

import javax.imageio.ImageIO;

public class WebServer {

	static ServerArgumentParser sap = null;
	static final AmazonDynamoDB ddb = AmazonDynamoDBClientBuilder.standard()
		.withRegion(Regions.US_EAST_1)
		.build();  


	public static void main(final String[] args) throws Exception {
		
		try {
			// Get user-provided flags.
			WebServer.sap = new ServerArgumentParser(args);
		}
		catch(Exception e) {
			System.out.println(e);
			return;
		}

		System.out.println("> Finished parsing Server args.");

		//final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8000), 0);

		final HttpServer server = HttpServer.create(new InetSocketAddress(WebServer.sap.getServerAddress(), WebServer.sap.getServerPort()), 0);



		server.createContext("/scan", new MyHandler());

		// be aware! infinite pool of threads!
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();

		System.out.println(server.getAddress().toString());
	}

	static class MyHandler implements HttpHandler {
		@Override
		public void handle(final HttpExchange t) throws IOException {

			// Get the query.
			final String query = t.getRequestURI().getQuery();

			System.out.println("> Query:\t" + query);

			// Break it down into String[].
			final String[] params = query.split("&");

			/*
			for(String p: params) {
				System.out.println(p);
			}
			*/

			// Store as if it was a direct call to SolverMain.
			final ArrayList<String> newArgs = new ArrayList<>();
			for (final String p : params) {
				final String[] splitParam = p.split("=");

				if(splitParam[0].equals("i")) {
					splitParam[1] = WebServer.sap.getMapsDirectory() + "/" + splitParam[1];
				}

				newArgs.add("-" + splitParam[0]);
				newArgs.add(splitParam[1]);

				/*
				System.out.println("splitParam[0]: " + splitParam[0]);
				System.out.println("splitParam[1]: " + splitParam[1]);
				*/
			}

			if(sap.isDebugging()) {
				newArgs.add("-d");
			}


			// Store from ArrayList into regular String[].
			final String[] args = new String[newArgs.size()];
			int i = 0;
			for(String arg: newArgs) {
				args[i] = arg;
				i++;
			}

			String requestId = null;
			
			try {
				requestId = parseId(newArgs);
				System.out.println(requestId);
			} catch (Exception e) {
				e.printStackTrace();
			}

			
			

			/*
			for(String ar : args) {
				System.out.println("ar: " + ar);
			} */



			// Create solver instance from factory.
			final Solver s = SolverFactory.getInstance().makeSolver(args);

			if(s == null) {
				System.out.println("> Problem creating Solver. Exiting.");
				System.exit(1);
			}

			// Write figure file to disk.
			File responseFile = null;
			try {

				final BufferedImage outputImg = s.solveImage();

				final String outPath = WebServer.sap.getOutputDirectory();

				final String imageName = s.toString();

				/*
				if(ap.isDebugging()) {
					System.out.println("> Image name: " + imageName);
				} */

				final Path imagePathPNG = Paths.get(outPath, imageName);
				ImageIO.write(outputImg, "png", imagePathPNG.toFile());

				responseFile = imagePathPNG.toFile();

			} catch (final FileNotFoundException e) {
				e.printStackTrace();
			} catch (final IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}



			// Send response to browser.
			final Headers hdrs = t.getResponseHeaders();

			

			hdrs.add("Content-Type", "image/png");

			hdrs.add("Access-Control-Allow-Origin", "*");
			hdrs.add("Access-Control-Allow-Credentials", "true");
			hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
			hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
			
			t.sendResponseHeaders(200, responseFile.length());

			final OutputStream os = t.getResponseBody();
			Files.copy(responseFile.toPath(), os);


			os.close();

			long threadId = Thread.currentThread().getId();
			System.out.println("THREAD: " + threadId);
			Long loadCount = new Long(CustomTool.getLoadcount(threadId));
			Long storeCount = new Long(CustomTool.getStorecount(threadId));

			System.out.println("> Sent response to " + t.getRemoteAddress().toString());
			System.out.println("Load count: " + loadCount);
			System.out.println("Store count: " + storeCount);

			if(requestId != null) {
				addItem(requestId, new Metrics(loadCount, storeCount));
			}

			CustomTool.clearMetrics(threadId);
		}

		private static String parseId(List<String> args) throws Exception {
			String s = null;
			String w = null;
			String h = null;
			int index = 0;
			for(String arg: args) {
				if(arg.equals("-s")) {
					s = args.get(index + 1);
				} else if (arg.equals("-w")) {
					w = args.get(index + 1);
				} else if (arg.equals("-h")) {
					h = args.get(index + 1);
				}
				index++;
			}

			if(s == null || w == null || h == null) {
				throw new Exception("invalid args");
			}

			return w + h + s; 
		}


		private static void addItem(String requestId, Metrics metrics) {

			String table_name = "cnv-metrics";

			HashMap<String, AttributeValue> item_values =
				new HashMap<String, AttributeValue>();
			
	
			item_values.put("requestId", new AttributeValue(requestId));
			item_values.put("loadCount", new AttributeValue(String.valueOf(metrics.loadCount)));
			item_values.put("storeCount", new AttributeValue(String.valueOf(metrics.storeCount)));

			try {
				ddb.putItem(table_name, item_values);
				System.out.println("ADDED METRIC: " + requestId);
			} catch (ResourceNotFoundException e) {
				System.err.format("Error: The table \"%s\" can't be found.\n", table_name);
				System.err.println("Be sure that it exists and that you've typed its name correctly!");
				System.exit(1);
			} catch (AmazonServiceException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			} catch (Exception e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
		}
	}

	public static class Metrics {
		Long loadCount;
		Long storeCount;

		public Metrics(Long loadCount, Long storeCount) {
			this.loadCount = loadCount;
			this.storeCount = storeCount;
		}
	}



}
