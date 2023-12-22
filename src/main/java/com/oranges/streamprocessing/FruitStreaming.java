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

package com.oranges.streamprocessing;

import com.oranges.streamprocessing.util.CLI;
import com.oranges.streamprocessing.util.TreeData;
import java.time.Duration;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

// medium
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.flink.util.Collector;
//

// ***** me


// *****

/**
 * Skeleton for a Flink DataStream Job.
 *
 * <p>For a tutorial how to write a Flink application, check the tutorials and examples on the <a
 * href="https://flink.apache.org">Flink Website</a>.
 *
 * <p>To package your application into a JAR file for execution, run 'mvn clean package' on the
 * command line.
 *
 * <p>If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
 */
public class FruitStreaming {

    /**
     * Implements the string tokenizer that splits sentences into words as a user-defined
     * FlatMapFunction. The function takes a line (String) and splits it into multiple pairs in the
     * form of "(word,1)" ({@code Tuple2<String, Integer>}).
     */
    public static final class Tokenizer
        implements FlatMapFunction<String, Tuple2<String, Integer>> {

        @Override
        public void flatMap(String value, Collector<Tuple2<String, Integer>> out) {
            // normalize and split the line
            String[] tokens = value.toLowerCase().split("\\W+");

            // emit the pairs
            for (String token : tokens) {
                if (token.length() > 0) {
                    out.collect(new Tuple2<>(token, 1));
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        /* steps from: https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/dev/datastream/overview/#anatomy-of-a-flink-program
         * 1. Obtain an execution environment (main entry point to building Flink applications) */
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        /* 2. Load/create the initial data */
        // final ParameterTool params = ParameterTool.fromArgs(args); // input
        // env.getConfig().setGlobalJobParameters(params);  // make the input available in the Flink UI
        // DataStream<String> string_data_stream = env.socketTextStream("localhost", 9999); // get input stream from port 9999

        // TODO take input from a directory of files

        final CLI params = CLI.fromArgs(args);
        env.setRuntimeMode(params.getExecutionMode());
        env.getConfig().setGlobalJobParameters(params);  // make the input available in the Flink UI

        DataStream<String> string_data_stream;
        if (params.getInputs().isPresent()) {
            // Create a new file source that will read files from a given set of directories.
            // Each file will be processed as plain text and split based on newlines.
            FileSource.FileSourceBuilder<String> builder =
                FileSource.forRecordStreamFormat(
                    new TextLineInputFormat(), params.getInputs().get());

            // If a discovery interval is provided, the source will
            // continuously watch the given directories for new files.
            params.getDiscoveryInterval().ifPresent(builder::monitorContinuously);
            string_data_stream = env.fromSource(builder.build(), WatermarkStrategy.noWatermarks(), "file-input");
        } else {
            string_data_stream = env.fromElements(TreeData.WORDS).name("in-memory-input");
        }

        /* 3. Specify transformations on this data */

        // TODO replace words by images

        /* 4. Specify where to put the results of your computations */

        // TODO monitor this directory at windowed intervals
        int windowSize = params.getInt("window").orElse(250);
        int slideSize = params.getInt("slide").orElse(150);

        DataStream<Tuple2<String, Integer>> orange_count =
            // The text lines read from the source are split into words
            // using a user-defined function. The tokenizer, implemented below,
            // will output each words as a (2-tuple) containing (word, 1)
            string_data_stream.flatMap(new Tokenizer())
                .name("tokenizer")
                // keyBy groups tuples based on the "0" field, the word.
                // Using a keyBy allows performing aggregations and other
                // stateful transformations over data on a per-key basis.
                // This is similar to a GROUP BY clause in a SQL query.
                .keyBy(value -> value.f0)
                // create windows of windowSize records slided every slideSize records
                .countWindow(windowSize, slideSize)
                // For each key, we perform a simple sum of the "1" field, the count.
                // If the input data set is bounded, sum will output a final count for
                // each word. If it is unbounded, it will continuously output updates
                // each time it sees a new instance of each word in the stream.
                .sum(1)
                .name("counter");

        if (params.getOutput().isPresent()) {
            // Given an output directory, Flink will write the results to a file
            // using a simple string encoding. In a production environment, this might
            // be something more structured like CSV, Avro, JSON, or Parquet.
            orange_count.sinkTo(
                    FileSink.<Tuple2<String, Integer>>forRowFormat(
                            params.getOutput().get(), new SimpleStringEncoder<>())
                        .withRollingPolicy(
                            DefaultRollingPolicy.builder()
                                .withMaxPartSize(MemorySize.ofMebiBytes(1))
                                .withRolloverInterval(Duration.ofSeconds(10))
                                .build())
                        .build())
                .name("file-sink");
        } else {
            orange_count.print().name("print-sink");
        }

        /* 5. Trigger the program execution */
        orange_count.print();
        env.execute("Streaming: oranges monitoring");

        /*
         * Here, you can start creating your execution plan for Flink.
         *
         * Start with getting some data from the environment, like
         * 	env.fromSequence(1, 10);
         *
         * then, transform the resulting DataStream<Long> using operations
         * like
         * 	.filter()
         * 	.flatMap()
         * 	.window()
         * 	.process()
         *
         * and many more.
         * Have a look at the programming guide:
         *
         * https://nightlies.apache.org/flink/flink-docs-stable/
         *
         */

    }
}
