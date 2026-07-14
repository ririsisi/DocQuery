package com.docquery.document.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.docquery.document.model.AskResult;
import com.docquery.document.model.Citation;
import com.docquery.document.model.DocumentChunk;
import com.docquery.document.repository.DocumentChunkRepository;

import dev.langchain4j.model.chat.ChatLanguageModel;

@Service
public class AskService {
    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository documentChunkRepository;
    private final ChatLanguageModel chatLanguageModel;

    public AskService(EmbeddingService embeddingService, DocumentChunkRepository documentChunkRepository,
            ChatLanguageModel chatLanguageModel) {
        this.embeddingService = embeddingService;
        this.documentChunkRepository = documentChunkRepository;
        this.chatLanguageModel = chatLanguageModel;
    }

    /**
     * 问答
     * 
     * @param question
     * @return
     */
    public AskResult ask(String question) {
        // 先将用户问题转换为向量
        float[] vector = embeddingService.embed(question);
        String vectorStr = toVectorString(vector);
        // 问题转换为向量后，在数据库中检索相似的文档片段
        List<DocumentChunk> documentChunks = documentChunkRepository.findSimilarByEmbedding(vectorStr, 5);
        // 将文档片段拼接成一个字符串
        String documentChunksContent = documentChunks.stream().map(DocumentChunk::getContent)
                .collect(Collectors.joining("\n"));
        String response = chatLanguageModel.generate(documentChunksContent + "\n" + question);
        List<Citation> citations = documentChunks.stream().map(chunk -> new Citation(chunk.getId(), chunk.getContent()))
                .collect(Collectors.toList());
        return new AskResult(response, citations);
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
