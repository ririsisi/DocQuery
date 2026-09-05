package com.docquery.document.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.docquery.document.model.DocumentChunk;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    /** Hibernate 绑不了 vector 类型，只能 native CAST。 */
    @Modifying
    @Query(value = "UPDATE document_chunks SET embedding = CAST(:vectorStr AS vector) WHERE id = :chunkId", nativeQuery = true)
    void updateEmbeddings(@Param("chunkId") Long chunkId, @Param("vectorStr") String vectorStr);

    /** <=> 是 pgvector 余弦距离，越小越近。 */
    @Query(value = "SELECT * FROM document_chunks ORDER BY embedding <=> CAST(:vectorStr AS vector) LIMIT :topK", nativeQuery = true)
    List<DocumentChunk> findSimilarByEmbedding(@Param("vectorStr") String vectorStr, @Param("topK") int topK);

    /**
     * 中文无 zhparser，不用 simple 全文（整句一个 token）。pg_trgm 打错字/字段名。
     * 0.12 挡住随机切片；过低会污染 RRF。
     */
    @Query(value = """
            SELECT * FROM document_chunks
            WHERE word_similarity(CAST(:question AS text), content) > 0.12
            ORDER BY word_similarity(CAST(:question AS text), content) DESC
            LIMIT :topK
            """, nativeQuery = true)
    List<DocumentChunk> findSimilarByTrigram(@Param("question") String question, @Param("topK") int topK);

    void deleteByDocumentId(Long documentId);

    List<DocumentChunk> findByDocumentIdAndChunkIndexBetweenOrderByChunkIndexAsc(Long documentId, int fromIndex,
            int toIndex);

}
