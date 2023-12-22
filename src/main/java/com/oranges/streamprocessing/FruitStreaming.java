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

import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

// medium
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
//

// ***** me
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.util.Collector;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

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

    public static final class Tokenizer implements MapFunction<String, Tuple2<String, Integer>> {
        @Override
        public Tuple2<String, Integer> map(String s) throws Exception {
            return new Tuple2<String, Integer>(s, 1);
        }
    }

    public static void main(String[] args) throws Exception {
        // steps from:
        // https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/dev/datastream/overview/#anatomy-of-a-flink-program

        // 1. Obtain an execution environment (main entry point to building Flink applications)
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 2. Load/create the initial data
        final ParameterTool params = ParameterTool.fromArgs(args); // input
        env.getConfig().setGlobalJobParameters(params);  // make available in UI
        DataStream<String> text = env.socketTextStream("localhost", 2023); // get input stream from port 2023

        // TODO 1 take input from a directory of files
        // TODO 2 monitor this directory at windowed intervals

        // 3. Specify transformations on this data

        // TODO 3 replace words by images

        // 4. Specify where to put the results of your computations
        DataStream<Tuple2<String, Integer>> orange_count =
            text.filter(new org.apache.flink.api.common.functions.FilterFunction<String>() {
            public boolean filter(String value) {
                return value.endsWith("orange");
            }})
                .map(new Tokenizer()) // split up the lines in pairs (2-tuples) containing: tuple2 {(rotten,1)...}
                .keyBy(0).sum(1); // group by the tuple field "0" and sum up tuple field "1"

        // 5. Trigger the program execution
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
