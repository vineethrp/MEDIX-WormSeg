package dataObjects;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * STEP 1
 * Parse frame time and camera movement information from Kyle's tracker log output.
 * 
 * Input:
 * 	log/log.dat
 * 
 * Output:
 * 	data/tracker.csv
 * 
 * @author Kyle Moy
 *
 */
public class TrackerLog {
	private static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
	
	public ArrayList<Double[]> data;
	public TrackerLog(String trackerLogPath) throws Exception {
		File file = new File(trackerLogPath);
		data = new ArrayList<Double[]>();
		
		if (trackerLogPath.contains(".log")) {
			parseLegacy(file);
		} else {
			parse(file);
		}
	}
	private void parse(File trackerLog) throws IOException {
		DataInputStream trackerIn = new DataInputStream(new FileInputStream((trackerLog)));
		//Process!
		long epoch = -1;
		long lastTime = -1;
		
		while(trackerIn.available() > 0) {
			int frame = trackerIn.readInt();
			long timeStamp = trackerIn.readLong();
			//System.out.println(frame);
			//Deprecated X, Y
			trackerIn.readInt();
			trackerIn.readInt();
			trackerIn.readInt();
			
			if (epoch == -1) epoch = timeStamp;
			long timeElapsed = timeStamp - epoch; //Relative to beginning of recording
			
			if (lastTime == -1) lastTime = timeStamp;
			double timeDelta = timeStamp - lastTime;
			
			lastTime = timeStamp;

			data.add(new Double[]{(double) frame, (double)timeElapsed, timeDelta});
		}
		trackerIn.close();
	}
	private void parseLegacy(File trackerLog) throws FileNotFoundException, ParseException {
		//Create file handlers
		Scanner tracker = new Scanner(trackerLog);
		
		//Process!
		long epoch = 0;
		long lastTime = 0;
		int frame = 0;
		while(tracker.hasNext()) {
			String line = tracker.nextLine();
			if (line.contains("wrote frame")) {
				String[] info = line.split("\t");
				if (epoch == 0) {
					epoch = parseTime(info[0]);
				}
				long timeElapsed = parseTime(info[0]) - epoch;
				long timeDelta = timeElapsed - lastTime;
				lastTime = timeElapsed;
				data.add(new Double[]{(double)frame, (double)timeElapsed, (double) timeDelta});
				frame++;
			}
		}
		tracker.close();
	}

	public static long parseTime(String time) throws ParseException {
		return format.parse(time).getTime();
	}
}
