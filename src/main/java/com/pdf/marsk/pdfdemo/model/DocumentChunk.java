package com.pdf.marsk.pdfdemo.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Enhanced document chunk entity for storing document segments with embeddings
 * Supports efficient retrieval and semantic search
 */
@Entity
@Table(name = "document_chunks", indexes = {
        @Index(name = "idx_document_chunks_document_id", columnList = "documentId"),
        @Index(name = "idx_document_chunks_chunk_index", columnList = "chunkIndex"),
        @Index(name = "idx_document_chunks_created_at", columnList = "createdAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentChunk {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String documentId;
    
    @Column(nullable = false)
    private String filename;
    
    @Column(nullable = false)
    private Integer chunkIndex;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;
    
    @Column
    private Integer pageNumber;
    
    @Column
    private Integer startPosition;
    
    @Column
    private Integer endPosition;
    
    @Column(name = "content_hash")
    private String contentHash;
    
    @ElementCollection(fetch = FetchType.EAGER) // Changed to EAGER
    @CollectionTable(name = "document_chunk_embeddings",
                     joinColumns = @JoinColumn(name = "chunk_id"))
    @Column(name = "embedding_value")
    private List<Double> embedding;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column
    private LocalDateTime updatedAt;
    
    // Transient fields for RAG functionality (not persisted)
    @Transient
    private Double similarityScore;
    
    @Transient
    private Map<String, Object> metadata;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Explicit setter methods (in case Lombok has issues)
    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }
    
    public void setFilename(String filename) {
        this.filename = filename;
    }
    
    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public void setEmbedding(List<Double> embedding) {
        this.embedding = embedding;
    }
    
    public void setSimilarityScore(Double similarityScore) {
        this.similarityScore = similarityScore;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    // Explicit getter methods (in case Lombok has issues)
    public String getContent() {
        return content;
    }
    
    public String getDocumentId() {
        return documentId;
    }
    
    public String getFilename() {
        return filename;
    }
      public Integer getChunkIndex() {
        return chunkIndex;
    }
      public List<Double> getEmbedding() {
        return embedding;
    }
    
    public Long getId() {
        return id;
    }
      public Integer getPageNumber() {
        return pageNumber;
    }
    
    // Manual builder pattern to work around Lombok issues
    public static DocumentChunkBuilder builder() {
        return new DocumentChunkBuilder();
    }
    
    public static class DocumentChunkBuilder {
        private DocumentChunk chunk = new DocumentChunk();
        
        public DocumentChunkBuilder documentId(String documentId) {
            chunk.documentId = documentId;
            return this;
        }
        
        public DocumentChunkBuilder filename(String filename) {
            chunk.filename = filename;
            return this;
        }
        
        public DocumentChunkBuilder chunkIndex(Integer chunkIndex) {
            chunk.chunkIndex = chunkIndex;
            return this;
        }
        
        public DocumentChunkBuilder content(String content) {
            chunk.content = content;
            return this;
        }
        
        public DocumentChunkBuilder pageNumber(Integer pageNumber) {
            chunk.pageNumber = pageNumber;
            return this;
        }
        
        public DocumentChunkBuilder startPosition(Integer startPosition) {
            chunk.startPosition = startPosition;
            return this;
        }
        
        public DocumentChunkBuilder endPosition(Integer endPosition) {
            chunk.endPosition = endPosition;
            return this;
        }
        
        public DocumentChunkBuilder contentHash(String contentHash) {
            chunk.contentHash = contentHash;
            return this;
        }
        
        public DocumentChunkBuilder embedding(List<Double> embedding) {
            chunk.embedding = embedding;
            return this;
        }
        
        public DocumentChunk build() {
            return chunk;
        }
    }
}
