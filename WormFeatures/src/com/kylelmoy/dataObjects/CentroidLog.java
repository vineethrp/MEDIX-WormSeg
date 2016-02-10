package com.kylelmoy.dataObjects;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

public class CentroidLog {
	public ArrayList<Double[]> data;
	public CentroidLog (String centroidLogPath) throws FileNotFoundException {
		File file = new File(centroidLogPath);
		Scanner centroidIn = new Scanner(file);
		centroidIn.useDelimiter(",|\r?\n|\r");
		data = new ArrayList<Double[]>();
		while (centroidIn.hasNext()) {
			double frameC = centroidIn.nextDouble();
			double x = centroidIn.nextDouble();
			double y = centroidIn.nextDouble();
			double area = centroidIn.nextDouble();
			data.add(new Double[]{frameC, x, y, area});
		}
		centroidIn.close();
	}
}
