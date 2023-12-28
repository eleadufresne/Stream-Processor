package com.fruits.streamprocessing.util;

import com.fruits.streamprocessing.FruitStreaming;

/** Execution mode of {@link FruitStreaming}
 *  @author Éléa Dufresne
 */
public enum ExecutionMode {
    TEXT, IMAGES;

    @Override
    public String toString() {
        switch (this) {
            case TEXT:
                return "text files";
            case IMAGES:
                return "images";
            default:
                throw new IllegalArgumentException();
        }
    }
}
