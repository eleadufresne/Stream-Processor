package com.fruits.streamprocessing.util;

import com.fruits.streamprocessing.FruitStreaming;
import com.fruits.streamprocessing.util.operators.FruitDetector;

import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

import java.util.Map;

/** Processor for image files monitored in the {@link FruitStreaming} app.
 *  @author Éléa Dufresne
 */
public class ImageProcessor implements StreamProcessor {
    final private Map<String, String> paths;
    /** constructor
     *
     * @param paths paths for the application
     */
    public ImageProcessor(Map<String, String> paths) { this.paths = paths; }

    /** Apply transformations on the input stream to count all fruits captured in 10-second intervals.
     *
     * @param input_stream stream to transform
     * @return  transformed stream
     */
    public DataStream<Tuple2<String, Integer>> process(DataStream<String> input_stream) {
        String path_to_images = paths.get("input") + "/images/";
        String fruit_images = paths.get("output") + "/processed-images/";

        // count the circles (placeholder for fruits) in this window's images
        return input_stream
                .filter((FilterFunction<String>) value -> value.endsWith(".png") || value.endsWith(".jpg"))
                .setDescription("Keep only the path to images (i.e. PNG or JPG).")
                .name("Filter")
                .map(new FruitDetector(path_to_images, fruit_images))
                .setDescription("Detect the fruits each image and crop them accordingly.")//.setParallelism(50)
                .name("Map")
                // group by the first value (i.e. the fruit type)
                .keyBy(value -> value.f0)
                // collect over 10s
                .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
                // sum the results
                .sum(1)
                .setDescription("Group and sum fruit types over 10-second intervals.")
                .name("Keyed Aggregation");
    }
}
