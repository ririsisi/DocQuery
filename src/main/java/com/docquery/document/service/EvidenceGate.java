package com.docquery.document.service;

import java.util.List;

import com.docquery.document.model.EvidenceLevel;
import com.docquery.document.model.EvidenceWindow;

/**
 * 类簇之后、生成之前。条数 / BOTH / 归一化 topScore。
 * 指导语由 Prompt 文件提供，这里只出等级。
 */
public final class EvidenceGate {

    public static final double SUFFICIENT_SCORE = 0.85;

    private EvidenceGate() {
    }

    /** k=0 时单路 rank1≈0.63，双路 rank1+rank1≈0.86。 */
    public static double normalize(double rawRrf) {
        return 1.0 - Math.exp(-Math.max(rawRrf, 0));
    }

    public static EvidenceLevel evaluate(List<EvidenceWindow> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return EvidenceLevel.NONE;
        }
        boolean hasBoth = false;
        boolean hasVector = false;
        double topScore = 0;
        for (EvidenceWindow window : evidence) {
            if (window == null) {
                continue;
            }
            String source = window.source();
            if ("BOTH".equals(source)) {
                hasBoth = true;
                hasVector = true;
            } else if ("VECTOR".equals(source)) {
                hasVector = true;
            }
            topScore = Math.max(topScore, window.score());
        }
        int size = (int) evidence.stream().filter(w -> w != null).count();
        if (size == 0) {
            return EvidenceLevel.NONE;
        }
        if (size >= 2 && (hasBoth || (hasVector && topScore >= SUFFICIENT_SCORE))) {
            return EvidenceLevel.SUFFICIENT;
        }
        if (hasBoth || size >= 2) {
            return EvidenceLevel.PARTIAL;
        }
        return EvidenceLevel.WEAK;
    }
}
