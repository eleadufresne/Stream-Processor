package com.oranges.streamprocessing;

import com.oranges.streamprocessing.util.FruitDatabaseSink;
import com.oranges.streamprocessing.util.Tokenizer;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.OnCheckpointRollingPolicy;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

import java.time.Duration;

/** Flink-based data processing app that reads files from a directory every 10s, filters and counts
 *  different types of oranges, and prints the result.
 *
 *  @author Éléa Dufresne */
public class FruitStreaming {

    /** https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/dev/datastream/overview/#anatomy-of-a-flink-program
     * @param args command-line arguments  */
    public static void main(String[] args) throws Exception {
        /* STEP 1 - obtain an execution environment (main entry point to building Flink apps) ****/
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        System.out.println("System: "  + System.getProperty("os.name"));
        System.out.println("Config: "  + env.getConfiguration());

        // https://nightlies.apache.org/flink/flink-docs-master/docs/dev/datastream/fault-tolerance/checkpointing/#enabling-and-configuring-checkpointing
        // enable checkpoints every 10.5s (Flink needs to complete checkpoints to finalize writing)
        env.enableCheckpointing(1000);
        // make sure 500 ms of progress happen between checkpoints
        // env.getCheckpointConfig().setMinPauseBetweenCheckpoints(500);
        // only two consecutive checkpoint failures are tolerated
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(2);
        // allow only one checkpoint to be in progress at the same time
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        // enable externalized checkpoints which are retained after job cancellation
        env.getCheckpointConfig().setExternalizedCheckpointCleanup(
            CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        // sets the checkpoint storage where checkpoint snapshots will be written
        env.getCheckpointConfig().setCheckpointStorage("file:/home/ecchymose/data/checkpoints");

        /* STEP 2 - load the initial data ************************************TODO input images***/
        final ParameterTool params = ParameterTool.fromArgs(args); // input
        env.getConfig().setGlobalJobParameters(params); // make it available in the Flink UI

        // monitor the directory given by the "--path" argument or default to "data/"
        String input_path = params.get("path", "file:/home/ecchymose/data");

        // System.out.println("Current working directory: " + System.getProperty("user.dir"));

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
        DataStream<Tuple2<String, Integer>> count = // TODO pipeline of the functions on this
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

        // set a sink
        FileSink<Tuple2<String, Integer>> file_sink =
            FileSink.forRowFormat(
                    new Path(input_path + "monitoring-files"),
                    new SimpleStringEncoder<Tuple2<String, Integer>>("UTF-8"))
                .withOutputFileConfig(output_file)
                .withRollingPolicy(OnCheckpointRollingPolicy.build()) // this is important
                .build();

        count.sinkTo(file_sink);

        // output to a database

        // credentials
        String connection_url = "jdbc:mysql://localhost:3306/fruits";
        String username = "fruit_enthusiast";
        String password = "Fru!t5";

        // set a second sink
        SinkFunction<Tuple2<String, Integer>> database_sink =
            new FruitDatabaseSink(connection_url, username, password);

        count.addSink(database_sink);

        //orange_count.windowAll(TumblingProcessingTimeWindows.of(Time.seconds(10)));

        /* STEP 5 - trigger the program execution ************************************************/
        count.print();
        env.execute("Streaming: fruit monitoring");

        // TODO Have a script that can execute all the experiments in your project.
    }
}