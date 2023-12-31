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

    /** Main method for the windows stream processing app.
     * @param args command-line arguments
     */
    public static void main(String[] args) throws Exception {
        // print help information and return if --help is the first argument
        if (args.length >= 1 && "--help".equals(args[0])) {
            help();
            return;
        }
        // obtain an execution environment (main entry point to building Flink apps)
        System.out.println("Setting up execution environment...");
        StreamExecutionEnvironment environment = StreamExecutionEnvironment.getExecutionEnvironment();

        // get the command line args and make them available in the Flink UI
        final ParameterTool params = ParameterTool.fromArgs(args);
        environment.getConfig().setGlobalJobParameters(params);

        // set the directory paths
        Map<String, String> paths = new HashMap<>();
        paths.put("work", System.getProperty("user.dir"));
        paths.put("default", paths.get("work") + "/orchard-watch");
        paths.put("input", params.get("input", paths.get("default") + "/data"));
        paths.put("output", params.get("output", paths.get("default") + "/output"));
        paths.put("checkpoints", params.get("checkpoint", paths.get("default") +
            "/checkpoints"));

        paths.forEach((key, value) -> System.out.println(key + " dir: " + value));

        // set the DB credentials
        System.out.println("Setting up database credentials...");
        SinkFunction<Tuple2<String, Integer>> database_sink = new DBSink(
            params.get("url", "jdbc:mysql://localhost:3306/fruits"),
            params.get("user", "fruit_enthusiast"),
            params.get("password", "Fru!t5"));

        // set up performance metrics
        environment.getConfig().setLatencyTrackingInterval(50); // ms

        // Configure checkpoint rules
        environment.enableCheckpointing(10000); // enable checkpoints every 10s
        // environment.getCheckpointConfig().setMinPauseBetweenCheckpoints(500); // progress between checkpoints (ms)
        environment.getCheckpointConfig().setTolerableCheckpointFailureNumber(2); // consecutive checkpoint failures are tolerated
        environment.getCheckpointConfig().setMaxConcurrentCheckpoints(1); // # checkpoints in progress at the same time
        environment.getCheckpointConfig().setCheckpointStorage("file:"+paths.get("checkpoints")); // checkpoint storage

        // set a source directory to be monitored every 10s
        FileSource<String> source =
            FileSource.forRecordStreamFormat(
                    new TextLineInputFormat(), new Path("file:"+paths.get("input")))
                .monitorContinuously(Duration.ofSeconds(10)) // monitor every 10s
                .build();

        // apply transformations on the input stream to count all fruits captured in 10-second intervals
        DataStream<Tuple2<String, Integer>> output_stream = StreamProcessorFactory.get_processor(
            // set the execution mode for the data processing strategy
            ExecutionMode.valueOf(params.get("mode", "IMAGES")), paths)
            // delegate the processing to the strategy implementation
            .process(environment.fromSource(source, WatermarkStrategy.noWatermarks(),"Monitored directory"));

        // sink to a log file containing results from this 10-second interval.
        OutputFileConfig output_file = OutputFileConfig.builder()
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

        output_stream.sinkTo(file_sink).name("File")
            .setDescription("Sink to a log file containing results from this 10-second interval.");

        // sink to a database and update it with the results from this 10-second interval.
        output_stream.addSink(database_sink)
            .name("Database")
            .setDescription("Sink to a database and update it with the results from this 10-second interval.");

        // triggers the job
        environment.execute("Executing stream processing job: " + params.get("job", "monitoring orchard"));
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