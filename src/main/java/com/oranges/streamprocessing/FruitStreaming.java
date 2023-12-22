/**
 * Flink-based data processing app that reads text files from a directory, filters and counts
 * different types of oranges, and prints the result. To run the app, specify the directory path
 * as a command-line argument. For example:
 *
 * ./bin/flink run /path/to/fruit-streaming-1.jar --path data/
 *
 * useful documentation :
 * https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/dev/datastream/overview/#anatomy-of-a-flink-program
 *
 */

package com.oranges.streamprocessing;

import java.time.Duration;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

import org.apache.flink.connector.file.src.FileSource;


/** @author Éléa Dufresne */
public class FruitStreaming {

    /** MapFunction takes a string and maps it to a tuple of string and integer */
    public static final class Tokenizer implements MapFunction<String, Tuple2<String, Integer>> {
        /** @param s input string to be mapped
         *  @return a Tuple2 containing the input string paired with the number 1 */
        @Override
        public Tuple2<String, Integer> map(String s) { return new Tuple2<>(s, 1); }
    }

    /** main method that executes the Flink app
     * @param args command-line arguments */
    public static void main(String[] args) throws Exception {
        /* 1. Obtain an execution environment (main entry point to building Flink applications) */
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        /* 2. Load/create the initial data */
        final ParameterTool params = ParameterTool.fromArgs(args); // input

        env.getConfig().setGlobalJobParameters(params);  // make the input available in the Flink UI

        String path_to_dir = params.get("path", "data/"); // directory to monitor

        FileSource<String> source_dir = FileSource
            .forRecordStreamFormat(new TextLineInputFormat(), new Path(path_to_dir))
            .monitorContinuously(Duration.ofSeconds(10)) // monitor every 10s
            .build();

        DataStream<String> data_stream = env.fromSource(
            source_dir,
            WatermarkStrategy.noWatermarks(),
            "source directory");

        /* https://github.com/SparkWorksnet/demoFlink/blob/master/src/main/java/net/sparkworks/stream/StreamProcessor.java
        // Define the window and apply the reduce transformation
        DataStream resultStream = keyedStream
                .timeWindow(Time.seconds(10))
                .reduce(new SensorDataAverageReduce());
         */

        // TODO take input from a directory of files

        /* 3. Specify transformations on this data */

        DataStream<Tuple2<String, Integer>> orange_count = data_stream
            .filter((FilterFunction<String>) value -> value.endsWith("orange")) // keep oranges
            .map(new Tokenizer()) // split up the lines in pairs (2-tuples) containing: tuple2 {(rotten,1)...}
            .keyBy(value -> value.f0) // group by the tuple field "0" (orange feature)
            .window(TumblingProcessingTimeWindows.of(Time.seconds(5))) // every 5 seconds
            .sum(1); // sum up tuple field "1" (the number of such oranges)

        // TODO replace words by images

        /* 4. Specify where to put the results of your computations */

        // TODO monitor this directory at windowed intervals

        /* 5. Trigger the program execution */
        orange_count.print();
        env.execute("Streaming: oranges monitoring");
    }
}
