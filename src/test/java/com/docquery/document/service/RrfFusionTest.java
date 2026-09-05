package com.docquery.document.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.docquery.document.model.DocumentChunk;

class RrfFusionTest {

    @Test
    void bothChannelsRankOneBeatsSingleChannel() {
        DocumentChunk onlyVector = chunk(1L);
        DocumentChunk both = chunk(2L);
        DocumentChunk onlyKeyword = chunk(3L);
        List<DocumentChunk> fused = RrfFusion.fuse(List.of(onlyVector, both), List.of(both, onlyKeyword), 3);
        assertEquals(List.of(2L, 1L, 3L), fused.stream().map(DocumentChunk::getId).toList());
    }

    @Test
    void kZeroRankOneIsOne() {
        assertEquals(1.0, RrfFusion.reciprocalRank(1));
        assertEquals(0.5, RrfFusion.reciprocalRank(2));
    }

    @Test
    void emptyChannelsStayEmpty() {
        assertEquals(List.of(), RrfFusion.fuse(List.of(), List.of(), 5));
    }

    @Test
    void bothChannelsMarkBothSource() {
        DocumentChunk onlyVector = chunk(1L);
        DocumentChunk both = chunk(2L);
        DocumentChunk onlyKeyword = chunk(3L);
        List<RrfFusion.RankedHit> ranked = RrfFusion.fuseRanked(List.of(onlyVector, both), List.of(both, onlyKeyword),
                3);
        assertEquals("BOTH", ranked.get(0).source());
        assertEquals("VECTOR", ranked.get(1).source());
        assertEquals("KEYWORD", ranked.get(2).source());
    }

    private static DocumentChunk chunk(long id) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(id);
        chunk.setContent("c" + id);
        return chunk;
    }
}
