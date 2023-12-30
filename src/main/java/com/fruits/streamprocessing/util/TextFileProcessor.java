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

package com.fruits.streamprocessing.util;

import com.fruits.streamprocessing.FruitStreaming;
import com.fruits.streamprocessing.util.operators.TextTokenizer;

import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

/** Processor for text files monitored in the {@link FruitStreaming} app. */
public class TextFileProcessor implements StreamProcessor {
    /** Apply transformations on the input stream to count all fruits captured in 10-second intervals.
     *
     * @param input_stream stream to transform
     * @return  transformed stream
     */
    public DataStream<Tuple2<String, Integer>> process(DataStream<String> input_stream) {
        // filter out everything that is not an orange, and aggregate them by features
        return input_stream
                .filter((FilterFunction<String>) value -> value.endsWith("orange"))
                .name("filter: oranges")
                .map(new TextTokenizer())
                .name("map: tuple")
                .keyBy(value -> value.f0)
                .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
                .sum(1) // sums the results that were accumulated over 10s
                .name("aggregate - group fruit types over 10s");
    }
}
