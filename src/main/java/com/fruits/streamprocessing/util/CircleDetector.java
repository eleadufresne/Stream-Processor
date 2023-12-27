package com.fruits.streamprocessing.util;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

public class CircleDetector {

	/**
	 * Reads the image at inputImagePath, looks through it for circles,
	 * and writes cropped images containing the circles in outputFolderPath
	 * @param inputImagePath the target image
	 * @param outputFolderPath where to output cropped images to
	 * @param rmin minimum radius of circles to look for in pixels
	 * @param rmax minimum radius of circles to look for in pixels
	 * @param steps number of iterations
	 * @param threshold number between 0-1, fraction of circle visible to be counted as a detection, for example 0.4 would mean at least 40% of a circle must be visible
	 */
	public static void detect(String inputImagePath, String outputFolderPath, int rmin, int rmax, int steps, double threshold) {
		BufferedImage toCrop = null;

		//read image
		try {
			File image = new File(inputImagePath);
			toCrop = ImageIO.read(image);
		}

		catch(Exception e) {
			//cannot read inputImage
			System.out.println("bad input");
		}

		//find circles
		List<Circle> points = new ArrayList<Circle>();
		for (int r = rmin; r < rmax + 1; r++) {
			for (int t = 0; t < steps; t++) {
				points.add(new Circle(r,
						(int) (r * Math.cos(2.0 * Math.PI * t / steps)),
						(int) (r * Math.sin(2.0 * Math.PI * t / steps))));
			}
		}

		//compute the coordinates of the center of all the circles that pass by that point
		//every time we find a pixel belonging to a circle of a given position and radius we increment a counter
		//when later divided by steps, this is how much of each circle we see
		Hashtable<Circle, Integer> acc = new Hashtable<Circle, Integer>();
		for (Coordinates point : CannyEdgeDetector.detect(toCrop)) {
			int x = point.x;
			int y = point.y;
			for (Circle p : points) {
				int a = x - p.y;
				int b = y - p.r;
				Circle forAcc = new Circle(a, b, p.x);
				Integer num = acc.get(forAcc);
				if (num == null) {
					acc.put(forAcc, 1);
				}
				else {
					acc.put(forAcc, ++num);
					//System.out.println(num);
				}
			}
		}

		//filter out all circles that are too incomplete (below threshold) or that overlap with already detected circles
	    List<Circle> circles = new ArrayList<>();
	    for (Circle c : Collections.list(acc.keys())) {
	        if ((double)acc.get(c) / steps >= threshold) {
	            boolean overlaps = false;
	            for (Circle existingCircle : circles) {
	                double distance = Math.sqrt(Math.pow(existingCircle.x - c.x, 2) + Math.pow(existingCircle.y - c.y, 2));
	                if (distance < existingCircle.r) {
	                    overlaps = true;
	                    break;
	                }
	            }
	            if (!overlaps) {
	                circles.add(c);
	                System.out.println(acc.get(c) / (double) steps + " " + c.x + " " + c.y + " " + c.r);
	            }
	        }
	    }

	    //crop all detected circles into their own images and write to disk
	    int imageIndex = 0;
	    for (Circle c : circles) {
			File output1file = new File(outputFolderPath+"\\testorange"+imageIndex+".png");
			int x0 = c.x - c.r;
			int x1 = c.x + c.r;
			int y0 = c.y - c.r;
			int y1 = c.y + c.r;
			x0 = CannyEdgeDetector.clip(x0, 0, toCrop.getWidth());
			x1 = CannyEdgeDetector.clip(x1, 0, toCrop.getWidth());
			y0 = CannyEdgeDetector.clip(y0, 0, toCrop.getHeight());
			y1 = CannyEdgeDetector.clip(y1, 0, toCrop.getHeight());
		    try {
		    	BufferedImage cropped = toCrop.getSubimage(x0, y0, x1-x0, y1-y0);
				ImageIO.write(cropped, "png", output1file);
				imageIndex++;
			} catch (IOException e) {
				e.printStackTrace();
			}
	    }
	}

