-- 关键词通道：pg_trgm，中文无分词时仍能靠三元组抓错字和字段名
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_chunks_content_trgm
    ON document_chunks
    USING gin (content gin_trgm_ops);
