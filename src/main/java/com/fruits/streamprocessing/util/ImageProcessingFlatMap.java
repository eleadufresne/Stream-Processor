package com.fruits.streamprocessing.util;

import com.fruits.streamprocessing.FruitStreaming;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.util.Collector;

import java.util.concurrent.atomic.AtomicInteger;

/** FlatMapFunction takes a path to an image and maps it to a tuple of the number of fruits found
 * in the image along with their type. Used in the {@link FruitStreaming} app.
 * @author Éléa Dufresne
 */
public class ImageProcessingFlatMap implements FlatMapFunction<String, Tuple2<String, Integer>> {
  private final String path_to_images;
  private final String path_to_cropped_images;
  private final AtomicInteger i;

  /** Constructor.
   *
   * @param path_to_image          path to the target image
   * @param path_to_cropped_images  path where cropped images will be generated
   */
  public ImageProcessingFlatMap(String path_to_image, String path_to_cropped_images) {
    this.path_to_images = path_to_image;
    this.path_to_cropped_images = path_to_cropped_images;
    this.i = new AtomicInteger();
  }

  /** Finds circles (fruits) in an image, crops new images to it and collects the total count.
   *
   * @param image target image
   * @param out circles (fruits) that were found
   */
  @Override
  public void flatMap(String image, Collector<Tuple2<String, Integer>> out) {
    // ensure there is an image
    if (image.isEmpty()) return;
    // reformat to fruit type (e.g. "orange_12" becomes "orange")
    String fruit_type = image.substring(0, image.indexOf('_'));
    // detect + crop
    int num_circles = CircleDetector.detect(
        this.path_to_images + image,
        this.path_to_cropped_images + fruit_type + "_" + i.getAndIncrement(),
        100, 0.4);
    // save the number of circles (fruits) found in this image
    out.collect(new Tuple2<>(fruit_type, num_circles));
  }
}
