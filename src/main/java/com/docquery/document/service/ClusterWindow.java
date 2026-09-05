package com.docquery.document.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.docquery.document.model.DocumentChunk;
import com.docquery.document.model.EvidenceWindow;
import com.docquery.document.service.RrfFusion.RankedHit;

/**
 * RRF TopK 之后：同文档严格 +1 并成一簇，再对簇首尾整段 ±1。
 * 中间空一档不并——没命中的切片不当证据。
 */
public final class ClusterWindow {

    public static final int NEIGHBOR = 1;

    private ClusterWindow() {
    }

    public static List<EvidenceWindow> clusterAndExpand(List<RankedHit> hits, NeighborLoader loader) {
        return expand(cluster(hits), loader);
    }

    public static List<Cluster> cluster(List<RankedHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        Map<Long, List<RankedHit>> byDoc = new LinkedHashMap<>();
        List<Cluster> isolated = new ArrayList<>();
        for (RankedHit hit : hits) {
            if (hit == null || hit.chunk() == null) {
                continue;
            }
            Long documentId = hit.chunk().getDocumentId();
            if (documentId == null) {
                isolated.add(fromMembers(List.of(hit)));
                continue;
            }
            byDoc.computeIfAbsent(documentId, id -> new ArrayList<>()).add(hit);
        }
        List<Cluster> clusters = new ArrayList<>(isolated);
        for (List<RankedHit> docHits : byDoc.values()) {
            docHits.sort(Comparator.comparingInt((RankedHit h) -> h.chunk().getChunkIndex())
                    .thenComparing(h -> h.chunk().getId(), Comparator.nullsLast(Long::compareTo)));
            List<RankedHit> run = new ArrayList<>();
            for (RankedHit hit : docHits) {
                if (!run.isEmpty() && hit.chunk().getChunkIndex() != lastIndex(run) + 1) {
                    clusters.add(fromMembers(run));
                    run = new ArrayList<>();
                }
                run.add(hit);
            }
            if (!run.isEmpty()) {
                clusters.add(fromMembers(run));
            }
        }
        clusters.sort(Comparator.comparingDouble(Cluster::score).reversed()
                .thenComparing(c -> c.primaryChunkId(), Comparator.nullsLast(Long::compareTo)));
        return clusters;
    }

    public static List<EvidenceWindow> expand(List<Cluster> clusters, NeighborLoader loader) {
        if (clusters == null || clusters.isEmpty()) {
            return List.of();
        }
        List<EvidenceWindow> out = new ArrayList<>();
        for (Cluster cluster : clusters) {
            out.add(expandOne(cluster, loader));
        }
        return out;
    }

    private static EvidenceWindow expandOne(Cluster cluster, NeighborLoader loader) {
        int from = Math.max(0, cluster.startIndex() - NEIGHBOR);
        int to = cluster.endIndex() + NEIGHBOR;
        List<DocumentChunk> window = List.of();
        if (cluster.documentId() != null && loader != null) {
            List<DocumentChunk> loaded = loader.load(cluster.documentId(), from, to);
            if (loaded != null) {
                window = loaded;
            }
        }
        TreeMap<Integer, DocumentChunk> byIndex = new TreeMap<>();
        for (DocumentChunk member : cluster.members()) {
            byIndex.putIfAbsent(member.getChunkIndex(), member);
        }
        for (DocumentChunk chunk : window) {
            if (chunk != null) {
                byIndex.putIfAbsent(chunk.getChunkIndex(), chunk);
            }
        }
        String content = byIndex.values().stream().map(DocumentChunk::getContent).filter(Objects::nonNull)
                .reduce((a, b) -> a + "\n\n" + b).orElse("");
        int start = byIndex.isEmpty() ? cluster.startIndex() : byIndex.firstKey();
        int end = byIndex.isEmpty() ? cluster.endIndex() : byIndex.lastKey();
        return new EvidenceWindow(cluster.primaryChunkId(), cluster.documentId(), start, end, content,
                cluster.source(), cluster.score(), EvidenceGate.normalize(cluster.score()));
    }

    private static int lastIndex(List<RankedHit> run) {
        return run.get(run.size() - 1).chunk().getChunkIndex();
    }

    private static Cluster fromMembers(List<RankedHit> members) {
        List<DocumentChunk> chunks = new ArrayList<>();
        double score = Double.NEGATIVE_INFINITY;
        Long primaryId = null;
        boolean hasVector = false;
        boolean hasKeyword = false;
        for (RankedHit hit : members) {
            chunks.add(hit.chunk());
            hasVector = hasVector || hit.vectorMatched();
            hasKeyword = hasKeyword || hit.keywordMatched();
            if (hit.rrfScore() > score) {
                score = hit.rrfScore();
                primaryId = hit.chunk().getId();
            }
        }
        String source = hasVector && hasKeyword ? "BOTH" : hasVector ? "VECTOR" : "KEYWORD";
        DocumentChunk first = chunks.get(0);
        DocumentChunk last = chunks.get(chunks.size() - 1);
        return new Cluster(first.getDocumentId(), first.getChunkIndex(), last.getChunkIndex(), score, primaryId, source,
                List.copyOf(chunks));
    }

    public record Cluster(Long documentId, int startIndex, int endIndex, double score, Long primaryChunkId,
            String source, List<DocumentChunk> members) {
    }

    @FunctionalInterface
    public interface NeighborLoader {
        List<DocumentChunk> load(Long documentId, int fromIndex, int toIndex);
    }
}
