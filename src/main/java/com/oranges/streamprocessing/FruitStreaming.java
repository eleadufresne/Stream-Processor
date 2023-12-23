/**
 * Flink-based data processing app that reads text files from a directory, filters and counts
 * different types of oranges, and prints the result. To run the app, specify the directory path as
 * a command-line argument. For example:
 *
 * <p>./bin/flink run /path/to/fruit-streaming-1.jar --path data/
 */
package com.oranges.streamprocessing;


import com.oranges.streamprocessing.util.FruitDatabaseSink;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.OnCheckpointRollingPolicy;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

import java.time.Duration;

/**
 * @author Éléa Dufresne
 */
public class FruitStreaming {

    /** MapFunction takes a string and maps it to a tuple of string and integer */
    public static final class Tokenizer implements MapFunction<String, Tuple2<String, Integer>> {
        /**
         * @param s input string to be mapped
         * @return a Tuple2 containing the input string paired with the number 1
         */
        @Override
        public Tuple2<String, Integer> map(String s) {
            return new Tuple2<>(s, 1);
        }
    }

    /**
     * main method that executes the Flink app
     * https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/dev/datastream/overview/#anatomy-of-a-flink-program
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) throws Exception {
        /* STEP 1 - obtain an execution environment (main entry point to building Flink apps) ****/

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // enable checkpoints every 10.5s (Flink needs to complete checkpoints to finalize writing)
        env.enableCheckpointing(10500);

        /* STEP 2 - load the initial data ********************************************************/
        // TODO input images

        final ParameterTool params = ParameterTool.fromArgs(args); // input
        env.getConfig().setGlobalJobParameters(params); // make it available in the Flink UI

        // monitor the directory given by the "--path" argument or default to "data/"
        String input_path = params.get("path", "data/");

        // set a source (DAG)
        FileSource<String> source =
                FileSource.forRecordStreamFormat(new TextLineInputFormat(), new Path(input_path))
                        .monitorContinuously(Duration.ofSeconds(10)) // monitor every 10s
                        .build();

        // save data to a stream
        DataStream<String> data_stream =
                env.fromSource(source, WatermarkStrategy.noWatermarks(), "source directory");

        /* STEP 3 - transform the data ***********************************************************/

        // filter out everything that is not an orange, and aggregate them by features
        DataStream<Tuple2<String, Integer>> orange_count =
                data_stream
                        .filter((FilterFunction<String>) value -> value.endsWith("orange"))
                        .map(new Tokenizer())
                        .keyBy(value -> value.f0)
                        .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
                        .sum(1); // sums the results that were accumulated over 9s

        /* STEP 4 - store the results ************************************************************/

        // output to a text file
        OutputFileConfig output_file =
                OutputFileConfig.builder()
                        .withPartPrefix("monitoring-data")
                        .withPartSuffix(".txt")
                        .build();

        FileSink<Tuple2<String, Integer>> file_sink =
                FileSink.forRowFormat(
                                new Path(input_path + "monitoring-files"),
                                new SimpleStringEncoder<Tuple2<String, Integer>>("UTF-8"))
                        .withOutputFileConfig(output_file)
                        .withRollingPolicy(OnCheckpointRollingPolicy.build()) // this is important
                        .build();

        orange_count.sinkTo(file_sink);

        // output to a database

        // credentials
        String connection_url = "jdbc:mysql://localhost:3306/fruits";
        String username = "user";
        String password = "oranges";

        SinkFunction<Tuple2<String, Integer>> database_sink =
            new FruitDatabaseSink(connection_url, username, password);
        orange_count.addSink(database_sink);

        //orange_count.windowAll(TumblingProcessingTimeWindows.of(Time.seconds(10)));

        /* STEP 5 - trigger the program execution ************************************************/
        orange_count.print();
        env.execute("Streaming: oranges monitoring");
    }
}
