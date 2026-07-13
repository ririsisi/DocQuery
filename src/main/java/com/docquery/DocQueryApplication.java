package com.docquery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import dev.langchain4j.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;

@SpringBootApplication
public class DocQueryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocQueryApplication.class, args);
    }

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${langchain4j.dashscope.api-key}") String apiKey,
            @Value("${langchain4j.dashscope.embedding-model.model-name:text-embedding-v3}") String modelName) {
        return QwenEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }
}
