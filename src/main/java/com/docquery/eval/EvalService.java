package com.docquery.eval;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docquery.document.model.Document;
import com.docquery.document.model.DocumentChunk;
import com.docquery.document.model.DocumentStatus;
import com.docquery.document.model.EvidenceLevel;
import com.docquery.document.model.RetrievalSnapshot;
import com.docquery.document.repository.DocumentChunkRepository;
import com.docquery.document.repository.DocumentRepository;
import com.docquery.document.service.AskService;
import com.docquery.document.service.ChunkService;
import com.docquery.document.service.EmbeddingService;
import com.docquery.document.service.MdParser;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 黄金集只评检索。正例按题型分桶，负例只看空不空，避免加成一个准确率。 */
@Service
public class EvalService {
    private static final Logger log = LoggerFactory.getLogger(EvalService.class);

    private final AskService askService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final MdParser mdParser;
    private final ObjectMapper objectMapper;
    private final Path runsDir;
    private final String retrieverLabel;

    public EvalService(AskService askService, DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository, ChunkService chunkService,
            EmbeddingService embeddingService, MdParser mdParser, ObjectMapper objectMapper,
            @Value("${docquery.eval.runs-dir:eval-runs}") String runsDir,
            @Value("${docquery.eval.retriever:vector+pg_trgm+rrf-k0+cluster-win1+gate}") String retrieverLabel) {
        this.askService = askService;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.chunkService = chunkService;
        this.embeddingService = embeddingService;
        this.mdParser = mdParser;
        this.objectMapper = objectMapper;
        this.runsDir = Path.of(runsDir);
        this.retrieverLabel = retrieverLabel;
    }

