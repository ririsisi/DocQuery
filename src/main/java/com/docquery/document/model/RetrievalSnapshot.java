package com.docquery.document.model;

import java.util.List;

/**
 * hits = RRF TopK，黄金集只打这个。
 * evidence = 类簇 + ±1 正文；level 在生成前判定。
 */
public record RetrievalSnapshot(String requestId, List<DocumentChunk> hits, List<EvidenceWindow> evidence,
        EvidenceLevel level, List<Citation> citations, long embedMs, long retrieveMs) {
}
