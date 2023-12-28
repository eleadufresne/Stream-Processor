package com.fruits.streamprocessing.util;

import com.fruits.streamprocessing.FruitStreaming;

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

/** Java adaptation of the tutorial "How to Detect Circles in Images" for the {@link FruitStreaming}
 *  application.
 *
 **** MULTISTEP CIRCLE DETECTOR *******************************************************************
 * 	STEP 1. Sobel edge detection
 * 	STEP 2. Canny denoising:
 *			- Gaussian filter
 *			- Compute image gradient
 *			- Non-maximum suppression
 *			- Edge tracking
 * 	STEP 3. Circle detection using Hough transform
 **************************************************************************************************
 *
 * @see <a href="https://www.codingame.com/playgrounds/38470/how-to-detect-circles-in-images">...</a>
 * @author Éléa Dufresne
 */
public class CircleDetector {

	/** Read the image at path_to_image, looks through it for circles, and writes cropped images
	 *  containing the circles in path_to_filtered_image.
	 *
	 * @param path_to_image				the target image
	 * @param path_to_filtered_image 	where to output cropped images to
	 * @param steps 					number of iterations
	 * @param threshold 				number between 0-1, fraction of circle visible to be counted as a detection, for example 0.4 would mean at least 40% of a circle must be visible
	 * @return 	number of circles detected
	 */
	public static int detect(String path_to_image, String path_to_filtered_image, int steps, double threshold) {
		// read the image
		BufferedImage image_to_crop;
		try {
			File image = new File(path_to_image);
			image_to_crop = ImageIO.read(image);
		} catch(Exception e) {
			System.err.println("ERROR: could not read from "+ path_to_image +" ("+ e.getMessage() +")");
			return 0;
		}

		// find circles
		int height = image_to_crop.getHeight(); // 200
		int width = image_to_crop.getWidth(); // 300
		int r_max = Math.min(height, width) / 10; // 22
		int r_min = Math.max(height, width) / 20; // 15

		// search for circles
		List<Circle> points = new ArrayList<>();
		for (int r = r_min; r < r_max + 1; r++) {
			for (int s = 0; s < steps; s++) {
				points.add(new Circle(r,
						(int) (r * Math.cos(2.0 * Math.PI * s / steps)),
						(int) (r * Math.sin(2.0 * Math.PI * s / steps))));
			}
		}

		// compute the coordinates of the center of all the circles that pass by that point
		// every time we find a pixel belonging to a circle of a given position and radius we
		// increment a counter when later divided by steps, this is how much of each circle we see
		Hashtable<Circle, Integer> accumulator = new Hashtable<>();
		for (Coordinates point : CannyEdgeDetector.detect(image_to_crop)) {
			int x = point.x;
			int y = point.y;
			for (Circle d : points) {
				int a = x - d.y;
				int b = y - d.r;
				Circle for_accumulator = new Circle(a, b, d.x);
				Integer within_threshold = accumulator.get(for_accumulator);
				if (within_threshold == null) accumulator.put(for_accumulator, 1);
				else accumulator.put(for_accumulator, ++within_threshold);
			}
		}

		// filter out all circles that are too incomplete (below threshold) or that overlap with
		// already detected circles
	    List<Circle> circles = new ArrayList<>();
	    for (Circle circle : Collections.list(accumulator.keys())) {
	        if ((double) accumulator.get(circle) / steps >= threshold) {
	            boolean overlaps = false;
	            for (Circle existing_circle : circles) {
	                double distance =
							Math.sqrt(Math.pow(existing_circle.x - circle.x, 2)
							+ Math.pow(existing_circle.y - circle.y, 2));
	                if (distance < existing_circle.r) {
	                    overlaps = true;
	                    break;
	                }
	            } if (!overlaps) circles.add(circle);
	        }
	    }

	    // crop all detected circles into their own images and write to disk
	    int i = 0;
	    for (Circle circle : circles) {
			File file = new File(path_to_filtered_image+"_"+i+".png");
			int x0 = circle.x - circle.r;
			int x1 = circle.x + circle.r;
			int y0 = circle.y - circle.r;
			int y1 = circle.y + circle.r;
			x0 = CannyEdgeDetector.clip(x0, 0, image_to_crop.getWidth());
			x1 = CannyEdgeDetector.clip(x1, 0, image_to_crop.getWidth());
			y0 = CannyEdgeDetector.clip(y0, 0, image_to_crop.getHeight());
			y1 = CannyEdgeDetector.clip(y1, 0, image_to_crop.getHeight());
		    try {
		    	BufferedImage cropped_image = image_to_crop.getSubimage(x0, y0, x1-x0, y1-y0);
				ImageIO.write(cropped_image, "png", file);
				i++;
			} catch (IOException e) {
				System.err.println("ERROR: could not write to output image: " + e.getMessage());
			}
	    }

		// return the number of images that were cropped
		return i;
	}

