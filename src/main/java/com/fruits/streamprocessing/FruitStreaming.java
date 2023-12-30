/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fruits.streamprocessing;

import com.fruits.streamprocessing.util.DBSink;
import com.fruits.streamprocessing.util.ExecutionMode;
import com.fruits.streamprocessing.util.StreamProcessor;
import com.fruits.streamprocessing.util.StreamProcessorFactory;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Flink-based data processing app that reads files from a directory every 10s, filters and counts
 *  different types of oranges, and prints the result.
 *
 *  @author Éléa Dufresne
 */
public class FruitStreaming {

    private final StreamExecutionEnvironment environment;
    private final Map<String, String> paths = new HashMap<>();
    private final Map<String, String> db = new HashMap<>();
    private DataStream<String> input_stream;
    private DataStream<Tuple2<String, Integer>> output_stream;

    private final StreamProcessor stream_processor;
    private final String job_name;

    /** Constructor.
     *
     * @param args command line arguments
     */
    public FruitStreaming(String[] args) {
        System.out.println("Setting up execution environment...");
        this.environment = StreamExecutionEnvironment.getExecutionEnvironment();

        // get the command line args and make them available in the Flink UI
        final ParameterTool params = ParameterTool.fromArgs(args);
        environment.getConfig().setGlobalJobParameters(params);

        // set the job name's
        this.job_name = params.get("job", "monitoring orchard");

        // set the execution mode and data processing strategy
        ExecutionMode execution_mode = ExecutionMode.valueOf(params.get("mode", "IMAGES"));
        this.stream_processor = StreamProcessorFactory.get_processor(execution_mode, paths);


        // set the directory paths
        System.out.println("Setting up directories...");
        this.paths.put("work", System.getProperty("user.dir"));
        this.paths.put("default", paths.get("work") + "/orchard-watch");
        this.paths.put("input", params.get("input", paths.get("default") + "/data"));
        this.paths.put("output", params.get("output", paths.get("default") + "/output"));
        this.paths.put("checkpoints", params.get("checkpoint", paths.get("default") +
            "/checkpoints"));

        this.paths.forEach((key, value) -> System.out.println(key + " dir: " + value));

        // set the DB credentials
        System.out.println("Setting up database credentials...");
        this.db.put("url", params.get("url", "jdbc:mysql://localhost:3306/fruits"));
        this.db.put("user", params.get("user", "fruit_enthusiast"));
        this.db.put("password", params.get("password", "Fru!t5"));

        this.db.forEach((key, value) -> System.out.println(key + ": " + value));

        // set up performance metrics
        environment.getConfig().setLatencyTrackingInterval(10); // ms
    }

    /** Configure checkpoint rules and enable checkpointing. */
    private void checkpointing() {
        // enable checkpoints every 10s (Flink needs to complete checkpoints to finalize writing)
        environment.enableCheckpointing(1000);
        // make sure 500 ms of progress happen between checkpoints
        environment.getCheckpointConfig().setMinPauseBetweenCheckpoints(500);
        // only two consecutive checkpoint failures are tolerated
        environment.getCheckpointConfig().setTolerableCheckpointFailureNumber(1);
        // allow only one checkpoint to be in progress at the same time
        environment.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        // enable externalized checkpoints which are retained after job cancellation
        environment
                .getCheckpointConfig()
                .setExternalizedCheckpointCleanup(
                        CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        // sets the checkpoint storage where checkpoint snapshots will be written
        environment.getCheckpointConfig().setCheckpointStorage("file:"+this.paths.get("checkpoints"));
    }

    /** Load the input data and put it into a stream. */
    private void load_data() {
        // set a source directory to be monitored every 10s
        FileSource<String> source =
                FileSource.forRecordStreamFormat(
                                new TextLineInputFormat(), new Path("file:"+paths.get("input")))
                        .monitorContinuously(Duration.ofSeconds(10)) // monitor every 10s
                        .build();

        // save data to a stream
        this.input_stream = environment
            .fromSource(source, WatermarkStrategy.noWatermarks(),"Source: directory");
    }

    /** Apply transformations on the input stream to count all fruits captured in 10-second intervals. */
    private void process() {
        // delegate the processing to the strategy implementation
        this.output_stream = stream_processor.process(input_stream);
    }

    /** Sink to a log file containing results from this 10-second interval. */
    private void sink_to_log_file() {
        OutputFileConfig output_file =
                OutputFileConfig.builder()
                        .withPartPrefix("monitoring-data")
                        .withPartSuffix(".txt")
                        .build();

        FileSink<Tuple2<String, Integer>> file_sink =
                FileSink.forRowFormat(
                                new Path("file:"+paths.get("output")),
                                new SimpleStringEncoder<Tuple2<String, Integer>>("UTF-8"))
                        .withOutputFileConfig(output_file)
                        .withRollingPolicy(OnCheckpointRollingPolicy.build()) // this is important
                        .build();

        output_stream.sinkTo(file_sink).name("Sink: log file");
    }

    /** Sink to a database and update it with the results from this 10-second interval. */
    private void sink_to_db() {
        SinkFunction<Tuple2<String, Integer>> database_sink =
                new DBSink(db.get("url"), db.get("user"), db.get("password"));
        output_stream.addSink(database_sink).name("Sink: database");
    }

    /** Triggers the job. */
    private void monitor() throws Exception {
        this.environment.execute("Executing stream processing job: " + this.job_name);
    }

    /** Main method for the windows stream processing app.
     * @see <a href="https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/dev/datastream/overview/#anatomy-of-a-flink-program">...</a>
     * @param args command-line arguments
     */
    public static void main(String[] args) throws Exception {
        // print info on the arguments instead of starting the app
        if (args.length >= 1 && "--help".equals(args[0])) {
            help();
            return;
        }

        // STEP 1 - obtain an execution environment (main entry point to building Flink apps)
        FruitStreaming job = new FruitStreaming(args);

        // STEP 2 - load the initial data
        job.checkpointing();
        job.load_data();

        // STEP 3 - transform the data
        job.process();

        // STEP 4 - store the results
        job.sink_to_log_file();
        job.sink_to_db();

        // STEP 5 - trigger the program execution
        job.monitor();
    }

    /** Print help information for the command line arguments. */
    private static void help() {
        System.out.println("\n*** Fruit monitoring job usage *******************************************************************\n\n"
                        + "  --job [string]: name of this job; default is monitoring orchard.\n"
                        + "  --mode [TEXT|IMAGES]: execution mode of the application; default is IMAGES.\n"
                        + "  --input [path]: path to the input directory; default is {work_dir}/orchard-watch/data\n"
                        + "  --output [path]: path to the output directory; default is {work_dir}/orchard-watch/output\n"
                        + "  --checkpoint [path]: path for checkpoint data; default is {work_dir}/orchard-watch/checkpoints\n"
                        + "  --url [database URL]: URL for database connection; default is jdbc:mysql://localhost:3306/fruits\n"
                        + "  --user [database user]: database username; default is fruit_enthusiast\n"
                        + "  --password [database password]: database password; default is Fru!t5\n\n"
                        + "***************************************************************************************************\n");
    }
}