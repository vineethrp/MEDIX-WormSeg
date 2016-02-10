package com.kylelmoy.WormCalc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;

import com.kylelmoy.dataObjects.CellOccupancy;
import com.kylelmoy.dataObjects.CentroidLog;
import com.kylelmoy.dataObjects.GlobalCentroids;
import com.kylelmoy.dataObjects.MovementFeatures;
import com.kylelmoy.dataObjects.TrackerLog;

/**
 * Calculates movement features from tracker and segmentation output
 * @author Kyle Moy
 *
 */
public class WormCalc {
	public static void main(String[] args) throws Exception {
		TrackerLog trackerLog = new TrackerLog(args[0]);
		CentroidLog centroidLog = new CentroidLog(args[1]);
		
		GlobalCentroids globalCentroids = new GlobalCentroids(trackerLog, centroidLog);
		
		MovementFeatures movementFeatures = new MovementFeatures(globalCentroids);
		
		CellOccupancy cellOccupancy = new CellOccupancy(movementFeatures);
		
		toFile(movementFeatures.data, args[2]);
		toFile(cellOccupancy.data, args[3]);
	}
	
	private static void toFile(ArrayList<Double[]> data, String path) throws FileNotFoundException {
		PrintWriter out = new PrintWriter(new File(path));
		for (Double[] row : data) {
			String line = "";
			for (Double d : row) {
				line += "," + d;
			}
			line = line.substring(1);
			out.println(line);
		}
		out.close();
	}
}