	/* (helper) Canny algorithm to clean the image and only keep the strongest edges */
	private static class CannyEdgeDetector {

		/** Keep strong edges.
		 *
		 * @param input_image	the target image
		 * @return	coordinates of edges that might belong to circles
		 */
		public static Coordinates[] detect (BufferedImage input_image) {
			BufferedImage grayscale = CannyEdgeDetector.grayscale(input_image);
			BufferedImage blurred = CannyEdgeDetector.blur(grayscale);
			double[][][] gradient_and_direction = gradient(blurred);
			filter_out_non_maximum(gradient_and_direction[0], gradient_and_direction[1], blurred);
			return keep(gradient_and_direction[0], blurred, 20, 25);
		}

		/** Average out all colours into a grayscale image.
		 *
		 * @param input_image	the target image
		 * @return	a grayscale image
		 */
		private static BufferedImage grayscale(BufferedImage input_image) {
			BufferedImage output_image = new BufferedImage(input_image.getWidth(),
					input_image.getHeight(), BufferedImage.TYPE_INT_RGB);
			for (int x = 0; x < input_image.getWidth(); x++) {
				for (int y = 0; y < input_image.getHeight(); y++) {
					Color old_colour = new Color(input_image.getRGB(x, y));
					int intensity =
							(old_colour.getRed() + old_colour.getGreen() + old_colour.getBlue()) /3;
					Color new_colour = new Color(intensity, intensity, intensity);
					output_image.setRGB(x, y, new_colour.getRGB());
				}
			}
			//write to file
			return output_image;
		}

		//

		/** Gaussian blur to remove noise, assumes grayscale input.
		 *
		 * @param input_image	the target grayscale image
		 * @return 	image with gaussian filter
		 */
		private static BufferedImage blur (BufferedImage input_image) {
			BufferedImage output_image = new BufferedImage(
					input_image.getWidth(),
					input_image.getHeight(),
					BufferedImage.TYPE_INT_RGB);

			// gaussian kernel
			double[][] kernel = {
				{1.0 / 256,  4.0 / 256,  6.0 / 256,  4.0 / 256, 1.0 / 256},
				{4.0 / 256, 16.0 / 256, 24.0 / 256, 16.0 / 256, 4.0 / 256},
				{6.0 / 256, 24.0 / 256, 36.0 / 256, 24.0 / 256, 6.0 / 256},
				{4.0 / 256, 16.0 / 256, 24.0 / 256, 16.0 / 256, 4.0 / 256},
				{1.0 / 256,  4.0 / 256,  6.0 / 256,  4.0 / 256, 1.0 / 256}
			};

			// middle of the kernel
			int offset = kernel.length / 2;

			for (int x = 0; x < input_image.getWidth(); x++) {
				for (int y = 0; y < input_image.getHeight(); y++) {
					double acc = 0;
					for (int a = 0; a < kernel.length; a++) {
						for (int b = 0; b < kernel.length; b++) {
							int xn = clip(x + a - offset, 0, input_image.getWidth() - 1);
							int yn = clip(y + b - offset, 0, input_image.getHeight() - 1);
							int colour = new Color(input_image.getRGB(xn, yn)).getRed();
							acc += colour * kernel[a][b];
						}
					}
					Color new_colour = new Color((int) acc, (int) acc, (int) acc);
					output_image.setRGB(x, y, new_colour.getRGB());
				}
			}
			return output_image;
		}

