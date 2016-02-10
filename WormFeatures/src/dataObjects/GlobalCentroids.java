package dataObjects;

import java.util.ArrayList;

/**
 * Combine the camera position from the parsed tracker logs and the centroids from the segmentation process.
 * 
 * @author Kyle Moy
 *
 */
public class GlobalCentroids {
	public static int WIN_SIZE = 15;
	
	public ArrayList<Double[]> data;
	
	public GlobalCentroids(TrackerLog trackerLog, CentroidLog centroidLog) {
		ArrayList<Double[]> tracker = trackerLog.data;
		ArrayList<Double[]> centroid = centroidLog.data;
		
		data = new ArrayList<Double[]>();
		
		//Process!!
		double lastKnownX = -1;
		double lastKnownY = -1;
		double globalXOffset = 0;
		double globalYOffset = 0;
		ArrayList<DataEntry> centroids = new ArrayList<DataEntry>();
		
		int minSize = Math.min(centroidLog.data.size(), trackerLog.data.size());
		for (int i = 0; i < minSize; i++) {
			
			//Messy... but if it ain't broke...
			DataEntry entry = new DataEntry(centroid.get(i), tracker.get(i));
			
			//Initialize last known location
			if (lastKnownX == -1 && lastKnownY == -1) {
				lastKnownX = entry.x;
				lastKnownY = entry.y;
			}
			
			//If there is a known last location, check that this point is within a reasonable range.
			if (lastKnownX != -1 && lastKnownY != -1) {
				double distance = Math.sqrt(Math.pow(lastKnownX - (entry.x + globalXOffset),2) + Math.pow(lastKnownY - (entry.y + globalYOffset),2));
				
				//If the worm moved too far away, add the opposite distance to the camera offset, thus centering the worm
				if (distance > 10) {
					globalXOffset += lastKnownX - (entry.x + globalXOffset);
					globalYOffset += lastKnownY - (entry.y + globalYOffset);
				}
				
				//Note that this replaces any missing values by setting them at the last known value
			}
			entry.x += globalXOffset;
			entry.y += globalYOffset;
			
			centroids.add(entry);
			lastKnownX = entry.x;
			lastKnownY = entry.y;
		}
		
		
		//Copy
		double[][] temp = new double[centroids.size()][2];
		for (int i = 0; i < centroids.size(); i++) {
			DataEntry e = centroids.get(i);
			temp[i][0] = e.x;
			temp[i][1] = e.y;
		}
		
		//Smooth
		double[] kernel = makeKernel(WIN_SIZE);
		int HWIN_SIZE = WIN_SIZE / 2;
		for (int i = HWIN_SIZE; i < centroids.size() - HWIN_SIZE; i++) {
			double feat[] = new double[2];
			int k = 0;
			for (int j = i - HWIN_SIZE; j < i + HWIN_SIZE; j++) {
				for (int f = 0; f < 2; f++ ){
					feat[f] += temp[j][f] * kernel[k];
				}
				k++;
			}
			DataEntry e = centroids.get(i);
			e.x = feat[0];
			e.y = feat[1];
			data.add(e.toArray());
		}
	}
	private static double[] makeKernel(int width) {
		double radius = width / 2;
		int r = (int) Math.ceil(radius);
		int rows = r * 2 + 1;
		double[] matrix = new double[rows];
		double sigma = radius / 3;
		double sigma22 = 2 * sigma * sigma;
		double sigmaPi2 = (2 * Math.PI * sigma);
		double sqrtSigmaPi2 = Math.sqrt(sigmaPi2);
		double radius2 = radius * radius;
		double total = 0;
		int index = 0;
		for (int row = -r; row <= r; row++) {
			float distance = row * row;
			if (distance > radius2)
				matrix[index] = 0;
			else
				matrix[index] = Math.exp(-(distance) / sigma22)
						/ sqrtSigmaPi2;
			total += matrix[index];
			index++;
		}
		for (int i = 0; i < rows; i++)
			matrix[i] /= total;
		return matrix;
	}
	
	/**
	 * Groan... Leftovers from the days of old
	 * 
	 * @author Kyle Moy
	 *
	 */
	private static class DataEntry {
		public double frame, area, timeDelta, timeElapsed, x, y;
		public DataEntry (Double[] centroidIn, Double[] trackerIn) {
			frame = centroidIn[0];
			x = centroidIn[1];
			y = centroidIn[2];
			area = centroidIn[3];
			timeElapsed = trackerIn[1];
			timeDelta = trackerIn[2];
			if (trackerIn[0] != trackerIn[0]) {
				System.err.println("Centroid log indexes do not match tracker log!");
			}
		}
		public Double[] toArray() {
			return new Double[] {frame, timeElapsed, timeDelta, x, y, area};
		}
	}
}
