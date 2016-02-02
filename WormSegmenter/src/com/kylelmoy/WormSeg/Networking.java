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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
		
		System.out.println("Welcome to WormSeg 2.0");
		System.out.println("Java Virtual Machine Heap Size: " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + "MB");
		
		config = new HashMap<String, String>();
		System.out.println("Parameters specified:");
		for (int i = 0; i < args.length; i+=2) {
			config.put(args[i].replace("-", ""), args[i+1].toLowerCase());
			System.out.println("\t" + args[i].replace("-", "") + ": " + args[i+1].toLowerCase());
		}
		if (config.get("mode").equals("host")) {
			host();
		} else if (config.get("mode").equals("node")) {
			node();
		} else {
			System.err.println("No mode specified! Include argument '-mode [host|node]'");
		}
	}
	
	/**
	 * Entry point for host 
	 */
	private static byte[][] file;
	private static void host() {
		String input = null;
		String output = null;
		String extension;
		
		int padding;
		int port;
		int frames;
		int threads;
		int startNodes;
		
		//Read arguments
		if (config.get("project") != null) {
			String project = config.get("project");
			String dir;
			if (config.get("dir") == null) {
				dir = "//medixsrv/Nematodes/data/";
			} else {
				dir = config.get("dir");
			}
			
			input = dir + project + "/input/";
			output = dir + project + "/log/feature.log";
		}
		if (config.get("input") == null) {
			if (input == null) {
				System.err.println("\tNo input directory specified! Include arugment '-input [directory path]");
				return;
			}
		} else {
			input = config.get("input");
			if (!(new File(input)).isDirectory()) {
				System.err.println("\t\"" + input + "\" is not a directory!");
				return;
			}
		}
		
		if (config.get("output") == null) {
			if (output == null) {
				System.err.println("\tNo output file specified! Include arugment '-output [file path]");
				return;
			}
		} else {
			output = config.get("output");
			if ((new File(output)).exists()) {
				System.err.println("\tWARNING: \"" + input + "\" already exists! This file will be overwritten.");
			}
		}
		if (config.get("port") == null) {
			port = 8190;
			System.out.println("\tport: " + port + " (DEFAULT)");
		} else {
			try {
				port = Integer.parseInt(config.get("port"));
			} catch (NumberFormatException e) {
				System.err.println("\tInvalid port.");
				return;
			}
		}

		if (config.get("threads") == null) {
			threads = Runtime.getRuntime().availableProcessors();
			System.out.println("\tthreads:" + threads + " (DEFAULT)");
		} else {
			try {
				threads = Integer.parseInt(config.get("threads"));
				System.out.println("\tthreads:" + threads);
			} catch (Exception e) {
				System.err.println("Invalid thread count.");
				return;
			}
		}

		if (config.get("nodes") == null) {
			startNodes = 1;
			System.out.println("\tnodes:" + startNodes + " (DEFAULT)");
		} else {
			try {
				startNodes = Integer.parseInt(config.get("nodes"));
				System.out.println("\tnodes:" + startNodes);
			} catch (Exception e) {
				System.err.println("Invalid nodes count.");
				return;
			}
		}

		if (config.get("extension") != null) {
			extension = config.get("extension");
			System.out.println("\textension: " + extension);
		} else {
			extension = ".jpg";
			System.out.println("\textension: " + extension + " (DEFAULT)");
		}
		
		if (config.get("padding") != null) {
			padding = Integer.parseInt(config.get("padding"));
			System.out.println("\tpadding: " + padding);
		} else {
			padding = 7;
			System.out.println("\tpadding: " + padding + " (DEFAULT)");
		}
		
		if (config.get("frames") != null) {
			frames = Integer.parseInt(config.get("frames"));
			if (!(new File(input + String.format("%0" + padding + "d", frames-1) + extension).exists())) {
				System.err.println("No file found at \"" +
				(input + String.format("%0" + padding + "d", frames-1) + extension) +
				"\". Either frames exceeds file count, or input path is incorrect!");
			}
			System.out.println("\tframes: " + frames);
		} else {
			frames = (new File(input)).list().length;
			while (!(new File(input + String.format("%0" + padding + "d", frames-1) + extension).exists())) {
				frames--;
			}
			System.out.println("\tframes: " + frames + " (DEFAULT ALL)");
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
		
		//Start listening for nodes
		Listener listener = new Listener(port);
		Thread listenerThread = new Thread(listener);
		listenerThread.start();
		
		//Preload data in the meantime
		System.out.println("Preloading data.");
		file = new byte[frames][];
		try{
			for (int i = 0; i < frames; i++) {
				file[i] = Files.readAllBytes(Paths.get(input + String.format("%0" + padding + "d", i) + extension));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Preloading complete.");
		if (listener.getNodes().size() < startNodes) {
			System.out.println("Waiting for " + startNodes + " nodes to finish joining.");
			while (listener.getNodes().size() < startNodes) {
				try{ Thread.sleep(1000); } catch (Exception e) {}
			}
		}
		
		//Stop listening for nodes
		try {
			listener.stop();
			listenerThread.join();
		} catch (InterruptedException e1) {}
		
		ArrayList<Socket> nodes = listener.getNodes();

		System.out.println("Beginning task.");
		long time = System.currentTimeMillis();
		int framesPerNode = (int)Math.ceil(frames / (double)nodes.size());
		int f = 0;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		NodeHandler[] nodeHandlers = new NodeHandler[nodes.size()];
		for (int i = 0; i < nodes.size(); i++ ) {
			int size = framesPerNode;
			if (f + size > frames) size -= (f + size - frames);
			int start = f;
			int end = f + size;
			f += size;
			nodeHandlers[i] = new NodeHandler(nodes.get(i), start, end);
			executor.execute(nodeHandlers[i]);
		}
		executor.shutdown();
		
		// Wait until all threads are finish
		while (!executor.isTerminated()) {
			try{ Thread.sleep(1000); } catch (Exception e) {}
		}

		//Compile results from nodes
		int columns = FeatureExtractor.OUTPUT_COLUMNS;
		PrintWriter out;
		try {
			out = new PrintWriter(new File(output));
		} catch (FileNotFoundException e) {
			//How tragic...
			e.printStackTrace();
			return;
		}
		System.out.println("Segmentation complete.");
		//Write results to disk
		System.out.println("Writing results to " + output);
		int l = 0;
		String line;
		for (int i = 0; i < nodeHandlers.length; i++) {
			double[][] workerOutput = nodeHandlers[i].getResult();
			for (int j = 0; j < workerOutput.length; j++) {
				line = "" + l++;
				for (int k = 0; k < columns; k++) {
					line += "," + workerOutput[j][k];
				}
				out.println(line);
			}
		}
		out.close();

		long millis = System.currentTimeMillis() - time;
		String elapsed = String.format("%02d min, %02d sec", 
			    TimeUnit.MILLISECONDS.toMinutes(millis),
			    TimeUnit.MILLISECONDS.toSeconds(millis) - 
			    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
			);
		System.out.println("Task complete. Task Time: " + elapsed);
	}
	private static class NodeHandler implements Runnable {
		private final int start;
		private final int end;
		private final int size;
		private final Socket s;
		
		private double[][] OUTPUT;
		public NodeHandler(Socket s, int start, int end) {
			this.s = s;
			this.end = end;
			this.start = start;
			size = end - start;
		}
		
		public double[][] getResult() {
			return OUTPUT;
		}
		
		@Override
		public void run() {
			try {
				//System.out.println("Sending " + size + " files to " + s.getRemoteSocketAddress());
				DataOutputStream os = new DataOutputStream(s.getOutputStream());
				DataInputStream is = new DataInputStream(s.getInputStream());
				
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
				System.err.println("Error on socket with " + s.getRemoteSocketAddress());
			}
		}
	}
	private static class Listener implements Runnable{
		private final int port;
		private ArrayList<Socket> nodes;
		private boolean run = true;
		ServerSocket serverSocket;
		
		public Listener(int p) {
			port = p;
			nodes = new ArrayList<Socket>();
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
						nodes.add(nodeSocket);
						System.out.println(nodeSocket.getRemoteSocketAddress() + " added to nodes. Total nodes: " + nodes.size());
					} catch (IOException e) {}
				}
				serverSocket.close();
			} catch (IOException e) {}
		}
		
		/**
		 * Stop listening
		 */
		public void stop() {
			run = false;
			try {
				serverSocket.close();
			} catch (IOException e) {}
		}
		
		/**
		 * Send a heartbeat message (0) to all nodes. Remove closed connections.
		 */
		public void heartbeat() {
			synchronized (nodes) {
				if (nodes.size() > 0) {
					for (int i = 0; i < nodes.size(); i++) {
						Socket s = nodes.get(i);
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
		 * @return Nodes
		 */
		public ArrayList<Socket> getNodes() {
			heartbeat();
			return nodes;
		}
	}
	/**
	 * Entry point for node
	 */
	private static void node() {
		String host;
		int threads;
		int port;
		
		//Read arguments
		System.out.println("Node Parameters:");
		if (config.get("host") == null) {
			host = "localhost";
			System.out.println("\thost:" + host + " (DEFAULT)");
		} else {
			host = config.get("host");
			System.out.println("\thost:" + host);
		}
		
		if (config.get("port") == null) {
			port = 8190;
			System.out.println("\tport:" + port + " (DEFAULT)");
		} else {
			try {
				port = Integer.parseInt(config.get("port"));
				System.out.println("\tport:" + port);
			} catch (Exception e) {
				System.err.println("Invalid port.");
				return;
			}
		}
		
		if (config.get("threads") == null) {
			threads = Runtime.getRuntime().availableProcessors();
			System.out.println("\tthreads:" + threads + " (DEFAULT)");
		} else {
			try {
				threads = Integer.parseInt(config.get("threads"));
				System.out.println("\tthreads:" + threads);
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
			System.out.println("Attempting to connect to " + host + ".");
			while (true) {
				try {
					socket = new Socket(host, port);
					is = new DataInputStream(socket.getInputStream());
					os = new DataOutputStream(socket.getOutputStream());
					System.out.println("Connection to " + host + " made.");
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
							int framesPerThread = (int)Math.ceil(size / (double)threads);
							ExecutorService executor = Executors.newFixedThreadPool(threads);
							FeatureExtractor[] workerPool = new FeatureExtractor[threads];
							int f = 0;
							for (int i = 0; i < threads; i++) {
								int fSize = framesPerThread;
								if (f + fSize > size) fSize -= f + fSize - size;
								List<InputStream> workload = input.subList(f, f + fSize);
								//System.out.println("Thread " + i + " gets " + f + " to " + (f + fSize));
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
							for (int i = 0; i < threads; i++) {
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
						System.out.println("Connection to " + host + " lost.");
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
