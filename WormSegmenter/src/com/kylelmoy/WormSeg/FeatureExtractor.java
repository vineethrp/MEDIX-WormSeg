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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Prepares and processes video frames for worm location and body area
 * 
 * @author Kyle Moy
 *
 */
public class FeatureExtractor implements Runnable {
	public final static int OUTPUT_COLUMNS = 3;//x, y, area
	
	private final int FRAME_WIDTH;
	private final int FRAME_HEIGHT;
	private final int SEARCH_AREA_MIN;
	private final int SEARCH_AREA_MAX;
	private final int SEARCH_WINDOW_SIZE;
	private final int BLUR_RADIUS;
	private final int THRESHOLD_NEIGHBORHOOD_SIZE;
	private final double THRESHOLD_RATIO;
	private final InputStream[] INPUT;
	private double[][] OUTPUT;
	
	public FeatureExtractor(List<InputStream> images, double[] params) {
		INPUT = new InputStream[images.size()];
		images.toArray(INPUT);
		OUTPUT = new double[INPUT.length][OUTPUT_COLUMNS];
		FRAME_WIDTH = (int)params[0];
		FRAME_HEIGHT = (int)params[1];
		SEARCH_AREA_MIN = (int)params[2];
		SEARCH_AREA_MAX = (int)params[3];
		SEARCH_WINDOW_SIZE = (int)params[4];
		BLUR_RADIUS = (int)params[5];
		THRESHOLD_NEIGHBORHOOD_SIZE = (int)params[6];
		THRESHOLD_RATIO = params[7];
	}
	public double[][] getResult() {
		return OUTPUT;
	}
	
	/**
	 * Writes boolean masks as images for debugging purposes
	 * @param image
	 * @param x
	 * @param y
	 * @param frame
	 * @throws IOException
	 */
	@SuppressWarnings("unused")
	private static void drawThreshold(boolean[][] image, int x, int y, int frame) throws IOException {
        int w = image.length;
        int h = image[0].length;
		BufferedImage draw = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
		Graphics g = draw.getGraphics();
		g.setColor(new Color(0));
		g.fillRect(0, 0, 640, 640);
		for (int i = 0; i < h; i++) {
			for (int j = 0; j < w; j++) {
				if (image[j][i])
					draw.setRGB(j + x, i + y, 0xFFFFF);
			}
		}
		g.dispose();
		draw.flush();
		ImageIO.write(draw, "png", new File(String.format("%07d", frame) + ".png"));
	}

	/**
	 * Writes grayscale images to file for debugging purposes
	 * @param image
	 * @param w
	 * @param h
	 * @param frame
	 * @throws IOException
	 */
	@SuppressWarnings("unused")
	private static void drawGrayscale(int[] image, int w, int h, int frame) throws IOException {
		BufferedImage draw = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics g = draw.getGraphics();
		g.setColor(new Color(0));
		g.fillRect(0, 0, 640, 640);
		for (int i = 0; i < h; i++) {
			for (int j = 0; j < w; j++) {
				int gray = image[(i * w) + j];
				int rgb = gray;
				rgb = (rgb << 8) + gray;
				rgb = (rgb << 8) + gray;
				draw.setRGB(j, i, rgb);
			}
		}
		g.dispose();
		draw.flush();
		ImageIO.write(draw, "png", new File(String.format("%07d", frame) + ".png"));
	}
	
