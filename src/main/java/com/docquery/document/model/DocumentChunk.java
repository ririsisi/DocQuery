package com.docquery.document.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "document_chunks")
@Getter
@Setter
public class DocumentChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    @Column(name = "document_id")
    private Long documentId;
    @Column(name = "chunk_index")
    private int chunkIndex;
    @Column(name = "content")
    private String content;
    @Column(name = "token_count")
    private int tokenCount;
    @Column(name = "embedding")
    private String embedding;
    @Column(name = "created_at")
    @CreationTimestamp
    private LocalDateTime createdAt;
}
