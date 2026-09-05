package com.docquery.document.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.docquery.document.model.EvidenceLevel;
import com.docquery.document.model.EvidenceWindow;

class EvidenceGateTest {

    @Test
    void emptyIsNone() {
        assertEquals(EvidenceLevel.NONE, EvidenceGate.evaluate(List.of()));
    }

    @Test
    void singleVectorIsWeak() {
        assertEquals(EvidenceLevel.WEAK, EvidenceGate.evaluate(List.of(window("VECTOR", 0.63))));
    }

    @Test
    void singleBothIsPartialNotWeak() {
        assertEquals(EvidenceLevel.PARTIAL, EvidenceGate.evaluate(List.of(window("BOTH", 0.86))));
    }

    @Test
    void twoVectorBelowThresholdIsPartial() {
        assertEquals(EvidenceLevel.PARTIAL,
                EvidenceGate.evaluate(List.of(window("VECTOR", 0.63), window("KEYWORD", 0.50))));
    }

    @Test
    void twoWithBothIsSufficient() {
        assertEquals(EvidenceLevel.SUFFICIENT,
                EvidenceGate.evaluate(List.of(window("BOTH", 0.63), window("KEYWORD", 0.40))));
    }

    @Test
    void twoVectorHighScoreIsSufficient() {
        assertEquals(EvidenceLevel.SUFFICIENT,
                EvidenceGate.evaluate(List.of(window("VECTOR", 0.86), window("VECTOR", 0.50))));
    }

    @Test
    void normalizeMatchesK0Anchors() {
        assertEquals(0.6321, EvidenceGate.normalize(1.0), 0.001);
        assertEquals(0.8647, EvidenceGate.normalize(2.0), 0.001);
        assertTrue(EvidenceGate.normalize(2.0) >= EvidenceGate.SUFFICIENT_SCORE);
        assertTrue(EvidenceGate.normalize(1.0) < EvidenceGate.SUFFICIENT_SCORE);
    }

    private static EvidenceWindow window(String source, double score) {
        return new EvidenceWindow(1L, 1L, 0, 0, "t", source, 0, score);
    }
}
