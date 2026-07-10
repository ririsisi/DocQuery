package com.docquery.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docquery.document.model.DocumentChunk;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

}
