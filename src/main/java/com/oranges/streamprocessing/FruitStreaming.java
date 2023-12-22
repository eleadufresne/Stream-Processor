package com.oranges.streamprocessing;

import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

// medium
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;

/* Build this artifact by running  ``mvn clean package`` in the root directory.
*
* TERMINAL #1
*   nc -l 9999
*
* TERMINAL #2
*  cd flink* && .bin/start-cluster
*  cd ../fruit* && mvn clean package && cd ../flink*
*  ./bin/flink run ../fruit* /tar* /fruit*.jar
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

    public static final class Tokenizer implements MapFunction<String, Tuple2<String, Integer>> {
        @Override
        public Tuple2<String, Integer> map(String s) throws Exception {
            return new Tuple2<String, Integer>(s, 1);
        }
    }

    public static void main(String[] args) throws Exception {
        /* steps from: https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/dev/datastream/overview/#anatomy-of-a-flink-program
         * 1. Obtain an execution environment (main entry point to building Flink applications) */
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        /* 2. Load/create the initial data */
        final ParameterTool params = ParameterTool.fromArgs(args); // input
        env.getConfig().setGlobalJobParameters(params);  // make the input available in the Flink UI
        DataStream<String> data_stream = env.socketTextStream("localhost", 9999); // get input stream from port 9999

        DataStream<Tuple2<String, Integer>> orange_count = data_stream
            .filter((FilterFunction<String>) value -> value.endsWith("orange")) // keep oranges
            .map(new Tokenizer()) // split up the lines in pairs (2-tuples) containing: tuple2 {(rotten,1)...}
            .keyBy(value -> value.f0) // group by the tuple field "0" (orange feature)
            .window(TumblingProcessingTimeWindows.of(Time.seconds(10))) // every 5 seconds
            .sum(1); // sum up tuple field "1" (the number of such oranges)

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
        orange_count.print();
        env.execute("Streaming: oranges monitoring");
    }
}
