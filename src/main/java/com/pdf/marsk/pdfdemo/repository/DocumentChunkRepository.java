package com.pdf.marsk.pdfdemo.repository;

import com.pdf.marsk.pdfdemo.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for managing document chunks with embedding-based queries
 */
@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
      /**
     * Find all chunks for a specific document
     */
    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(String documentId);
    
    /**
     * Find all chunks for a specific document (without ordering)
     */
    List<DocumentChunk> findByDocumentId(String documentId);
    
    /**
     * Find chunks by filename
     */
    List<DocumentChunk> findByFilenameOrderByChunkIndex(String filename);
    
    /**
     * Find chunks by content hash (for deduplication)
     */
    List<DocumentChunk> findByContentHash(String contentHash);
    
    /**
     * Check if document already exists
     */
    boolean existsByDocumentId(String documentId);
    
    /**
     * Delete all chunks for a document
     */
    void deleteByDocumentId(String documentId);
    
    /**
     * Get distinct document IDs
     */
    @Query("SELECT DISTINCT dc.documentId FROM DocumentChunk dc")
    List<String> findDistinctDocumentIds();
    
    /**
     * Get documents with metadata
     */
    @Query("SELECT dc.documentId, dc.filename, COUNT(dc), MIN(dc.createdAt) " +
           "FROM DocumentChunk dc " +
           "GROUP BY dc.documentId, dc.filename " +
           "ORDER BY MIN(dc.createdAt) DESC")
    List<Object[]> findDocumentSummaries();
    
    /**
     * Find chunks by page number for a specific document
     */
    List<DocumentChunk> findByDocumentIdAndPageNumberOrderByChunkIndex(String documentId, Integer pageNumber);
    
    /**
     * Find chunks by content (for similarity lookup)
     */
    List<DocumentChunk> findByContent(String content);
      /**
     * Count distinct document IDs
     */
    @Query("SELECT COUNT(DISTINCT dc.documentId) FROM DocumentChunk dc")
    long countDistinctDocumentIds();
    
    /**
     * Find all chunks that have embeddings (not null)
     */
    @Query("SELECT dc FROM DocumentChunk dc WHERE dc.embedding IS NOT NULL")
    List<DocumentChunk> findAllWithEmbeddings();
    
    /**
     * Find chunks from documents other than the specified document ID
     */
    List<DocumentChunk> findByDocumentIdNot(String documentId);
    
    /**
     * Count distinct documents
     */
    @Query("SELECT COUNT(DISTINCT dc.documentId) FROM DocumentChunk dc")
    long countDistinctDocuments();
    
    /**
     * Get average chunks per document
     */
    @Query("SELECT AVG(subquery.chunkCount) FROM (SELECT COUNT(dc) as chunkCount FROM DocumentChunk dc GROUP BY dc.documentId) as subquery")
    Double getAverageChunksPerDocument();
    
    /**
     * Find chunks without embeddings
     */
    @Query("SELECT dc FROM DocumentChunk dc WHERE dc.embedding IS NULL")
    List<DocumentChunk> findByEmbeddingIsNull();
      /**
     * Clear all embeddings (set to null)
     */
    @Modifying
    @Query("UPDATE DocumentChunk dc SET dc.embedding = NULL")
    void clearAllEmbeddings();
}
