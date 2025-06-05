-- Test schema for H2 database
-- This is a simplified version without PostgreSQL-specific features

-- Create the document_chunk table
CREATE TABLE IF NOT EXISTS document_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id VARCHAR(255) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL UNIQUE,
    page_number INTEGER,
    position_in_page INTEGER,
    embedding CLOB, -- Using CLOB instead of VECTOR for H2
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_document_id (document_id),
    INDEX idx_filename (filename),
    INDEX idx_content_hash (content_hash)
);

-- Create the knowledge_snippet table
CREATE TABLE IF NOT EXISTS knowledge_snippet (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    snippet TEXT NOT NULL,
    document_name VARCHAR(255),
    page_number INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_title (title),
    INDEX idx_document_name (document_name)
);

-- Create the ocr_text_document table
CREATE TABLE IF NOT EXISTS ocr_text_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    extracted_text TEXT,
    processing_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_filename (filename),
    INDEX idx_processing_status (processing_status)
);

-- Create the html_template table
CREATE TABLE IF NOT EXISTS html_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    html_content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_name (name)
);
