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

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;

/** MapFunction takes a string and maps it to a tuple of string and integer for {@link FruitStreaming}
 * @author Éléa Dufresne
 */
public class TextTokenizer implements MapFunction<String, Tuple2<String, Integer>> {
    /**
     * @param s input string to be mapped
     * @return a Tuple2 containing the input string paired with the number 1
     */
    @Override
    public Tuple2<String, Integer> map(String s) {
        return new Tuple2<>(s, 1);
    }
}
