package com.docquery.document.service;

import org.springframework.stereotype.Service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

/** 换 Embedding 模型通常要改维度并重建向量列，禁止和旧向量混用。 */
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public float[] embed(String text) {
        Response<Embedding> embedding = embeddingModel.embed(text);
        return embedding.content().vector();
    }
}
