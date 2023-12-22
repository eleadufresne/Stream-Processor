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

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

// medium
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
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

    public static class Splitter implements FlatMapFunction<String, Tuple2<String, Integer>> {
        @Override
        public void flatMap(String sentence, Collector<Tuple2<String, Integer>> out) throws Exception {
            for (String word: sentence.split(" ")) {
                out.collect(new Tuple2<String, Integer>(word, 1));
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

        DataStream<Tuple2<String, Integer>> data_stream = env
            .socketTextStream("localhost", 9999) // get input stream from port 9999
            .flatMap(new Splitter()) // split based on newlines
            .keyBy(value -> value.f0) // keyBy groups tuples based on the "0" field, i.e. the word
            .window(TumblingProcessingTimeWindows.of(Time.seconds(5)))
            .sum(1);

        /* https://github.com/SparkWorksnet/demoFlink/blob/master/src/main/java/net/sparkworks/stream/StreamProcessor.java
        // Define the window and apply the reduce transformation
        DataStream resultStream = keyedStream
                .timeWindow(Time.seconds(10))
                .reduce(new SensorDataAverageReduce());
         */

        // TODO take input from a directory of files

        /* 3. Specify transformations on this data */

        // TODO replace words by images

        /* 4. Specify where to put the results of your computations */

        // TODO monitor this directory at windowed intervals

        /* 5. Trigger the program execution */
        data_stream.print();
        env.execute("Streaming: oranges monitoring");
    }
}
