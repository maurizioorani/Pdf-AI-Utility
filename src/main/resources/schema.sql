-- Database schema for DocumentChunk entity supporting RAG functionality
-- This script creates the necessary tables for storing document chunks with embeddings

-- Create extension for vector operations (PostgreSQL specific)
-- Note: This requires the pgvector extension to be installed
-- For other databases, this will be ignored
CREATE EXTENSION IF NOT EXISTS vector;

-- Create the document_chunk table
CREATE TABLE IF NOT EXISTS document_chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id VARCHAR(255) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL UNIQUE,
    page_number INTEGER,
    position_in_page INTEGER,
    embedding VECTOR(384), -- Adjust dimension based on embedding model
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for efficient querying
-- CREATE INDEX IF NOT EXISTS idx_document_chunk_document_id ON document_chunk(document_id);
-- CREATE INDEX IF NOT EXISTS idx_document_chunk_filename ON document_chunk(filename);
-- CREATE INDEX IF NOT EXISTS idx_document_chunk_content_hash ON document_chunk(content_hash);
-- CREATE INDEX IF NOT EXISTS idx_document_chunk_page_number ON document_chunk(page_number);

-- Create vector index for similarity search (PostgreSQL with pgvector)
-- Note: This will be ignored on databases that don't support vector operations
-- CREATE INDEX IF NOT EXISTS idx_document_chunk_embedding_cosine
-- ON document_chunk USING ivfflat (embedding vector_cosine_ops)
-- WITH (lists = 100);

-- Create a view for document statistics
-- CREATE OR REPLACE VIEW document_chunk_stats AS
-- SELECT
--     document_id,
--     filename,
--     COUNT(*) as chunk_count,
--     MAX(page_number) as max_page,
--     MIN(created_at) as first_processed,
--     MAX(updated_at) as last_updated
-- FROM document_chunk
-- GROUP BY document_id, filename;

-- COMMENT ON TABLE document_chunk IS 'Stores document chunks with embeddings for RAG functionality';
-- COMMENT ON COLUMN document_chunk.document_id IS 'Unique identifier for the source document';
-- COMMENT ON COLUMN document_chunk.filename IS 'Original filename of the source document';
-- COMMENT ON COLUMN document_chunk.chunk_index IS 'Sequential index of this chunk within the document';
-- COMMENT ON COLUMN document_chunk.content IS 'The text content of this chunk';
-- COMMENT ON COLUMN document_chunk.content_hash IS 'SHA-256 hash of content for deduplication';
-- COMMENT ON COLUMN document_chunk.embedding IS 'Vector embedding of the content for semantic search';
-- COMMENT ON VIEW document_chunk_stats IS 'Statistics view for processed documents';