    public GoldensetFile loadGoldenset() {
        try (InputStream in = getClass().getResourceAsStream("/eval/goldenset.json")) {
            if (in == null) {
                throw new IllegalStateException("classpath:/eval/goldenset.json 不存在");
            }
            return objectMapper.readValue(in, GoldensetFile.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional
    public SeedResult seedCorpus(boolean replace) {
        int created = 0;
        int skipped = 0;
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:/eval/corpus/*.md");
            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                if (fileName == null) {
                    continue;
                }
                var existing = documentRepository.findFirstByFileName(fileName);
                if (existing.isPresent()) {
                    if (!replace) {
                        skipped++;
                        continue;
                    }
                    documentChunkRepository.deleteByDocumentId(existing.get().getId());
                    documentRepository.delete(existing.get());
                }
                String text;
                try (InputStream in = resource.getInputStream()) {
                    text = mdParser.parse(in);
                }
                Document document = Document.pendingUpload(fileName, "text/markdown", (long) text.length());
                document.setContentText(text);
                document.setStatus(DocumentStatus.COMPLETED);
                Document saved = documentRepository.save(document);
                int index = 0;
                for (String chunk : chunkService.chunk(text)) {
                    DocumentChunk documentChunk = new DocumentChunk();
                    documentChunk.setDocumentId(saved.getId());
                    documentChunk.setChunkIndex(index++);
                    documentChunk.setContent(chunk);
                    documentChunk.setCreatedAt(LocalDateTime.now());
                    Long chunkId = documentChunkRepository.save(documentChunk).getId();
                    float[] embedding = embeddingService.embed(chunk);
                    documentChunkRepository.updateEmbeddings(chunkId, toVectorString(embedding));
                }
                created++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new SeedResult(created, skipped);
    }

    public EvalReport runGoldenset() {
        GoldensetFile file = loadGoldenset();
        List<EvalItemResult> results = new ArrayList<>();
        int scored = 0;
        int hits = 0;
        int hitsAt1 = 0;
        double scoredRecip = 0;
        int hardScored = 0;
        int hardHits = 0;
        int hardHitsAt1 = 0;
        double hardRecip = 0;
        int noisyScored = 0;
        int noisyHits = 0;
        int noisyHitsAt1 = 0;
        double noisyRecip = 0;
        int negatives = 0;
        int emptyNegatives = 0;
        int noneNegatives = 0;
        int adversarial = 0;
        int emptyAdversarial = 0;
        int noneAdversarial = 0;
        for (GoldensetItem item : file.items()) {
            RetrievalSnapshot snapshot = askService.retrieve(item.question());
            boolean empty = snapshot.hits().isEmpty();
            EvidenceLevel level = snapshot.level();
            boolean refused = level == EvidenceLevel.NONE;
            Integer rank = RetrievalJudge.rankAt5(snapshot.hits(), item.expectedHitContains());
            boolean hit = rank != null;
            boolean hit1 = Integer.valueOf(1).equals(rank);
            double recip = rank == null ? 0 : 1.0 / rank;
            if (item.expectRefuse()) {
                // 负例不进 Hit@k。拒答=NONE；WEAK 仍会生成
                if ("adversarial".equals(item.type())) {
                    adversarial++;
                    if (empty) {
                        emptyAdversarial++;
                    }
                    if (refused) {
                        noneAdversarial++;
                    }
                } else {
                    negatives++;
                    if (empty) {
                        emptyNegatives++;
                    }
                    if (refused) {
                        noneNegatives++;
                    }
                }
            } else if ("hard".equals(item.type())) {
                hardScored++;
                if (hit) {
                    hardHits++;
                }
                if (hit1) {
                    hardHitsAt1++;
                }
                hardRecip += recip;
            } else if ("noisy".equals(item.type())) {
                noisyScored++;
                if (hit) {
                    noisyHits++;
                }
                if (hit1) {
                    noisyHitsAt1++;
                }
                noisyRecip += recip;
            } else {
                scored++;
                if (hit) {
                    hits++;
                }
                if (hit1) {
                    hitsAt1++;
                }
                scoredRecip += recip;
            }
            results.add(new EvalItemResult(item.id(), item.type(), item.question(), item.expectRefuse(), empty, refused,
                    level == null ? null : level.name(), hit, hit1, rank, snapshot.embedMs(), snapshot.retrieveMs(),
                    snapshot.requestId()));
            log.info(
                    "eval.item id={} type={} level={} hitAt5={} hitAt1={} rank={} empty={} refuseNone={} embedMs={} retrieveMs={} requestId={}",
                    item.id(), item.type(), level, hit, hit1, rank, empty, refused, snapshot.embedMs(),
                    snapshot.retrieveMs(), snapshot.requestId());
        }
        EvalReport report = new EvalReport(file.version(), file.items().size(), scored, hits, hitsAt1,
                mrr(scored, scoredRecip), hardScored, hardHits, hardHitsAt1, mrr(hardScored, hardRecip), noisyScored,
                noisyHits, noisyHitsAt1, mrr(noisyScored, noisyRecip), negatives, emptyNegatives, noneNegatives,
                adversarial, emptyAdversarial, noneAdversarial,
                "未写入简历；回归/难例/错字分开报 Hit@5 与 Hit@1；拒答只算 NONE，WEAK 仍生成", results);
        persistRun(report);
        return report;
    }

    /** 落盘是为了和基线对比，不靠翻控制台。目录 git 忽略；失败不挡接口返回。 */
    private void persistRun(EvalReport report) {
        try {
            Files.createDirectories(runsDir);
            Instant now = Instant.now();
            EvalRunFile run = new EvalRunFile(now.toString(), retrieverLabel, report);
            String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault()).format(now);
            Path dated = runsDir.resolve(stamp + "-" + report.goldensetVersion() + ".json");
            Path latest = runsDir.resolve("latest.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dated.toFile(), run);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(latest.toFile(), run);
            log.info("eval.run saved path={} retriever={} hitsAt1={} noisyHits={} G56 见 items", dated.toAbsolutePath(),
                    retrieverLabel, report.hitsAt1(), report.noisyHits());
        } catch (IOException e) {
            log.warn("eval.run 落盘失败（评测结果仍在接口返回里）: {}", e.getMessage());
        }
    }

    private static double mrr(int count, double reciprocalSum) {
        if (count <= 0) {
            return 0;
        }
        return Math.round(reciprocalSum / count * 10000.0) / 10000.0;
    }

    private String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    public record SeedResult(int created, int skipped) {
    }

    public record EvalItemResult(String id, String type, String question, boolean expectRefuse, boolean emptyRetrieval,
            boolean refuseNone, String evidenceLevel, boolean hitAt5, boolean hitAt1, Integer rank, long embedMs,
            long retrieveMs, String requestId) {
    }

    public record EvalRunFile(String ranAt, String retriever, EvalReport report) {
    }

    public record EvalReport(String goldensetVersion, int total, int scored, int hits, int hitsAt1, double mrr,
            int hardScored, int hardHits, int hardHitsAt1, double hardMrr, int noisyScored, int noisyHits,
            int noisyHitsAt1, double noisyMrr, int negatives, int emptyNegatives, int noneNegatives, int adversarial,
            int emptyAdversarial, int noneAdversarial, String disclaimer, List<EvalItemResult> items) {
    }
}
