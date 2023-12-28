package com.fruits.streamprocessing.util;

import com.fruits.streamprocessing.FruitStreaming;

import java.util.Map;

/** factory that creates data processors for {@link FruitStreaming}
 * @author Éléa Dufresne
 */
public class StreamProcessorFactory {
    public static StreamProcessor getProcessor(ExecutionMode mode, Map<String, String> paths) {
        switch (mode) {
            case TEXT:
                return new TextFileProcessor();
            case IMAGES:
                return new ImageProcessor(paths);
            default:
                throw new IllegalArgumentException("Unsupported execution mode: " + mode);
        }
    }
}
