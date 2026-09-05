package com.docquery.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.docquery.document.model.DocumentChunk;

class RetrievalJudgeTest {

    @Test
    void hitsWhenTopChunkContainsNeedle() {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setContent("每个月 22 号凌晨生成当月的第三方工时核算单。");
        assertTrue(RetrievalJudge.hitAt5(List.of(chunk), "每个月 22 号凌晨"));
    }

    @Test
    void missesWhenNeedleAbsent() {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setContent("服务商审核岗可见待审列表。");
        assertFalse(RetrievalJudge.hitAt5(List.of(chunk), "每个月 22 号凌晨"));
    }

    @Test
    void blankNeedleNeverHits() {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setContent("任何正文");
        assertFalse(RetrievalJudge.hitAt5(List.of(chunk), "  "));
    }

    @Test
    void rankIsOneBasedAndHitAt1UsesFirstSlot() {
        DocumentChunk first = new DocumentChunk();
        first.setContent("干扰：每个月 1 号凌晨。");
        DocumentChunk second = new DocumentChunk();
        second.setContent("每个月 22 号凌晨生成当月的第三方工时核算单。");
        List<DocumentChunk> chunks = List.of(first, second);
        assertEquals(2, RetrievalJudge.rankAt5(chunks, "每个月 22 号凌晨"));
        assertTrue(RetrievalJudge.hitAt5(chunks, "每个月 22 号凌晨"));
        assertFalse(RetrievalJudge.hitAt1(chunks, "每个月 22 号凌晨"));
        assertTrue(RetrievalJudge.hitAt1(List.of(second, first), "每个月 22 号凌晨"));
        assertNull(RetrievalJudge.rankAt5(chunks, "不存在的标针"));
    }
}
