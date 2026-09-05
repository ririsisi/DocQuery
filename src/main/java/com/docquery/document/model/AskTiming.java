package com.docquery.document.model;

public record AskTiming(String requestId, AskMode mode, long embedMs, long retrieveMs, long llmMs, long totalMs) {

    public static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
