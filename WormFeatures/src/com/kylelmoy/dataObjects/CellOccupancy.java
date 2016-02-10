package com.kylelmoy.dataObjects;

import java.util.ArrayList;


public class CellOccupancy {
	static double PXPERMM = 70.0;
	static double BIN_SIZE = 1.0; //In micrometers
	static double TIME_BIN = 1.0 * 60.0;
	public ArrayList<Double[]> data;
	public CellOccupancy(MovementFeatures movementFeatures) throws Exception {
		ArrayList<Double[]> entries = movementFeatures.data;
		data = new ArrayList<Double[]>();
		//Almost all of this following min/max stuff is meaningless...
		//Lifted directly from path animation code
		double minx = Integer.MAX_VALUE;
		double maxx = Integer.MIN_VALUE;
		double miny = Integer.MAX_VALUE;
		double maxy = Integer.MIN_VALUE;
		for (Double[] e : entries) {
			if (e[3] < minx) minx = e[3];
			if (e[3] > maxx) maxx = e[3];
			if (e[4] < miny) miny = e[4];
			if (e[4] > maxy) maxy = e[4];
		}
		int meanx = (int) ((maxx + minx) / 2);
		int meany = (int) ((maxy + miny) / 2);
		double rangex = maxx - minx;
		double rangey = maxy - miny;
		double mdim = Math.max(rangex, rangey) / 2;
		int binLength = (int)(Math.ceil((Math.max(rangex, rangey) / PXPERMM) / BIN_SIZE));
		binLength *= 1.1;
		double[][] occupancy = new double[binLength][binLength];
		double xoff = meanx - mdim;
		double yoff = meany - mdim;
		boolean[][] visited = new boolean[binLength][binLength];
		double currentTime = 0;
		int max = 0;
		
		for (Double[] e : entries) {
			int mx = (int) (((e[3] - xoff) / PXPERMM) / BIN_SIZE);
			int my = (int) (((e[4] - yoff) / PXPERMM) / BIN_SIZE);
			occupancy[mx][my] += e[2];
			visited[mx][my] = true;
			if (e[1] > currentTime + TIME_BIN) {
				currentTime = e[1];
				int visitCount = 0;
				for (boolean[] a : visited)
					for (boolean b : a)
						if (b) visitCount++;
				if (visitCount > max) max = visitCount;
				visited = new boolean[binLength][binLength];
				data.add(new Double[]{currentTime, (double)visitCount});
			}
		}
	}
}
