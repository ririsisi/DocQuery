package com.docquery.document.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.docquery.document.model.AskMode;
import com.docquery.document.model.AskResult;
import com.docquery.document.model.AskTiming;
import com.docquery.document.model.Citation;
import com.docquery.document.model.DocumentChunk;
import com.docquery.document.model.EvidenceLevel;
import com.docquery.document.model.EvidenceWindow;
import com.docquery.document.model.RetrievalSnapshot;
import com.docquery.document.repository.DocumentChunkRepository;
import com.docquery.document.sse.AskSseSession;
import com.docquery.prompt.QaPromptService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;

/** 问答主链。CHAT 不检索；KB 双路 RRF → 类簇 ±1 → 四级闸门。流式放到工作线程，避免堵住 Tomcat 工作线程。 */
@Service
public class AskService {
    private static final Logger log = LoggerFactory.getLogger(AskService.class);

    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository documentChunkRepository;
    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final QaPromptService qaPromptService;
    private final long heartbeatMs;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "docquery-sse-ask");
        thread.setDaemon(true);
        return thread;
    });

    public AskService(EmbeddingService embeddingService, DocumentChunkRepository documentChunkRepository,
            ChatLanguageModel chatLanguageModel, StreamingChatLanguageModel streamingChatLanguageModel,
            QaPromptService qaPromptService, @Value("${docquery.sse.heartbeat-ms:15000}") long heartbeatMs) {
        this.embeddingService = embeddingService;
        this.documentChunkRepository = documentChunkRepository;
        this.chatLanguageModel = chatLanguageModel;
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        this.qaPromptService = qaPromptService;
        this.heartbeatMs = heartbeatMs;
    }

    public AskResult ask(String question, AskMode mode) {
        String requestId = newRequestId();
        long totalStart = System.nanoTime();
        // 闲聊不当制度依据；空检索拒答会像故障
        if (mode == AskMode.CHAT) {
            long llmStart = System.nanoTime();
            Response<AiMessage> response = chatLanguageModel.generate(
                    SystemMessage.from(qaPromptService.chatSystem()),
                    UserMessage.from(question));
            logTiming(new AskTiming(requestId, mode, 0, 0, AskTiming.millisSince(llmStart),
                    AskTiming.millisSince(totalStart)));
            return new AskResult(response.content().text(), List.of(), null);
        }
        RetrievalSnapshot snapshot = retrieve(question, requestId);
        if (snapshot.level() == EvidenceLevel.NONE) {
            // 只有 NONE 不调生成；WEAK 仍走模型
            logTiming(new AskTiming(requestId, mode, snapshot.embedMs(), snapshot.retrieveMs(), 0,
                    AskTiming.millisSince(totalStart)));
            return new AskResult(qaPromptService.refuseWhenNoEvidence(), snapshot.citations(),
                    EvidenceLevel.NONE.name());
        }
        long llmStart = System.nanoTime();
        String userPrompt = qaPromptService.renderUser(question, snapshot.evidence(),
                qaPromptService.levelLabel(snapshot.level()), qaPromptService.guidance(snapshot.level()));
        Response<AiMessage> response = chatLanguageModel.generate(
                SystemMessage.from(qaPromptService.system()),
                UserMessage.from(userPrompt));
        logTiming(new AskTiming(requestId, mode, snapshot.embedMs(), snapshot.retrieveMs(),
                AskTiming.millisSince(llmStart), AskTiming.millisSince(totalStart)));
        return new AskResult(response.content().text(), snapshot.citations(), snapshot.level().name());
    }

    public void askStream(String question, AskMode mode, SseEmitter emitter) {
        AskSseSession session = new AskSseSession(emitter, heartbeatMs);
        streamExecutor.execute(() -> streamOnWorker(question, mode, session));
    }

    public RetrievalSnapshot retrieve(String question) {
        return retrieve(question, newRequestId());
    }

    public RetrievalSnapshot retrieve(String question, String requestId) {
        long embedStart = System.nanoTime();
        float[] vector = embeddingService.embed(question);
        long embedMs = AskTiming.millisSince(embedStart);
        String vectorStr = toVectorString(vector);
        long retrieveStart = System.nanoTime();
        // 各路先多取再融合，避免只在 Top5 里做 RRF
        List<DocumentChunk> vectorHits = documentChunkRepository.findSimilarByEmbedding(vectorStr,
                RrfFusion.CANDIDATE_K);
        List<DocumentChunk> keywordHits = question == null || question.isBlank()
                ? List.of()
                : documentChunkRepository.findSimilarByTrigram(question, RrfFusion.CANDIDATE_K);
        List<RrfFusion.RankedHit> ranked = RrfFusion.fuseRanked(vectorHits, keywordHits, RrfFusion.FINAL_K);
        List<DocumentChunk> hits = ranked.stream().map(RrfFusion.RankedHit::chunk).collect(Collectors.toList());
        List<EvidenceWindow> evidence = ClusterWindow.clusterAndExpand(ranked,
                documentChunkRepository::findByDocumentIdAndChunkIndexBetweenOrderByChunkIndexAsc);
        EvidenceLevel level = EvidenceGate.evaluate(evidence);
        long retrieveMs = AskTiming.millisSince(retrieveStart);
        log.info("retrieve.rrf requestId={} vector={} keyword={} fused={}", requestId, vectorHits.size(),
                keywordHits.size(), hits.size());
        log.info("retrieve.cluster requestId={} hits={} clusters={}", requestId, hits.size(), evidence.size());
        log.info("retrieve.gate requestId={} level={} clusters={}", requestId, level, evidence.size());
        List<Citation> citations = evidence.stream()
                .map(window -> new Citation(window.primaryChunkId(), window.content()))
                .collect(Collectors.toList());
        return new RetrievalSnapshot(requestId, hits, evidence, level, citations, embedMs, retrieveMs);
    }

    private void streamOnWorker(String question, AskMode mode, AskSseSession session) {
        String requestId = newRequestId();
        long totalStart = System.nanoTime();
        try {
            if (mode == AskMode.CHAT) {
                session.send("citations", List.of());
                streamGenerate(qaPromptService.chatSystem(), question, session, requestId, mode, 0, 0, totalStart);
                return;
            }
            RetrievalSnapshot snapshot = retrieve(question, requestId);
            session.send("evidence", snapshot.level().name());
            session.send("citations", snapshot.citations());
            if (snapshot.level() == EvidenceLevel.NONE) {
                session.send("token", qaPromptService.refuseWhenNoEvidence());
                logTiming(new AskTiming(requestId, mode, snapshot.embedMs(), snapshot.retrieveMs(), 0,
                        AskTiming.millisSince(totalStart)));
                session.complete();
                return;
            }
            String userPrompt = qaPromptService.renderUser(question, snapshot.evidence(),
                    qaPromptService.levelLabel(snapshot.level()), qaPromptService.guidance(snapshot.level()));
            streamGenerate(qaPromptService.system(), userPrompt, session, requestId, mode, snapshot.embedMs(),
                    snapshot.retrieveMs(), totalStart);
        } catch (Exception e) {
            session.fail(e.getMessage() != null ? e.getMessage() : "问答失败");
        }
    }

    private void streamGenerate(String system, String user, AskSseSession session, String requestId, AskMode mode,
            long embedMs, long retrieveMs, long totalStart) {
        long llmStart = System.nanoTime();
        streamingChatLanguageModel.generate(
                List.of(SystemMessage.from(system), UserMessage.from(user)),
                new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        session.send("token", token);
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        logTiming(new AskTiming(requestId, mode, embedMs, retrieveMs, AskTiming.millisSince(llmStart),
                                AskTiming.millisSince(totalStart)));
                        session.complete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        logTiming(new AskTiming(requestId, mode, embedMs, retrieveMs, AskTiming.millisSince(llmStart),
                                AskTiming.millisSince(totalStart)));
                        session.fail(error.getMessage() != null ? error.getMessage() : "生成失败");
                    }
                });
    }

    private void logTiming(AskTiming timing) {
        log.info("ask.timing requestId={} mode={} embedMs={} retrieveMs={} llmMs={} totalMs={}",
                timing.requestId(), timing.mode(), timing.embedMs(), timing.retrieveMs(), timing.llmMs(),
                timing.totalMs());
    }

    private String newRequestId() {
        return UUID.randomUUID().toString();
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
}