		/** Compute image gradient.
		 *
		 * @param input_image	the target image with gaussian filter
		 * @return	gradient and direction of edges
		 */
		private static double[][][] gradient (BufferedImage input_image) {
			int width = input_image.getWidth();
			int height = input_image.getHeight();
			double[][] gradient = new double[width][height];
			double[][] direction = new double[width][height];

			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					if (0 < x && x < width -1 && 0 < y && y < height - 1) {
						//red has no special significance, this just retrieves the pixel intensity provided input is grayscale
						int magx = new Color(input_image.getRGB(x + 1, y)).getRed() - new Color(input_image.getRGB(x - 1, y)).getRed();
						int magy = new Color(input_image.getRGB(x, y+1)).getRed() - new Color(input_image.getRGB(x, y - 1)).getRed();
						gradient[x][y] = Math.sqrt(Math.pow(magx, 2) + Math.pow(magy, 2));
						direction[x][y] = Math.atan2(magy, magx);
					}
				}
			}
			return new double[][][]{gradient, direction};
		}

		/** Non-maximum suppression: filter out all non-maximum gradients by setting them to 0.
		 *
		 * @param gradients		computed edge gradients
		 * @param directions 	computed edges directions
		 * @param input_image 	the target image
		 */
		private static void filter_out_non_maximum(double[][] gradients, double[][] directions,
												   BufferedImage input_image) {
			for (int x = 1; x < input_image.getWidth() - 1; x++) {
				for (int y = 1; y < input_image.getHeight() - 1; y++) {
					double angle = directions[x][y] >= 0 ? directions[x][y] : directions[x][y] + Math.PI;
					double rangle = Math.round(angle / (Math.PI / 4));
					double mag = gradients[x][y];
					if ((rangle == 0 || rangle == 4) && (gradients[x - 1][y] > mag || gradients[x + 1][y] > mag)
							|| (rangle == 1 && (gradients[x - 1][y - 1] > mag || gradients[x + 1][y + 1] > mag))
							|| (rangle == 2 && (gradients[x][y - 1] > mag || gradients[x][y + 1] > mag))
							|| (rangle == 3 && (gradients[x + 1][y - 1] > mag || gradients[x - 1][y + 1] > mag))) {
						gradients[x][y] = 0;
					}
				}
			}
		}

		// keep strong edges and weak edges close to strong pixels
		private static Coordinates[] keep (double[][] gradient, BufferedImage input_image, int low, int high) {
			//keep strong edges
			int width = input_image.getWidth();
			int height = input_image.getHeight();
			Set<Coordinates> keep = new HashSet<Coordinates>();
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					if (gradient[x][y] > high) {
						keep.add(new Coordinates(x, y));
					}
				}
			}

			// keep weak edges close to strong
			Set<Coordinates> last_iter = keep;
			while (last_iter.size() > 0) {
				Set<Coordinates> new_keep = new HashSet<Coordinates>();
				for (Coordinates c : last_iter) {
					int x = c.x;
					int y = c.y;
					for (int a :  new int[] {-1, 0, 1}) {
						for (int b :  new int[] {-1, 0, 1}) {
							if (gradient[x + a][y + b] > low
									&& !keep.contains(new Coordinates(x+a, y+b))) {
								new_keep.add(new Coordinates(x+a,y+b));
							}
						}
					}
				}
				keep.addAll(new_keep);
				last_iter = new_keep;
			}
			Coordinates[] to_return = new Coordinates[keep.size()];
			Iterator<Coordinates> iter = keep.iterator();
			int i = 0;
			while (iter.hasNext()) {
				to_return[i] = iter.next();
				i++;
			}
			return to_return;
		}

		/** helper method used in blur() and detect() to place bounds on coordinates */
		private static int clip(int x, int l, int u) {
			if (x < l) return l;
			return Math.min(x, u);
		}
	}

	/** (helper) Point with coordinates (x, y) */
	private static class Coordinates {
		int x, y;
		Coordinates(int x, int y){
			this.x = x;
			this.y = y;
		}
		@Override
		public boolean equals(Object o){
			if (o == this) return true;
			if (!(o instanceof Coordinates)) return false;
			Coordinates other = (Coordinates) o;
			return (this.x == other.x && this.y == other.y);
		}
		@Override
		public int hashCode() {
			int f = y + (x+1)/2;
			return x + f * f;
		}
	}

	/** (helper) Circle centered at (x,y) with radius r */
	private static class Circle {
		int x, y, r;
		public Circle(int x, int y, int r) {
			this.x = x;
			this.y = y;
			this.r = r;
		}
		@Override
		public boolean equals(Object o){
			if (o == this) return true;
			if (!(o instanceof Circle)) return false;
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