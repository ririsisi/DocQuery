package com.docquery.document.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.docquery.document.model.AskResult;
import com.docquery.document.model.Citation;
import com.docquery.document.model.DocumentChunk;
import com.docquery.document.repository.DocumentChunkRepository;
import com.docquery.prompt.QaPromptService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;

@Service
public class AskService {
    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository documentChunkRepository;
    private final ChatLanguageModel chatLanguageModel;
    private final QaPromptService qaPromptService;

    public AskService(EmbeddingService embeddingService, DocumentChunkRepository documentChunkRepository,
            ChatLanguageModel chatLanguageModel, QaPromptService qaPromptService) {
        this.embeddingService = embeddingService;
        this.documentChunkRepository = documentChunkRepository;
        this.chatLanguageModel = chatLanguageModel;
        this.qaPromptService = qaPromptService;
    }

    /**
     * 问答
     * 
     * @param question
     * @return
     */
    public AskResult ask(String question) {
        float[] vector = embeddingService.embed(question);
        String vectorStr = toVectorString(vector);
        List<DocumentChunk> documentChunks = documentChunkRepository.findSimilarByEmbedding(vectorStr, 5);

        List<Citation> citations = documentChunks.stream()
                .map(chunk -> new Citation(chunk.getId(), chunk.getContent()))
                .collect(Collectors.toList());

        if (documentChunks.isEmpty()) {
            return new AskResult(qaPromptService.refuseWhenNoEvidence(), citations);
        }

        String user = qaPromptService.renderUser(question, documentChunks, QaPromptService.LEVEL_UNGATED,
                qaPromptService.ungatedGuidance());
        Response<AiMessage> response = chatLanguageModel.generate(
                SystemMessage.from(qaPromptService.system()),
                UserMessage.from(user));
        return new AskResult(response.content().text(), citations);
    }

    /**
     * 将向量转换为字符串
     * 
     * @param vector
     * @return
     */
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
