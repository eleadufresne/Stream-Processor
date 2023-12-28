package com.fruits.streamprocessing.util;

import com.fruits.streamprocessing.FruitStreaming;

import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
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
        String path_to_images_prefix = this.paths.get("work") + "/fruit-dir/data/images/";
        String path_to_filtered_images = path_to_images_prefix + "filtered-images/";

        // count the circles (placeholder for fruits) in this window's images
        return input_stream
                .filter(
                        (FilterFunction<String>)
                                value -> value.endsWith(".png") || value.endsWith(".jpg"))
                .name("Filter: PNG and JPG")
                .flatMap(new ImageProcessingFlatMap(path_to_images_prefix, path_to_filtered_images))
                // provide type information so the compiler stops yelling at me
                .returns(TypeInformation.of(new TypeHint<Tuple2<String, Integer>>() {}))
                .name("FlatMap: detect and crop")
                // group by the first value (i.e. the fruit type)
                .keyBy(value -> value.f0)
                // collect over 10s
                .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
                // sum the results
                .sum(1)
                .name("Aggregate: group fruit types over 10s");
    }
}
