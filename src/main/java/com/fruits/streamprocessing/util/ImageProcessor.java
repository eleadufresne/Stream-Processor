package com.fruits.streamprocessing.util;

import com.fruits.streamprocessing.FruitStreaming;
import com.fruits.streamprocessing.util.operators.ImageTokenizer;

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
    final private String input_path, output_path;
    /** constructor
     *
     * @param paths paths for the application
     */
    public ImageProcessor(Map<String, String> paths) {
        input_path = paths.get("input") + "/images/";
        output_path = paths.get("output") + "/processed-images/";
    }

    /** Apply transformations on the input stream to count all fruits captured in 10-second intervals.
     *
     * @param input_stream stream to transform
     * @return  transformed stream
     */
    public DataStream<Tuple2<String, Integer>> process(DataStream<String> input_stream) {
        // count the circles (placeholder for fruits) in this window's images
        return input_stream.rescale()
                .filter((FilterFunction<String>) value -> value.endsWith(".png") || value.endsWith(".jpg"))
                .setDescription("Keep only the path to images (i.e. PNG or JPG).")
                .name("Filter").uid("image-filter").rescale()
                .map(new ImageTokenizer(input_path, output_path)).setParallelism(8)
                .setDescription("Detect the fruits each image and crop them accordingly.")
                .name("Map").uid("fruit-map").startNewChain().rescale()
                // group by the first value (i.e. the fruit type)
                .keyBy(value -> value.f0)
                // collect over 10s
                .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
                // sum the results
                .sum(1)
                .setDescription("Group and sum fruit types over 10-second intervals.")
                .name("Keyed Aggregation").uid("keyed-aggregation").startNewChain();
    }
}
