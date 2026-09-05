package com.docquery.document.model;

/** 一簇扩窗后的一条证据。黄金集仍打 RRF 命中切片，不看这段正文。 */
public record EvidenceWindow(Long primaryChunkId, Long documentId, int startChunkIndex, int endChunkIndex,
        String content, String source, double rawScore, double score) {

    public EvidenceWindow(Long primaryChunkId, Long documentId, int startChunkIndex, int endChunkIndex,
            String content) {
        this(primaryChunkId, documentId, startChunkIndex, endChunkIndex, content, "KEYWORD", 0, 0);
    }
}
