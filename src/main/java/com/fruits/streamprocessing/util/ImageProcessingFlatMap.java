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

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.util.Collector;

public class ImageProcessingFlatMap
        implements FlatMapFunction<String, Tuple2<String, Integer>> {
    @Override
    public void flatMap(String path_to_img, Collector<Tuple2<String, Integer>> out) {
        // remove  everything that comes after the last instance of the character "/"
        // e.g. "file:/data/img.png" -> "file:/data/cropped"
        String cropped_img_dir =
            path_to_img.substring(0, path_to_img.lastIndexOf("/") + 1) + "cropped";
        // crop the image to a circle (placeholder for orange)
        CircleDetector.detect(path_to_img, cropped_img_dir, 30, 50, 100, 0.4);
        // parse the output of CircleDetector and collect results
        out.collect(new Tuple2<>("DetectedCircle", 1));
    }
}
