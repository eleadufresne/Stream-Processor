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

import com.fruits.streamprocessing.util.CircleDetector;
import com.fruits.streamprocessing.util.DBSink;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
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
import org.apache.flink.util.Collector;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** Flink-based data processing app that reads files from a directory every 10s, filters and counts
 *  different types of oranges, and prints the result.
 *
 *  @author Éléa Dufresne
 */
public class FruitStreaming {

    /** Main method for the windows stream processing app.
     * @see <a href="https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/dev/datastream/overview/#anatomy-of-a-flink-program">...</a>
     * @param args command-line arguments
     */
    public static void main(String[] args) throws Exception {
        /* STEP 1 - obtain an execution environment (main entry point to building Flink apps) ****/
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        final String work_dir = System.getProperty("user.dir");
        System.out.println("Working directory: " + work_dir); // debug



        /* STEP 2 - load the initial data ********************************************************/
        final ParameterTool params = ParameterTool.fromArgs(args); // input
        env.getConfig().setGlobalJobParameters(params); // make it available in the Flink UI

        // monitor the directory given by the "--path" argument or default to "data/"
        String default_folder = "file:"+work_dir+"/fruit-dir";
        String input_dir = params.get("input", default_folder + "/data");
        String output_dir = params.get("output", default_folder + "/logs");
        String checkpoint_dir = params.get("checkpoint", default_folder+ "/checkpoints");

        /* CHECKPOINTS ***************************************************************************/

        // enable checkpoints every 10.5s (Flink needs to complete checkpoints to finalize writing)
        env.enableCheckpointing(1000);
        // make sure 500 ms of progress happen between checkpoints
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(500);
        // only two consecutive checkpoint failures are tolerated
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(2);
        // allow only one checkpoint to be in progress at the same time
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        // enable externalized checkpoints which are retained after job cancellation
        env.getCheckpointConfig().setExternalizedCheckpointCleanup(
            CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        // sets the checkpoint storage where checkpoint snapshots will be written
        env.getCheckpointConfig().setCheckpointStorage(checkpoint_dir);

        // set a source directory to be monitored
        FileSource<String> source =
            FileSource.forRecordStreamFormat(new TextLineInputFormat(), new Path(input_dir))
                .monitorContinuously(Duration.ofSeconds(10)) // monitor every 10s
                .build();

        // save data to a stream
        DataStream<String> data_stream = env
            .fromSource(source, WatermarkStrategy.noWatermarks(), "Source: directory");

        /* STEP 3 - transform the data ***********************************************************/

        // count the circles (placeholder for oranges) in this window's images
        String path_to_images_prefix = work_dir + "/fruit-dir/data/images/";
        String path_to_filtered_images = path_to_images_prefix + "filtered-images/";
        AtomicInteger i = new AtomicInteger();

        DataStream<Tuple2<String, Integer>> count = data_stream
                .filter((FilterFunction<String>) value -> value.endsWith(".png") || value.endsWith(".jpg"))
                .name("Filter: PNG and JPG")
                .flatMap((String image, Collector<Tuple2<String, Integer>> out) -> {
                    if(image.isEmpty()) return;
                    String fruit_type = image.substring(0, image.indexOf('_')); // "orange_12" -> "orange"
                    int num_circles = CircleDetector.detect( // detect + crop
                        path_to_images_prefix + image,
                        path_to_filtered_images + fruit_type + "_" + i.getAndIncrement(),
                        100, 0.4);
                    out.collect(new Tuple2<>(fruit_type, num_circles)); // save the curr count
                }) // provide type information so the compiler stops yelling at me
                .returns(TypeInformation.of(new TypeHint<Tuple2<String, Integer>>() {}))
                .name("FlatMap: detect and crop")
                // group by the first value (i.e. the fruit type)
                .keyBy(value -> value.f0)
                // collect over 10s
                .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
                // sum the results
                .sum(1)
                .name("Aggregate: group fruit types over 10s");

        /* STEP 4 - store the results ************************************************************/

        // SINK to a text file
        OutputFileConfig output_file = OutputFileConfig.builder()
                .withPartPrefix("monitoring-data")
                .withPartSuffix(".txt")
                .build();

        FileSink<Tuple2<String, Integer>> file_sink = FileSink.forRowFormat(
                    new Path(output_dir),
                    new SimpleStringEncoder<Tuple2<String, Integer>>("UTF-8"))
                .withOutputFileConfig(output_file)
                .withRollingPolicy(OnCheckpointRollingPolicy.build()) // this is important
                .build();

        count.sinkTo(file_sink).name("Sink: log file");

        // SINK to a database
        SinkFunction<Tuple2<String, Integer>> database_sink =
            new DBSink("jdbc:mysql://localhost:3306/fruits", "fruit_enthusiast", "Fru!t5");

        count.addSink(database_sink).name("Sink: database");

        /* STEP 5 - trigger the program execution ************************************************/
        count.print();
        env.execute("Stream Processing: fruit monitoring");

        // TODO Have a script that can execute all the experiments in your project.
    }

    /** (helper) MapFunction takes a string and maps it to a tuple of string and integer */
    private static class TextTokenizer implements MapFunction<String, Tuple2<String, Integer>> {
        /** @param s input string to be mapped
         *  @return a Tuple2 containing the input string paired with the number 1 */
        @Override
        public Tuple2<String, Integer> map(String s) {
            return new Tuple2<>(s, 1);
        }
    }
}