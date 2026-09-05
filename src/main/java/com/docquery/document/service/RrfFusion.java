package com.docquery.document.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.docquery.document.model.DocumentChunk;

/**
 * 双路只按排名融合，不用原始分（向量和关键词数量级不同）。
 * k=0 拉开头部，给闸门 0.85 阈值配套。
 */
public final class RrfFusion {

    public static final int RRF_K = 0;
    public static final int CANDIDATE_K = 10;
    public static final int FINAL_K = 5;

    private RrfFusion() {
    }

    public static List<DocumentChunk> fuse(List<DocumentChunk> vectorHits, List<DocumentChunk> keywordHits, int topK) {
        return fuseRanked(vectorHits, keywordHits, topK).stream().map(RankedHit::chunk).toList();
    }

    public static List<RankedHit> fuseRanked(List<DocumentChunk> vectorHits, List<DocumentChunk> keywordHits, int topK) {
        Map<Long, Acc> scores = new LinkedHashMap<>();
        addChannel(scores, vectorHits, true);
        addChannel(scores, keywordHits, false);
        List<Acc> ranked = new ArrayList<>(scores.values());
        ranked.sort(Comparator.comparingDouble(Acc::score).reversed().thenComparing(a -> a.chunk().getId()));
        int limit = Math.max(0, topK);
        List<RankedHit> out = new ArrayList<>();
        for (int i = 0; i < ranked.size() && i < limit; i++) {
            Acc acc = ranked.get(i);
            out.add(new RankedHit(acc.chunk(), acc.score(), acc.vectorMatched(), acc.keywordMatched()));
        }
        return out;
    }

    public record RankedHit(DocumentChunk chunk, double rrfScore, boolean vectorMatched, boolean keywordMatched) {
        public RankedHit(DocumentChunk chunk, double rrfScore) {
            this(chunk, rrfScore, false, false);
        }

        public String source() {
            if (vectorMatched && keywordMatched) {
                return "BOTH";
            }
            return vectorMatched ? "VECTOR" : "KEYWORD";
        }
    }

    /** k=0 时必须挡住 rank=0，否则除零。排名按 1 起。 */
    public static double reciprocalRank(int rank) {
        return 1.0 / (RRF_K + Math.max(rank, 1));
    }

    private static void addChannel(Map<Long, Acc> scores, List<DocumentChunk> hits, boolean vectorChannel) {
        if (hits == null) {
            return;
        }
        for (int i = 0; i < hits.size(); i++) {
            DocumentChunk chunk = hits.get(i);
            if (chunk == null || chunk.getId() == null) {
                continue;
            }
            double add = reciprocalRank(i + 1);
            scores.compute(chunk.getId(), (id, acc) -> {
                if (acc == null) {
                    return new Acc(chunk, add, vectorChannel, !vectorChannel);
                }
                return new Acc(acc.chunk(), acc.score() + add, acc.vectorMatched() || vectorChannel,
                        acc.keywordMatched() || !vectorChannel);
            });
        }
    }

    private record Acc(DocumentChunk chunk, double score, boolean vectorMatched, boolean keywordMatched) {
    }
}
