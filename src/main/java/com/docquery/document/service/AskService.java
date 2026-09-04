package com.docquery.document.service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.docquery.document.model.AskMode;
import com.docquery.document.model.AskResult;
import com.docquery.document.model.Citation;
import com.docquery.document.model.DocumentChunk;
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

@Service
public class AskService {
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
        if (mode == AskMode.CHAT) {
            Response<AiMessage> response = chatLanguageModel.generate(
                    SystemMessage.from(qaPromptService.chatSystem()),
                    UserMessage.from(question));
            return new AskResult(response.content().text(), List.of());
        }
        PreparedAsk prepared = prepare(question);
        if (prepared.chunks().isEmpty()) {
            return new AskResult(qaPromptService.refuseWhenNoEvidence(), prepared.citations());
        }
        Response<AiMessage> response = chatLanguageModel.generate(
                SystemMessage.from(qaPromptService.system()),
                UserMessage.from(prepared.userPrompt()));
        return new AskResult(response.content().text(), prepared.citations());
    }

    public void askStream(String question, AskMode mode, SseEmitter emitter) {
        AskSseSession session = new AskSseSession(emitter, heartbeatMs);
        streamExecutor.execute(() -> streamOnWorker(question, mode, session));
    }

    private void streamOnWorker(String question, AskMode mode, AskSseSession session) {
        try {
            if (mode == AskMode.CHAT) {
                session.send("citations", List.of());
                streamGenerate(qaPromptService.chatSystem(), question, session);
                return;
            }
            PreparedAsk prepared = prepare(question);
            session.send("citations", prepared.citations());
            if (prepared.chunks().isEmpty()) {
                session.send("token", qaPromptService.refuseWhenNoEvidence());
                session.complete();
                return;
            }
            streamGenerate(qaPromptService.system(), prepared.userPrompt(), session);
        } catch (Exception e) {
            session.fail(e.getMessage() != null ? e.getMessage() : "问答失败");
        }
    }

    private void streamGenerate(String system, String user, AskSseSession session) {
        streamingChatLanguageModel.generate(
                List.of(SystemMessage.from(system), UserMessage.from(user)),
                new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        session.send("token", token);
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        session.complete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        session.fail(error.getMessage() != null ? error.getMessage() : "生成失败");
                    }
                });
    }

    private PreparedAsk prepare(String question) {
        float[] vector = embeddingService.embed(question);
        String vectorStr = toVectorString(vector);
        List<DocumentChunk> chunks = documentChunkRepository.findSimilarByEmbedding(vectorStr, 5);
        List<Citation> citations = chunks.stream()
                .map(chunk -> new Citation(chunk.getId(), chunk.getContent()))
                .collect(Collectors.toList());
        String userPrompt = chunks.isEmpty() ? ""
                : qaPromptService.renderUser(question, chunks, QaPromptService.LEVEL_UNGATED,
                        qaPromptService.ungatedGuidance());
        return new PreparedAsk(chunks, citations, userPrompt);
    }

    private record PreparedAsk(List<DocumentChunk> chunks, List<Citation> citations, String userPrompt) {
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