	private static class CannyEdgeDetector {

		//find edges that can belong to circles
		public static Coordinates[] detect (BufferedImage inputImage) {
			BufferedImage grayscale = CannyEdgeDetector.grayscale(inputImage);
			BufferedImage blurred = CannyEdgeDetector.blur(grayscale);
			double[][][] gradientAndDirection = gradient(blurred);
			filter(gradientAndDirection[0], gradientAndDirection[1], blurred);
			Coordinates[] keep = keep(gradientAndDirection[0], blurred, 20, 25);
			return keep;
		}

		//averages out all colors into a grayscale image
		private static BufferedImage grayscale (BufferedImage inputImage) {
			BufferedImage outputImage = new BufferedImage(inputImage.getWidth(), inputImage.getHeight(), BufferedImage.TYPE_INT_RGB);
			for (int x = 0; x < inputImage.getWidth(); x++) {
				for (int y = 0; y < inputImage.getHeight(); y++) {
					Color oldColor = new Color(inputImage.getRGB(x, y));
					int intensity = (oldColor.getRed()+oldColor.getGreen()+oldColor.getBlue()) / 3;
					Color newColor = new Color(intensity, intensity, intensity);
					outputImage.setRGB(x, y, newColor.getRGB());
				}
			}
			//write to file
			return outputImage;
		}

		//gaussian blur image to remove noise, assumes grayscale input
		private static BufferedImage blur (BufferedImage inputImage) {
			BufferedImage outputImage = new BufferedImage(inputImage.getWidth(), inputImage.getHeight(), BufferedImage.TYPE_INT_RGB);

			//gaussian kernel
			double[][] kernel = {
				{1.0 / 256,  4.0 / 256,  6.0 / 256,  4.0 / 256, 1.0 / 256},
				{4.0 / 256, 16.0 / 256, 24.0 / 256, 16.0 / 256, 4.0 / 256},
				{6.0 / 256, 24.0 / 256, 36.0 / 256, 24.0 / 256, 6.0 / 256},
				{4.0 / 256, 16.0 / 256, 24.0 / 256, 16.0 / 256, 4.0 / 256},
				{1.0 / 256,  4.0 / 256,  6.0 / 256,  4.0 / 256, 1.0 / 256}
			};

			//middle of the kernel
			int offset = kernel.length / 2;

			for (int x = 0; x < inputImage.getWidth(); x++) {
				for (int y = 0; y < inputImage.getHeight(); y++) {
					double acc = 0;
					for (int a = 0; a < kernel.length; a++) {
						for (int b = 0; b < kernel.length; b++) {
							int xn = clip(x + a - offset, 0, inputImage.getWidth() - 1);
							int yn = clip(y + b - offset, 0, inputImage.getHeight() - 1);
							int color = new Color(inputImage.getRGB(xn, yn)).getRed();
							acc += color * kernel[a][b];
						}
					}
					Color newColor = new Color((int) acc, (int) acc, (int) acc);
					outputImage.setRGB(x, y, newColor.getRGB());
				}
			}
			return outputImage;
		}

