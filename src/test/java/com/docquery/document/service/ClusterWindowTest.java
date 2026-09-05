package com.docquery.document.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.docquery.document.model.DocumentChunk;
import com.docquery.document.model.EvidenceWindow;
import com.docquery.document.service.ClusterWindow.Cluster;
import com.docquery.document.service.RrfFusion.RankedHit;
import com.docquery.eval.RetrievalJudge;

class ClusterWindowTest {

    @Test
    void adjacentSameDocumentMergesToOneCluster() {
        List<Cluster> clusters = ClusterWindow.cluster(List.of(hit(10L, 1L, 2, 0.5), hit(11L, 1L, 3, 1.0)));
        assertEquals(1, clusters.size());
        assertEquals(2, clusters.get(0).startIndex());
        assertEquals(3, clusters.get(0).endIndex());
        assertEquals(1.0, clusters.get(0).score());
        assertEquals(11L, clusters.get(0).primaryChunkId());
    }

    @Test
    void gapDoesNotMerge() {
        List<Cluster> clusters = ClusterWindow.cluster(List.of(hit(10L, 1L, 2, 0.5), hit(12L, 1L, 4, 0.8)));
        assertEquals(2, clusters.size());
        assertEquals(4, clusters.get(0).startIndex());
        assertEquals(2, clusters.get(1).startIndex());
    }

    @Test
    void differentDocumentsNeverMerge() {
        List<Cluster> clusters = ClusterWindow.cluster(List.of(hit(10L, 1L, 2, 0.4), hit(20L, 2L, 3, 0.4)));
        assertEquals(2, clusters.size());
    }

    @Test
    void expandLoadsInclusiveNeighborRange() {
        RankedHit hit = new RankedHit(chunk(11L, 1L, 2, "hit"), 1.0);
        List<EvidenceWindow> windows = ClusterWindow.clusterAndExpand(List.of(hit), (docId, from, to) -> {
            assertEquals(1L, docId);
            assertEquals(1, from);
            assertEquals(3, to);
            return List.of(chunk(10L, 1L, 1, "prev"), chunk(11L, 1L, 2, "hit"), chunk(12L, 1L, 3, "next"));
        });
        assertEquals(1, windows.size());
        assertEquals(1, windows.get(0).startChunkIndex());
        assertEquals(3, windows.get(0).endChunkIndex());
        assertEquals("prev\n\nhit\n\nnext", windows.get(0).content());
    }

    @Test
    void judgeUsesHitsNotExpandedWindow() {
        DocumentChunk hitChunk = chunk(11L, 1L, 2, "命中切片没有标针");
        EvidenceWindow expanded = new EvidenceWindow(11L, 1L, 1, 3, "邻居里才有每个月 22 号凌晨");
        assertNull(RetrievalJudge.rankAt5(List.of(hitChunk), "每个月 22 号凌晨"));
        assertTrue(expanded.content().contains("每个月 22 号凌晨"));
    }

    @Test
    void emptyHitsStayEmpty() {
        assertEquals(List.of(), ClusterWindow.clusterAndExpand(List.of(), (docId, from, to) -> List.of()));
    }

    @Test
    void adjacentMembersUnionSourceToBoth() {
        RankedHit vector = new RankedHit(chunk(10L, 1L, 2, "a"), 0.5, true, false);
        RankedHit keyword = new RankedHit(chunk(11L, 1L, 3, "b"), 1.0, false, true);
        List<Cluster> clusters = ClusterWindow.cluster(List.of(vector, keyword));
        assertEquals(1, clusters.size());
        assertEquals("BOTH", clusters.get(0).source());
    }

    private static RankedHit hit(long id, long documentId, int index, double score) {
        return new RankedHit(chunk(id, documentId, index, "c" + id), score);
    }

    private static DocumentChunk chunk(long id, long documentId, int index, String content) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(id);
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        return chunk;
    }
}
