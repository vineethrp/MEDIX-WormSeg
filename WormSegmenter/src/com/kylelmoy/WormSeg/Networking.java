/*  Copyright (C) 2016 Kyle Moy

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.kylelmoy.WormSeg;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Kyle Moy
 *
 */
public class Networking {
	private static HashMap<String, String> config;
	private static double[] SEG_CONFIG = new double[] {640,	//width
													   480,	//height
													   200,	//area_min
													   400,	//area_max
													   100,	//search_win_size
													   3,		//blur_radius
													   25,		//threshold_win_size
													   0.9		//threshold_ratio
	};
	private static String[] SEG_ARGS = {"width",
										"height",
										"area_min",
										"area_max",
										"search_win_size",
										"blur_radius",
										"threshold_win_size",
										"threshold_ratio"
	};
	public static void main(String[] args) {
		if(args.length == 0) {
			System.out.println("No arguments specified. Please refer to user guide.");
			return;
		}
		
		System.out.println("Welcome to WormSeg");
		System.out.println("Current Date: " + (new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")).format(new Date()));
		System.out.println("JVM Heap Size: " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + "MB");
		
		config = new HashMap<String, String>();
		System.out.println("Parameters specified:");
		for (int i = 0; i < args.length; i+=2) {
			String key = args[i].replace("-", "");
			String value;
			if (i + 1 > args.length) {
				value = "";
			} else if (args[i+1].contains("-")) {
				value = "";
				i--;
			} else {
				value = args[i+1].toLowerCase();
			}
			config.put(key, value);
			System.out.println("\t" + args[i].replace("-", "") + ": " + args[i+1].toLowerCase());
		}
		if (config.get("mode").equals("tasker")) {
			host();
		} else if (config.get("mode").equals("worker")) {
			node();
		} else if (config.get("mode").equals("terminate")) {
			terminate();
		} else {
			System.err.println("No mode specified! Include argument '-mode [tasker|worker|terminate]'");
		}
	}

	/**
	 * Entry point for terminator
	 */
	private static void terminate() {
		int serverPort;
		if (config.get("port") == null) {
			serverPort = 8190;
		} else {
			try {
				serverPort = Integer.parseInt(config.get("port"));
			} catch (NumberFormatException e) {
				System.err.println("\tInvalid port.");
				return;
			}
		}
		System.out.println("Gathering nodes for termination.");
		NodeHandler nodeHandler = new NodeHandler(serverPort);
		nodeHandler.gatherNodes(1);
		nodeHandler.terminate();
	}
	/**
	 * Entry point for host 
	 */
	private static byte[][] file;
	private static void host() {
		String inputPath = null;
		String outputPath = null;
		String fileExtension;
		
		int padding;
		int serverPort;
		int numFrames;
		int numThreads;
		int numNodes;
		
		//Read arguments
		if (config.get("project") != null) {
			String project = config.get("project");
			String dir;
			if (config.get("dir") == null) {
				dir = "//medixsrv/Nematodes/data/";
			} else {
				dir = config.get("dir");
			}
			
			inputPath = dir + project + "/input/";
			outputPath = dir + project + "/log/feature.log";
		}
		if (config.get("input") == null) {
			if (inputPath == null) {
				System.err.println("\tNo input directory specified! Include arugment '-input [directory path]");
				return;
			}
		} else {
			inputPath = config.get("input");
			if (!(new File(inputPath)).isDirectory()) {
				System.err.println("\t\"" + inputPath + "\" is not a directory!");
				return;
			}
		}
		
		if (config.get("output") == null) {
			if (outputPath == null) {
				System.err.println("\tNo output file specified! Include arugment '-output [file path]");
				return;
			}
		} else {
			outputPath = config.get("output");
			if ((new File(outputPath)).exists()) {
				System.out.println("\tWARNING: \"" + outputPath + "\" already exists! This file will be overwritten.");
			}
		}
		if (config.get("port") == null) {
			serverPort = 8190;
			System.out.println("\tport: " + serverPort + " (DEFAULT)");
		} else {
			try {
				serverPort = Integer.parseInt(config.get("port"));
			} catch (NumberFormatException e) {
				System.err.println("\tInvalid port.");
				return;
			}
		}

		if (config.get("threads") == null) {
			numThreads = Runtime.getRuntime().availableProcessors();
			System.out.println("\tthreads:" + numThreads + " (DEFAULT)");
		} else {
			try {
				numThreads = Integer.parseInt(config.get("threads"));
				System.out.println("\tthreads:" + numThreads);
			} catch (Exception e) {
				System.err.println("Invalid thread count.");
				return;
			}
		}

		if (config.get("nodes") == null) {
			numNodes = 1;
			System.out.println("\tnodes:" + numNodes + " (DEFAULT)");
		} else {
			try {
				numNodes = Integer.parseInt(config.get("nodes"));
				System.out.println("\tnodes:" + numNodes);
			} catch (Exception e) {
				System.err.println("Invalid nodes count.");
				return;
			}
		}

		if (config.get("extension") != null) {
			fileExtension = config.get("extension");
			System.out.println("\textension: " + fileExtension);
		} else {
			fileExtension = ".jpg";
			System.out.println("\textension: " + fileExtension + " (DEFAULT)");
		}
		
		if (config.get("padding") != null) {
			padding = Integer.parseInt(config.get("padding"));
			System.out.println("\tpadding: " + padding);
		} else {
			padding = 7;
			System.out.println("\tpadding: " + padding + " (DEFAULT)");
		}
		
		if (config.get("frames") != null) {
			numFrames = Integer.parseInt(config.get("frames"));
			if (!(new File(inputPath + String.format("%0" + padding + "d", numFrames-1) + fileExtension).exists())) {
				System.err.println("No file found at \"" +
				(inputPath + String.format("%0" + padding + "d", numFrames-1) + fileExtension) +
				"\". Either frames exceeds file count, or input path is incorrect!");
			}
			System.out.println("\tframes: " + numFrames);
		} else {
			numFrames = (new File(inputPath)).list().length;
			while (!(new File(inputPath + String.format("%0" + padding + "d", numFrames-1) + fileExtension).exists())) {
				numFrames--;
			}
			System.out.println("\tframes: " + numFrames + " (DEFAULT ALL)");
		}
		
		System.out.println("Segmentation Parameters:");
		for (int i = 0; i < SEG_ARGS.length; i++) {
			if (config.get(SEG_ARGS[i]) != null) {
				try {
					SEG_CONFIG[i] = Double.parseDouble(config.get(SEG_ARGS[i]));
					System.out.println("\t" + SEG_ARGS[i] + ": " + SEG_CONFIG[i]);
				} catch (NumberFormatException e) {
					System.err.println("Invalid input! " + SEG_ARGS[i] + ": " + config.get(SEG_ARGS[i]));
				}
			} else {
				System.out.println("\t" + SEG_ARGS[i] + ": " + SEG_CONFIG[i] + " (DEFAULT)");
			}
		}
		
		//For benchmarking
		long time = System.currentTimeMillis();
		long epoch = System.currentTimeMillis();
		
		//Preload data
		System.out.print("Loading input data... ");
		file = new byte[numFrames][];
		try{
			for (int i = 0; i < numFrames; i++) {
				file[i] = Files.readAllBytes(Paths.get(inputPath + String.format("%0" + padding + "d", i) + fileExtension));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Complete. +(" + formatTime(System.currentTimeMillis() - time) + ")");
		time = System.currentTimeMillis();
		
		System.out.println("Gathering " + numNodes + " nodes... ");
		//Start listening for nodes
		NodeHandler nodeHandler = new NodeHandler(serverPort);
		nodeHandler.gatherNodes(numNodes);
		System.out.println("Complete. +(" + formatTime(System.currentTimeMillis() - time) + ")");
		time = System.currentTimeMillis();
		
		System.out.print("Processing... ");
		//Distribute workload evenly across all nodes
		nodeHandler.assignTask(numFrames);
		
		//Send input, receive output
		nodeHandler.startTask(numThreads, true);

		System.out.println("Complete. +(" + formatTime(System.currentTimeMillis() - time) + ")");
		time = System.currentTimeMillis();
		//Write results to disk
		
		
		System.out.print("Writing results to " + outputPath + "... ");
		//Compile results from nodes
		int columns = FeatureExtractor.OUTPUT_COLUMNS;
		PrintWriter out;
		try {
			out = new PrintWriter(new File(outputPath));
		} catch (FileNotFoundException e) {
			//How tragic...
			try {
				String fName = "RESULT_DUMP" + System.currentTimeMillis() + ".log";
				out = new PrintWriter(new File(fName));
				e.printStackTrace();
				System.err.println("File error occured. Dumping results to " + fName + ".");
			} catch (FileNotFoundException e1) {
				//How really, really tragic.
				e1.printStackTrace();
				return;
			}
		}
		
		List<Node> nodes = nodeHandler.getNodes();
		int l = 0;
		String line;
		for (Node node : nodes) {
			double[][] workerOutput = node.getResult();
			for (int j = 0; j < workerOutput.length; j++) {
				line = "" + l++;
				for (int k = 0; k < columns; k++) {
					line += "," + workerOutput[j][k];
				}
				out.println(line);
			}
		}
		out.close();

		System.out.println("Complete. Total elapsed time [" + formatTime(System.currentTimeMillis() - epoch) + "]");
		
	}

	/**
	 * Dress up milliseconds
	 * @param time
	 * @return
	 */
	private static String formatTime(long time) {
		long second = (time / 1000) % 60;
		long minute = (time / (1000 * 60)) % 60;
		long hour = (time / (1000 * 60 * 60)) % 24;

		return String.format("%02d:%02d:%02d:%03d", hour, minute, second, time % 1000);
	}
	
	/**
	 * Handles networking with Nodes
	 *
	 */
	private static class Node implements Runnable {
		private int start;
		private int end;
		private int size;
		private final Socket socket;
		private DataOutputStream os;
		private DataInputStream is;
		
		private double[][] OUTPUT;
		
		public Node(Socket s) {
			socket = s;
			try {
				os = new DataOutputStream(socket.getOutputStream());
				is = new DataInputStream(socket.getInputStream());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		public void initialize(int s, int e) {
			start = s;
			end = e;
			size = end - start;
		}
		
		public double[][] getResult() {
			return OUTPUT;
		}
		
		/**
		 * Tell nodes to quit
		 */
		public void terminateRemote() {
			try {
				os.writeInt(-1);
			} catch (IOException e) {}
		}
		
		@Override
		public void run() {
			try {
				//Send size
				os.writeInt(size);
				
				//Send segmentation parameters
				for (int i = 0; i < SEG_CONFIG.length; i++) {
					os.writeDouble(SEG_CONFIG[i]);
				}
				
				//Send frame data
				for (int i = start; i < end; i++) {
					//byte[] data = file[i];//Files.readAllBytes(Paths.get(input + String.format("%0" + padding + "d", f++) + extension));
					os.writeInt(file[i].length);
					os.write(file[i]);
				}
				//System.out.println("Done sending files to " + s.getRemoteSocketAddress());
				
				//Get results
				int columns = FeatureExtractor.OUTPUT_COLUMNS;
				OUTPUT = new double[size][columns];
				for (int i = 0; i < size; i++) {
					for (int j = 0; j < columns; j++) {
						OUTPUT[i][j] = is.readDouble();
					}
				}
			
				//Done
			} catch (IOException e) {
				System.err.println("Error on socket with " + socket.getRemoteSocketAddress());
			}
		}
	}
	
	/**
	 * Listens for incoming Node connections
	 *
	 */
	private static class NodeHandler implements Runnable{
		private final int port;
		private boolean run = true;
		private ServerSocket serverSocket;
		private List<Node> nodes;
		private Thread thread;
		
		public NodeHandler(int p) {
			port = p;
			nodes = new ArrayList<Node>();
		}
		
		@Override
		public void run() {
			try {
				serverSocket = new ServerSocket(port);
				//This creates a new thread to call heartbeat() every 500 ms
				(new Thread() { public void run() { while(run){heartbeat(); try{Thread.sleep(500);}catch(Exception e){}}}}).start();
				
				//Listen for new nodes
				while (run) {
					try {
						Socket nodeSocket = serverSocket.accept();
						nodes.add(new Node(nodeSocket));
						System.out.println(nodeSocket.getRemoteSocketAddress() + " added to nodes. Total nodes: " + nodes.size());
					} catch (IOException e) {}
				}
				serverSocket.close();
			} catch (IOException e) {}
		}
		
		/**
		 * Tells nodes to end process
		 */
		public void terminate() {
			for (Node node : nodes) {
				node.terminateRemote();
			}
		}
		
		/**
		 * Begins listening for nodes, and waits until at LEAST numNodes nodes join
		 * @param numNodes
		 */
		public void gatherNodes(int numNodes) {
			start();
			while (nodes.size() < numNodes) {
				try{ Thread.sleep(1000); } catch (Exception e) {}
			}
			//Wait just a bit in case another node wants in on the fun
			try{ Thread.sleep(1000); } catch (Exception e) {}
			stop();
		}
		
		/**
		 * Start listening for node connections
		 */
		public void start() {
			if (thread == null || !thread.isAlive()) {
				thread = new Thread(this);
				thread.start();
			}
		}
		
		/**
		 * Stop listening
		 */
		public void stop() {
			try {
				run = false;
				serverSocket.close();
				thread.join();
			} catch (Exception e) {}
		}
		
		/**
		 * Send a heartbeat message (0) to all nodes. Remove closed connections.
		 */
		private void heartbeat() {
			synchronized (nodes) {
				if (nodes.size() > 0) {
					for (int i = 0; i < nodes.size(); i++) {
						Socket s = nodes.get(i).socket;
						try {
							new DataOutputStream(s.getOutputStream()).writeInt(0);
						} catch (Exception e) {
							nodes.remove(i--);
							System.out.println(s.getRemoteSocketAddress() + " removed from nodes. Total nodes: " + nodes.size());
						}
					}
				}
			}
		}
		
		/**
		 * Get the list of all connected nodes.
		 * @return NodeHandlers
		 */
		public List<Node> getNodes() {
			heartbeat();
			return nodes;
		}
		
		/**
		 * Assigns each node a part of the data to be processed
		 * @param size Size of the data
		 */
		public void assignTask(int size) {
			int framesPerNode = (int)Math.ceil(size / (double)nodes.size());
			int f = 0;
			for (Node node : nodes) {
				node.initialize(f, f + framesPerNode);
				
				f += framesPerNode;
				if (f + framesPerNode > size) {
					framesPerNode -= (f + framesPerNode - size);
				}
			}
		}
		
		/**
		 * Starts distributing workload to nodes, optionally waits for their response.
		 * @param threads
		 * @param wait
		 */
		public void startTask(int threads, boolean wait) {
			ExecutorService executor = Executors.newFixedThreadPool(threads);
			for (Node node : nodes) {
				executor.submit(node);
			}
			executor.shutdown();
			
			if (wait) {
				while (!executor.isTerminated()) {
					try{ Thread.sleep(1000); } catch (Exception e) {}
				}
			}
		}
	}
	/**
	 * Entry point for node
	 */
	private static void node() {
		String hostAddress;
		int numThreads;
		int hostPort;
		
		//Read arguments
		System.out.println("Node Parameters:");
		if (config.get("host") == null) {
			hostAddress = "localhost";
			System.out.println("\thost:" + hostAddress + " (DEFAULT)");
		} else {
			hostAddress = config.get("host");
			System.out.println("\thost:" + hostAddress);
		}
		
		if (config.get("port") == null) {
			hostPort = 8190;
			System.out.println("\tport:" + hostPort + " (DEFAULT)");
		} else {
			try {
				hostPort = Integer.parseInt(config.get("port"));
				System.out.println("\tport:" + hostPort);
			} catch (Exception e) {
				System.err.println("Invalid port.");
				return;
			}
		}
		
		if (config.get("threads") == null) {
			numThreads = Runtime.getRuntime().availableProcessors();
			System.out.println("\tthreads:" + numThreads + " (DEFAULT)");
		} else {
			try {
				numThreads = Integer.parseInt(config.get("threads"));
				System.out.println("\tthreads:" + numThreads);
			} catch (Exception e) {
				System.err.println("Invalid thread count.");
				return;
			}
		}
		
		//Connect to host
		Socket socket;
		DataInputStream is;
		DataOutputStream os;
		
		while (true) {
			System.out.println("Attempting to connect to " + hostAddress + ".");
			while (true) {
				try {
					socket = new Socket(hostAddress, hostPort);
					is = new DataInputStream(socket.getInputStream());
					os = new DataOutputStream(socket.getOutputStream());
					System.out.println("Connection to " + hostAddress + " made.");
					try {
						while (true) {
							System.out.println("Waiting for work.");
							
							//Wait for go time! 
							int size;
							while (true) {
								size = is.readInt();
								if (size > 0) {
									//IT'S GO TIME WOOOO
									System.out.println("Incoming data length: " + size);
									break;
								} else if (size < 0) {
									//Time to die :c
									return;
								}
							}
							
							//Receive config
							System.out.println("Segmentation parameters:");
							for (int i = 0; i < SEG_CONFIG.length; i++) {
								SEG_CONFIG[i] = is.readDouble();
								System.out.println("\t" + SEG_ARGS[i] + ": " + SEG_CONFIG[i]);
							}

							System.out.println("Receiving data.");
							
							//Receive data
							List<InputStream> input = new ArrayList<InputStream>(size);
							for (int i = 0; i < size; i++) {
								int s = is.readInt();
								byte[] data = new byte[s];
								is.readFully(data);
								input.add(new ByteArrayInputStream(data));
							}
							
							System.out.println("All data recieved.");
							
							//Make worker threads
							System.out.println("Beginning segmentation.");
							int framesPerThread = (int)Math.ceil(size / (double)numThreads);
							ExecutorService executor = Executors.newFixedThreadPool(numThreads);
							FeatureExtractor[] workerPool = new FeatureExtractor[numThreads];
							int f = 0;
							for (int i = 0; i < numThreads; i++) {
								int fSize = framesPerThread;
								if (f + fSize > size) fSize -= f + fSize - size;
								List<InputStream> workload = input.subList(f, f + fSize);
								f += fSize;
								workerPool[i] = new FeatureExtractor(workload, SEG_CONFIG);
								executor.submit(workerPool[i]);
							}
							executor.shutdown();
							
							// Wait until all threads are finish
							while (!executor.isTerminated()) {
								try{ Thread.sleep(1000); } catch (Exception e) {}
							}
							System.out.println("Segmentation complete.");

							//Compile results from threads
							//Also send to host! Efficiency wooo!
							System.out.println("Sending results to host.");
							int columns = FeatureExtractor.OUTPUT_COLUMNS;
							f = 0;
							for (int i = 0; i < numThreads; i++) {
								double[][] workerOutput = workerPool[i].getResult();
								for (int j = 0; j < workerOutput.length; j++) {
									for (int k = 0; k < columns; k++) {
										os.writeDouble(workerOutput[j][k]);
									}
								}
							}
							System.out.println("Task complete.");
							//Done
						}
					} catch (IOException e) {
						System.out.println("Connection to " + hostAddress + " lost.");
						socket.close();
					}
				} catch (UnknownHostException e) {
					System.err.println("Unable to resolve host.");
				} catch (IOException e) {
					//Do nothing, try again later.
				}
				try {Thread.sleep(1000);}catch(Exception s) {}
			}
		}
	}
}