		//calculate gradient and direction for edges, assumes grayscale input
		private static double[][][] gradient (BufferedImage inputImage) {
			int width = inputImage.getWidth();
			int height = inputImage.getHeight();
			double[][] gradient = new double[width][height];
			double[][] direction = new double[width][height];

			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					if (0 < x && x < width -1 && 0 < y && y < height - 1) {
						//red has no special significance, this just retrieves the pixel intensity provided input is grayscale
						int magx = new Color(inputImage.getRGB(x + 1, y)).getRed() - new Color(inputImage.getRGB(x - 1, y)).getRed();
						int magy = new Color(inputImage.getRGB(x, y+1)).getRed() - new Color(inputImage.getRGB(x, y - 1)).getRed();
						gradient[x][y] = Math.sqrt(Math.pow(magx, 2) + Math.pow(magy, 2));
						direction[x][y] = Math.atan2(magy, magx);
					}
				}
			}
			return new double[][][]{gradient, direction};
		}

		//filters out all non-maximum gradients by setting them to 0
		private static void filter (double[][] gradient, double[][] direction, BufferedImage inputImage) {
			for (int x = 1; x < inputImage.getWidth() - 1; x++) {
				for (int y = 1; y < inputImage.getHeight() - 1; y++) {
					double angle = direction[x][y] >= 0 ? direction[x][y] : direction[x][y] + Math.PI;
					double rangle = Math.round(angle / (Math.PI / 4));
					double mag = gradient[x][y];
					if ((rangle == 0 || rangle == 4) && (gradient[x - 1][y] > mag || gradient[x + 1][y] > mag)
							|| (rangle == 1 && (gradient[x - 1][y - 1] > mag || gradient[x + 1][y + 1] > mag))
							|| (rangle == 2 && (gradient[x][y - 1] > mag || gradient[x][y + 1] > mag))
							|| (rangle == 3 && (gradient[x + 1][y - 1] > mag || gradient[x - 1][y + 1] > mag))) {
						gradient[x][y] = 0;
					}
				}
			}
		}

		//keep strong edges and weak edges close to strong pixels
		private static Coordinates[] keep (double[][] gradient, BufferedImage inputImage, int low, int high) {
			//keep strong edges
			int width = inputImage.getWidth();
			int height = inputImage.getHeight();
			Set<Coordinates> keep = new HashSet<Coordinates>();
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					if (gradient[x][y] > high) {
						keep.add(new Coordinates(x, y));
					}
				}
			}

			//keep weak edges close to strong
			Set<Coordinates> lastiter = keep;
			while (lastiter.size() > 0) {
				Set<Coordinates> newkeep = new HashSet<Coordinates>();
				for (Coordinates c : lastiter) {
					int x = c.x;
					int y = c.y;
					for (int a :  new int[] {-1, 0, 1}) {
						for (int b :  new int[] {-1, 0, 1}) {
							if (gradient[x + a][y + b] > low && !keep.contains(new Coordinates(x+a, y+b))) {
								newkeep.add(new Coordinates(x+a,y+b));
							}
						}
					}
				}
				keep.addAll(newkeep);
				lastiter = newkeep;
			}
			Coordinates[] toReturn = new Coordinates[keep.size()];
			Iterator<Coordinates> iter = keep.iterator();
			int index = 0;
			while (iter.hasNext()) {
				toReturn[index] = iter.next();
				index++;
			}
			return toReturn;
		}

		//helper method used in blur() and detect() to place bounds on coordinates
		public static int clip(int x, int l, int u) {
			if (x < l) return l;
			if (x > u) return u;
			return x;
		}


	}

	//helper class representing a point at (x, y)
	private static class Coordinates {
		int x;
		int y;
		Coordinates(int x, int y){
			this.x = x;
			this.y = y;
		}
		@Override
		public boolean equals(Object o){
			if (o == this)
				return true;
			if (!(o instanceof Coordinates))
				return false;
			Coordinates other = (Coordinates) o;
			return (this.x == other.x && this.y == other.y);
		}
		@Override
		public int hashCode() {
			int f = y + (x+1)/2;
			return x + f * f;
		}
	}

	//helper class representing a circle at (x,y) with radius r
	//used as general 3-tuple in List<Circle> points
	private static class Circle {
		int x;
		int y;
		int r;
		public Circle(int x, int y, int r) {
			this.x = x;
			this.y = y;
			this.r = r;
		}
		@Override
		public boolean equals(Object o){
			if (o == this)
				return true;
			if (!(o instanceof Circle))
				return false;
			Circle other = (Circle) o;
			return (this.x == other.x && this.y == other.y && this.r == other.r);
		}
		@Override
		public int hashCode() {
			int f = y + (x+1)/2;
			return x + f * f;
		}
	}
}