-- ============================================================
-- DocQuery V1 — 初始建表
-- 文档表 + 分块表（pgvector 向量存储）
-- ============================================================

-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 文档表
CREATE TABLE IF NOT EXISTS documents (
    id              BIGSERIAL       PRIMARY KEY,
    file_name       VARCHAR(512)    NOT NULL,
    file_type       VARCHAR(64)     NOT NULL,
    file_size       BIGINT          NOT NULL,
    content_text    TEXT            NOT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT now()
);

COMMENT ON TABLE  documents              IS '上传的原始文档';
COMMENT ON COLUMN documents.status       IS 'PENDING | PROCESSING | COMPLETED | FAILED';
COMMENT ON COLUMN documents.content_text IS '解析后的纯文本内容';

-- 分块表（含向量）
CREATE TABLE IF NOT EXISTS document_chunks (
    id              BIGSERIAL       PRIMARY KEY,
    document_id     BIGINT          NOT NULL REFERENCES documents(id),
    chunk_index     INT             NOT NULL,
    content         TEXT            NOT NULL,
    token_count     INT             NOT NULL DEFAULT 0,
    embedding       vector(1024),
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_chunk_document FOREIGN KEY (document_id) REFERENCES documents(id)
);

COMMENT ON TABLE  document_chunks             IS '文档分块 + 向量存储';
COMMENT ON COLUMN document_chunks.embedding   IS 'text-embedding-v3 输出 1024 维向量';

-- 检索索引（HNSW + Cosine 距离）
CREATE INDEX IF NOT EXISTS idx_chunks_embedding
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);

-- 按文档 ID 查分块
CREATE INDEX IF NOT EXISTS idx_chunks_document_id
    ON document_chunks (document_id, chunk_index);
