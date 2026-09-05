package com.docquery.eval;

import java.util.List;

import com.docquery.document.model.DocumentChunk;

public final class RetrievalJudge {

    private RetrievalJudge() {
    }

    /** 标针第一次出现的 1-based 位次；未进 Top5 为 null。负例标针为空，视为未命中。 */
    public static Integer rankAt5(List<DocumentChunk> topChunks, String expectedHitContains) {
        if (expectedHitContains == null || expectedHitContains.isBlank() || topChunks == null) {
            return null;
        }
        String needle = expectedHitContains.trim();
        int limit = Math.min(5, topChunks.size());
        for (int i = 0; i < limit; i++) {
            String text = topChunks.get(i).getContent();
            if (text != null && text.contains(needle)) {
                return i + 1;
            }
        }
        return null;
    }

    public static boolean hitAt5(List<DocumentChunk> topChunks, String expectedHitContains) {
        return rankAt5(topChunks, expectedHitContains) != null;
    }

    public static boolean hitAt1(List<DocumentChunk> topChunks, String expectedHitContains) {
        return Integer.valueOf(1).equals(rankAt5(topChunks, expectedHitContains));
    }
}