	public void run() {
		int segmentationErrorCount = 0;
		int offset = (SEARCH_WINDOW_SIZE / 2);
		Integer lastx = null;
		Integer lasty = null;
		int offx, offy;
		double[] centroid;
		
		BufferedImage img;
		int[] grayscale;
		boolean[][] thresh;
		
		int index = 0;
		while (true) {
			if (index >= INPUT.length) break;
			try {
				img = ImageIO.read(INPUT[index]);
			} catch (IOException e) {
				break;
			}
			
			//Convert to grayscale, as primitive array
			grayscale = grayscale(img);
			
			//Apply blur to the image
			grayscale = quickBlur(grayscale, FRAME_WIDTH, FRAME_HEIGHT, BLUR_RADIUS);
			//try {drawGrayscale(grayscale,FRAME_WIDTH, FRAME_HEIGHT,index);} catch (IOException e) {}
			
			try {
				
				//If there's no known prior location, search the whole image for an approximate worm location
				if (lastx == null) {
					thresh = threshold(grayscale,FRAME_WIDTH, FRAME_HEIGHT);
					centroid = largestComponent(thresh);
					lastx = (int)centroid[0];
					lasty = (int)centroid[1];
				}
				
				//Search cropped location for the worm (improves speed and accuracy over full image search)
				thresh = threshold(grayscale,FRAME_WIDTH, FRAME_HEIGHT, lastx, lasty, SEARCH_WINDOW_SIZE / 2);
				
				//Keep track of crop region origin, so we can reconstruct the position relative to the frame
				offx = lastx - offset;
				offy = lasty - offset;
				if (offx < 0) offx = 0;
				if (offy < 0) offy = 0;
				
				//Attempt to find the largest component
				//If this fails, an exception is thrown, skip to CATCH
				centroid = largestComponent(thresh);
				centroid[0] += offx;
				centroid[1] += offy;
				lastx = (int)centroid[0];
				lasty = (int)centroid[1];
				
				//Calculate the area of connected components
				int area = calcArea(thresh);
				
				//Log to output
				OUTPUT[index][0] = centroid[0];
				OUTPUT[index][1] = centroid[1];
				OUTPUT[index][2] = area;
			} catch (SegmentationFailureException e) {
				lastx = null;
				
				//Narrow search area to the center area if too many failures
				//(in the case that many objects are in the image)
				segmentationErrorCount ++;
				if (segmentationErrorCount == 3) { 
					lastx = FRAME_WIDTH / 2;
					lasty = FRAME_HEIGHT / 2;
					segmentationErrorCount = 0;
				}
				
				//Log failure to output
				OUTPUT[index][0] = -1.0;
				OUTPUT[index][1] = -1.0;
				OUTPUT[index][2] = -1.0;
				//e.printStackTrace();
			}
			index++;
		}
	}

	/**
	 * Optimized box blur
	 * @param image Grayscale image
	 * @param w Width
	 * @param h Height
	 * @param radius Radius
	 * @return Blurred grayscale image
	 */

	int[] dv;
	private int[] quickBlur(int[] image, int w, int h, int radius) {
		if (radius < 1) {
			return image;
		}
		int wm = w - 1;
		int hm = h - 1;
		int wh = w * h;
		int div = radius + radius + 1;
		int s[] = new int[wh];
		int sum, x, y, i, p, p1, p2, yp, yi, yw;
		int vmin[] = new int[Math.max(w, h)];
		int vmax[] = new int[Math.max(w, h)];
		if (dv == null) {
			dv = new int[256 * div];
			for (i = 0; i < 256 * div; i++) {
				dv[i] = (i / div);
			}
		}
		yw = 0; 
		yi = 0;
		
		//Horizontal 1D blur
		for (y = 0; y < h; y++) {
			sum = 0;
			for (i = -radius; i <= radius; i++) {
				p = image[yi + Math.min(wm, Math.max(i, 0))];
				sum += p;
			}
			for (x = 0; x < w; x++) {
				s[yi] = dv[sum];

				if (y == 0) {
					vmin[x] = Math.min(x + radius + 1, wm);
					vmax[x] = Math.max(x - radius, 0);
				}
				p1 = image[yw + vmin[x]];
				p2 = image[yw + vmax[x]];

				sum += p1 - p2;
				yi++;
			}
			yw += w;
		}
		
		//Vertical 1D blur
		for (x = 0; x < w; x++) {
			sum = 0;
			yp = -radius * w;
			for (i = -radius; i <= radius; i++) {
				yi = Math.max(0, yp) + x;
				sum += s[yi];
				yp += w;
			}
			yi = x;
			for (y = 0; y < h; y++) {
				image[yi] = dv[sum];
				if (x == 0) {
					vmin[y] = Math.min(y + radius + 1, hm) * w;
					vmax[y] = Math.max(y - radius, 0) * w;
				}
				p1 = x + vmin[y];
				p2 = x + vmax[y];

				sum += s[p1] - s[p2];
				yi += w;
			}
		}
		return image;
	}
	
