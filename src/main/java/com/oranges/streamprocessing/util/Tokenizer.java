package com.oranges.streamprocessing.util;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;

/** MapFunction takes a string and maps it to a tuple of string and integer */
public final class Tokenizer implements MapFunction<String, Tuple2<String, Integer>> {
    /** @param s input string to be mapped
     *  @return a Tuple2 containing the input string paired with the number 1 */
    @Override
    public Tuple2<String, Integer> map(String s) {
        return new Tuple2<>(s, 1);
    }
}
