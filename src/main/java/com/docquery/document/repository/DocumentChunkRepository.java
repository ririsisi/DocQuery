package com.docquery.document.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.docquery.document.model.DocumentChunk;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    @Modifying
    @Query(value = "UPDATE document_chunks SET embedding = CAST(:vectorStr AS vector) WHERE id = :chunkId", nativeQuery = true)
    void updateEmbeddings(@Param("chunkId") Long chunkId, @Param("vectorStr") String vectorStr);

    /**
     * 余弦相似度检索 Top-K
     * 
     * @param vectorStr "[0.1,0.2,...]" 格式的向量字符串
     * @param topK      返回条数
     */
    @Query(value = "SELECT * FROM document_chunks ORDER BY embedding <=> CAST(:vectorStr AS vector) LIMIT :topK", nativeQuery = true)
    List<DocumentChunk> findSimilarByEmbedding(@Param("vectorStr") String vectorStr, @Param("topK") int topK);

}