	/**
	 * Takes an image and strips all but one color channel
	 * @param img A BufferedImage
	 * @return
	 */
	private int[] grayscale(BufferedImage img) {
		byte[] src = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
		// Strip image to red channel --- faster than grayscale conversion
		int[] gray = new int[src.length/3];
		for (int i = 0; i < gray.length; i ++) {
			/* 
			 * Java byte primitive is signed, whereas the values
			 * we're reading are unsigned. We will store the image
			 * as an int[] array to preserve positive values above
			 * 127. 
			 */
			gray[i] = src[i*3] & 0xFF;
		}
		
		/* For DataBufferInt
		int[] gray = new int[src.length];
		for (int i = 0; i < src.length; i ++) {
			gray[i] = (src[i] & 0xFF); //0xFF is bitmask stripping one value.
		}*/
		
		return gray;
	}
	
	/**
	 * Calculates the total area of components in a thresholded image
	 * @param image
	 * @return
	 */
	private int calcArea(boolean[][] image) {
		int c = 0;
		for (boolean[] a : image) {
			for (boolean b : a) {
				if (b) c++;
			}
		}
		return c;
	}

	/**
	 * Produces a boolean mask from an image using dynamic thresholding
	 * @param img The image to threshold
	 * @return
	 */
	private boolean[][] threshold(int[] gray, int w, int h) {
		return threshold(gray, w, h, 0, 0, w, h);
	}
	
	/**
	 * Produces a boolean mask from an image region centered on a specified position
	 * @param img The image to threshold
	 * @param x The center x
	 * @param y The center y
	 * @param size The region width and height
	 * @return
	 */
	private boolean[][] threshold(int[] gray, int w, int h, int x, int y, int size) {
		int bx = x - size;
		int by = y - size;
		int ex = x + size;
		int ey = y + size;
		if (bx < 0) bx = 0;
		if (by < 0) by = 0;
		if (ex >= w) ex = w - 1;
		if (ey >= h) ey = h - 1;
		return threshold(gray, w, h, bx, by, ex, ey);
	}
	
	/**
	 * Produces a boolean mask from a specified image region using dynamic thresholding
	 * @param gray
	 * @param w
	 * @param h
	 * @param x1
	 * @param y1
	 * @param x2
	 * @param y2
	 * @return
	 */
	private boolean[][] threshold(int[] gray, int w, int h, int x1, int y1, int x2, int y2) {
		// Calculate integral table
		int[][] integral = new int[w][h];
		for (int j = 0; j < h; j++) {
			for (int i = 0; i < w; i++) {
				integral[i][j] = sub(gray, i, j, w)
						+ sub(integral, i - 1, j)
						+ sub(integral, i, j - 1)
						- sub(integral, i - 1, j - 1);
			}
		}
		
		//Apply threshold logic to pixel values
		boolean[][] threshold = new boolean[x2 - x1][y2 - y1];
		
		for (int i = x1; i < x2; i++) {
			for (int j = y1; j < y2; j++) {
				if (sub(gray, i, j, w) / average(integral, i, j, w, h, THRESHOLD_NEIGHBORHOOD_SIZE) < THRESHOLD_RATIO) {
					threshold[i - x1][j - y1] = true;
				} else {
					threshold[i - x1][j - y1] = false;
				}
			}
		}
		return threshold;
	}
	
	/**
	 * Looks up the average on an integral table
	 * @param integral
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @param winsize
	 * @return
	 */
	private double average(int[][] integral, int x, int y, int w, int h, int winsize) {
		winsize /= 2;
		int x1 = x - 1 - winsize;
		int y1 = y - 1 - winsize;
		int x2 = x + winsize;
		int y2 = y + winsize;

		// Max Bounds
		if (x2 > w - 1)
			x2 = w - 1;
		if (y2 > h - 1)
			y2 = h - 1;

		// Min Bounds
		final int a, b, c, d;
		if (x1 < 0 || y1 < 0) {
			a = 0;
		} else {
			a = integral[x1][y1];
		}
		if (y1 < 0) {
			b = 0;
		} else {
			b = integral[x2][y1];
		}
		if (x1 < 0) {
			c = 0;
		} else {
			c = integral[x1][y2];
		}
		d = integral[x2][y2];
		return (d - c - b + a) / (double) ((x2 - x1) * (y2 - y1));
	}
	
	/**
	 * Retrieves an element from an array using subscripts, and checks boundaries.
	 * @param arr Array
	 * @param i Subscript
	 * @param j Subscript
	 * @return The element
	 */
	private int sub(int[][] pix, int i, int j) {
		if (i < 0)
			return 0;
		if (j < 0)
			return 0;
		return pix[i][j];
	}

