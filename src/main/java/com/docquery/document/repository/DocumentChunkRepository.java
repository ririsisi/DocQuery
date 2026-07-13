package com.docquery.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.docquery.document.model.DocumentChunk;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    @Modifying
    @Query(value = "UPDATE document_chunks SET embedding = CAST(:vectorStr AS vector) WHERE id = :chunkId",nativeQuery = true)
    void updateEmbeddings(@Param("chunkId") Long chunkId, @Param("vectorStr") String vectorStr);

}