	/**
	 * Retrieves an element from an array using subscripts, and checks boundaries.
	 * @param arr Array
	 * @param i Subscript
	 * @param j Subscript
	 * @return The element
	 */
	private int sub(int[] arr, int i, int j, int w) {
		if (i < 0)
			return 0;
		if (j < 0)
			return 0;
		int val = arr[i + (j * w)];
		return val;
	}
	
	/**
	 * Counts the number of connected components in a boolean mask using
	 * UnionFind
	 * 
	 * @param image The boolean mask
	 * @return The number of connected components
	 * @throws ComponentLabellingFailureException 
	 */
	private double[] largestComponent(boolean[][] image) throws SegmentationFailureException {
		ConnectedComponentAnalysis cmp = new ConnectedComponentAnalysis(image, SEARCH_AREA_MIN, SEARCH_AREA_MAX);
		int w = image.length;
		int h = image[0].length;
		for (int i = 0; i < h; i++) {
			for (int j = 0; j < w; j++) {
				if (image[j][i]) {
					boolean done = false;
					for (int x = -1; x < 2; x++) {
						for (int y = -1; y < 2; y++) {
							final int _x = j + x;
							final int _y = i + y;
							if (_x < 0 || _x >= w)
								continue;
							if (_y < 0 || _y >= h)
								continue;
							if (image[_x][_y]) {
								cmp.union(j, i, _x, _y);
								done = true;
							}
						}
					}
					if (!done) {
						image[j][i] = false;
						cmp.union(j, i, 0, 0);
					}
				}
			}
		}
		return cmp.largestComponentCentroid();
	}

	/**
	 * The <code>ConnectedComponentAnalysis</code> class provides utilities to use UnionFind
	 * 
	 * @author Kyle Moy
	 *
	 */
	private class ConnectedComponentAnalysis {
		private final int[] id;
		private int[] segment;
		private int count;
		private final int w;
		private final int h;
		private final int min;
		private final int max;
		
		public ConnectedComponentAnalysis(boolean[][] image, int areaMin, int areaMax) throws SegmentationFailureException {
			min = areaMin;
			max = areaMax;
			w = image.length;
			h = image[0].length;
			id = new int[w * h + 1];
			count = 0;
			for (int i = 0; i < h; i++) {
				for (int j = 0; j < w; j++) {
					int p = j + (i * w);
					if (image[j][i]) {
						id[p] = p + 1;
						count++;
					} else {
						id[p] = 0;
					}
				}
			}
			if (count == 0) {
				throw new SegmentationFailureException("Threshold image is empty! No worm in image, or threshold parameters incorrect!");
			}

			if (count > (w * h / 2)) {
				throw new SegmentationFailureException("Threshold image is too populated! Image is too dark, or threshold parameters incorrect!");
			}
			segment = new int[w * h + 1];
		}

		private boolean connected(final int p, final int q) {
			return id[p] == id[q];
		}

		private void union(int x1, int y1, int x2, int y2) {
			int p = x1 + (y1 * w);
			int q = x2 + (y2 * w);
			if (p < 0 || q < 0)
				return;
			if (connected(p, q))
				return;
			final int pid = id[p];
			for (int i = 0; i < id.length; i++)
				if (id[i] == pid)
					id[i] = id[q];
			count--;
		}

		public double[] largestComponentCentroid() throws SegmentationFailureException {
			int largestId = -1;
			int largest = 0;
			for (int i = 0; i < h; i++) {
				for (int j = 0; j < w; j++) {
					int compId = id[j + (i * w)];
					if (compId != 0)
						++segment[compId];
				}
			}
			for (int i = 0; i < segment.length; i++) {
				 if (segment[i] > largest && segment[i] > min && segment[i] < max) { 
						largest = segment[i];
						largestId = i;
					}
			}
			if (largest == 0) {
				throw new SegmentationFailureException("No components found!");
			}
			int avgx = 0;
			int avgy = 0;
			for (int i = 0; i < h; i++) {
				for (int j = 0; j < w; j++) {
					if (id[j + (i * w)] == largestId) {
						avgx += j;
						avgy += i;
					}
				}
			}
			return new double[] { ((double)avgx / (double)largest), ((double)avgy / (double)largest) };
		}
	}
	@SuppressWarnings("serial")
	private static class SegmentationFailureException extends Exception {
		public SegmentationFailureException(String message) {
	        super(message);
	    }
	}
}
