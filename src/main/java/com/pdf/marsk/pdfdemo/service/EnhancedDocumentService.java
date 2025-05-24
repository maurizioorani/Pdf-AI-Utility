package com.pdf.marsk.pdfdemo.service;

import com.pdf.marsk.pdfdemo.model.DocumentChunk;
import com.pdf.marsk.pdfdemo.repository.DocumentChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Enhanced document processing service with RAG (Retrieval Augmented Generation) capabilities
 * Provides efficient semantic search and document chunk management
 */
@Service
@Transactional
public class EnhancedDocumentService {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedDocumentService.class);
      private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingService embeddingService;
    private final TextChunkingService textChunkingService;
    private ExecutorService executorService;
    
    @Value("${rag.similarity.threshold:0.7}")
    private double similarityThreshold;
    
    @Value("${rag.max.results:10}")
    private int maxResults;
    
    @Value("${rag.processing.threads:4}")
    private int processingThreads;
    
    public EnhancedDocumentService(DocumentChunkRepository documentChunkRepository,
                                   EmbeddingService embeddingService,
                                   TextChunkingService textChunkingService) {
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingService = embeddingService;
        this.textChunkingService = textChunkingService;
    }
    
    @PostConstruct
    public void initializeExecutorService() {
        this.executorService = Executors.newFixedThreadPool(processingThreads);
        logger.info("Initialized ExecutorService with {} threads", processingThreads);
    }
    
    @PreDestroy
    public void shutdownExecutorService() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            logger.info("ExecutorService shutdown");
        }
    }
    
    /**
     * Process and store a document with embeddings for RAG
     */
    public CompletableFuture<String> processDocumentAsync(String filename, String fullText, String documentId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Starting document processing for: {} (ID: {})", filename, documentId);
                
                // Check if document already exists
                if (documentChunkRepository.existsByDocumentId(documentId)) {
                    logger.info("Document {} already processed, skipping", documentId);
                    return documentId;
                }
                
                // Chunk the document intelligently
                List<String> chunks = textChunkingService.chunkText(fullText);
                logger.info("Document chunked into {} segments", chunks.size());
                
                // Process chunks in parallel
                List<CompletableFuture<DocumentChunk>> chunkFutures = new ArrayList<>();
                
                for (int i = 0; i < chunks.size(); i++) {
                    final int chunkIndex = i;
                    final String chunkContent = chunks.get(i);
                    
                    CompletableFuture<DocumentChunk> chunkFuture = CompletableFuture.supplyAsync(() -> {
                        return processChunk(documentId, filename, chunkIndex, chunkContent);
                    }, executorService);
                    
                    chunkFutures.add(chunkFuture);
                }
                
                // Wait for all chunks to be processed
                List<DocumentChunk> processedChunks = chunkFutures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList());
                
                // Save all chunks to database
                documentChunkRepository.saveAll(processedChunks);
                
                logger.info("Successfully processed and stored {} chunks for document {}", 
                           processedChunks.size(), documentId);
                
                return documentId;
                
            } catch (Exception e) {
                logger.error("Error processing document {}: {}", documentId, e.getMessage(), e);
                throw new RuntimeException("Failed to process document", e);
            }
        });
    }
    
    /**
     * Perform semantic search across stored documents
     */
    public List<DocumentChunk> semanticSearch(String query, int maxResults) {
        try {
            logger.info("Performing semantic search for query: '{}'", query);
            
            // Generate embedding for the query
            List<Double> queryEmbedding = embeddingService.generateEmbedding(query);
            
            // Get all document chunks (in a real implementation, you'd use a vector database)
            List<DocumentChunk> allChunks = documentChunkRepository.findAll();
            
            // Calculate similarities and sort
            List<ScoredChunk> scoredChunks = allChunks.stream()
                    .filter(chunk -> chunk.getEmbedding() != null && !chunk.getEmbedding().isEmpty())
                    .map(chunk -> {
                        double similarity = embeddingService.calculateCosineSimilarity(
                                queryEmbedding, chunk.getEmbedding());
                        return new ScoredChunk(chunk, similarity);
                    })
                    .filter(scored -> scored.score >= similarityThreshold)
                    .sorted((a, b) -> Double.compare(b.score, a.score))
                    .limit(maxResults)
                    .collect(Collectors.toList());
            
            logger.info("Found {} relevant chunks with similarity >= {}", 
                       scoredChunks.size(), similarityThreshold);
            
            return scoredChunks.stream()
                    .map(scored -> scored.chunk)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            logger.error("Error performing semantic search: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Retrieve relevant context for a query to augment LLM processing
     */
    public String retrieveRelevantContext(String query, int maxChunks) {
        List<DocumentChunk> relevantChunks = semanticSearch(query, maxChunks);
        
        if (relevantChunks.isEmpty()) {
            return "";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("RELEVANT CONTEXT FROM DOCUMENTS:\n\n");
        
        for (int i = 0; i < relevantChunks.size(); i++) {
            DocumentChunk chunk = relevantChunks.get(i);
            context.append("--- Context ").append(i + 1).append(" ---\n");
            context.append("Source: ").append(chunk.getFilename());
            if (chunk.getPageNumber() != null) {
                context.append(" (Page ").append(chunk.getPageNumber()).append(")");
            }
            context.append("\n");
            context.append(chunk.getContent()).append("\n\n");
        }
        
        return context.toString();
    }
    
    /**
     * Check if a document has already been processed
     */
    @Cacheable(value = "documentExists", key = "#documentId")
    public boolean isDocumentProcessed(String documentId) {
        return documentChunkRepository.existsByDocumentId(documentId);
    }
    
    /**
     * Get document processing statistics
     */
    public Map<String, Object> getProcessingStats() {
        List<Object[]> summaries = documentChunkRepository.findDocumentSummaries();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDocuments", summaries.size());
        stats.put("totalChunks", documentChunkRepository.count());
        
        List<Map<String, Object>> documentDetails = summaries.stream()
                .map(row -> {
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("documentId", row[0]);
                    doc.put("filename", row[1]);
                    doc.put("chunkCount", row[2]);
                    doc.put("createdAt", row[3]);
                    return doc;
                })
                .collect(Collectors.toList());
        
        stats.put("documents", documentDetails);
        return stats;
    }
    
    /**
     * Delete a processed document and its chunks
     */
    public void deleteDocument(String documentId) {
        logger.info("Deleting document: {}", documentId);
        documentChunkRepository.deleteByDocumentId(documentId);
    }
    
    /**
     * Process a single chunk with embedding generation
     */
    private DocumentChunk processChunk(String documentId, String filename, int chunkIndex, String content) {
        try {
            // Generate content hash for deduplication
            String contentHash = embeddingService.generateContentHash(content);
            
            // Check if chunk already exists
            List<DocumentChunk> existingChunks = documentChunkRepository.findByContentHash(contentHash);
            if (!existingChunks.isEmpty()) {
                logger.debug("Chunk {} already exists with same content hash, reusing embedding", chunkIndex);
                DocumentChunk existing = existingChunks.get(0);
                return DocumentChunk.builder()
                        .documentId(documentId)
                        .filename(filename)
                        .chunkIndex(chunkIndex)
                        .content(content)
                        .contentHash(contentHash)
                        .embedding(existing.getEmbedding())
                        .build();
            }
            
            // Generate embedding for new content
            List<Double> embedding = embeddingService.generateEmbedding(content, contentHash);
            
            // Extract page number if available
            Integer pageNumber = extractPageNumber(content);
            
            return DocumentChunk.builder()
                    .documentId(documentId)
                    .filename(filename)
                    .chunkIndex(chunkIndex)
                    .content(content)
                    .pageNumber(pageNumber)
                    .contentHash(contentHash)
                    .embedding(embedding)
                    .build();
                    
        } catch (Exception e) {
            logger.error("Error processing chunk {} for document {}: {}", chunkIndex, documentId, e.getMessage());
            throw new RuntimeException("Failed to process chunk", e);
        }
    }
    
    /**
     * Extract page number from chunk content if available
     */
    private Integer extractPageNumber(String content) {
        if (content.contains("--- Page ")) {
            try {
                String[] lines = content.split("\\n");
                for (String line : lines) {
                    if (line.contains("--- Page ")) {
                        String pageStr = line.replaceAll(".*--- Page (\\d+).*", "$1");
                        return Integer.parseInt(pageStr);
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not extract page number from content");
            }
        }
        return null;
    }
    
    /**
     * Helper class for scored chunks during similarity search
     */
    private static class ScoredChunk {
        final DocumentChunk chunk;
        final double score;
        
        ScoredChunk(DocumentChunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
    }

    /**
     * Find similar document chunks based on semantic similarity
     */
    public List<DocumentChunk> findSimilarChunks(String query, int limit, double threshold) {
        try {
            logger.info("Finding similar chunks for query: '{}' with threshold: {}", query, threshold);
            
            // Generate embedding for the query
            List<Double> queryEmbedding = embeddingService.generateEmbedding(query);
            
            // Get all document chunks
            List<DocumentChunk> allChunks = documentChunkRepository.findAll();
            
            // Calculate similarities and sort
            List<ScoredChunk> scoredChunks = allChunks.stream()
                    .filter(chunk -> chunk.getEmbedding() != null && !chunk.getEmbedding().isEmpty())
                    .map(chunk -> {
                        double similarity = embeddingService.calculateCosineSimilarity(
                                queryEmbedding, chunk.getEmbedding());
                        return new ScoredChunk(chunk, similarity);
                    })
                    .filter(scored -> scored.score >= threshold)
                    .sorted((a, b) -> Double.compare(b.score, a.score))
                    .limit(limit)
                    .collect(Collectors.toList());
            
            logger.info("Found {} relevant chunks with similarity >= {}", 
                       scoredChunks.size(), threshold);
            
            return scoredChunks.stream()
                    .map(scored -> scored.chunk)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            logger.error("Error finding similar chunks: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Find similar documents by document ID
     */
    public List<DocumentChunk> findSimilarDocuments(String documentId, int limit) {
        try {
            logger.info("Finding similar documents for document ID: {}", documentId);
            
            // Get chunks from the reference document
            List<DocumentChunk> referenceChunks = documentChunkRepository.findByDocumentId(documentId);
            if (referenceChunks.isEmpty()) {
                logger.warn("No chunks found for document ID: {}", documentId);
                return Collections.emptyList();
            }
            
            // Use the first chunk as reference for similarity
            DocumentChunk referenceChunk = referenceChunks.get(0);
            if (referenceChunk.getEmbedding() == null || referenceChunk.getEmbedding().isEmpty()) {
                logger.warn("Reference chunk has no embedding");
                return Collections.emptyList();
            }
            
            // Get all chunks except from the reference document
            List<DocumentChunk> allChunks = documentChunkRepository.findAll().stream()
                    .filter(chunk -> !chunk.getDocumentId().equals(documentId))
                    .collect(Collectors.toList());
            
            // Calculate similarities and sort
            List<ScoredChunk> scoredChunks = allChunks.stream()
                    .filter(chunk -> chunk.getEmbedding() != null && !chunk.getEmbedding().isEmpty())
                    .map(chunk -> {
                        double similarity = embeddingService.calculateCosineSimilarity(
                                referenceChunk.getEmbedding(), chunk.getEmbedding());
                        return new ScoredChunk(chunk, similarity);
                    })
                    .filter(scored -> scored.score >= similarityThreshold)
                    .sorted((a, b) -> Double.compare(b.score, a.score))
                    .limit(limit)
                    .collect(Collectors.toList());
            
            logger.info("Found {} similar documents", scoredChunks.size());
            
            return scoredChunks.stream()
                    .map(scored -> scored.chunk)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            logger.error("Error finding similar documents: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Get comprehensive processing statistics
     */
    public Map<String, Object> getProcessingStatistics() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            long totalChunks = documentChunkRepository.count();
            stats.put("totalChunks", totalChunks);
            
            // Get document summaries if method exists
            try {
                List<Object[]> summaries = documentChunkRepository.findDocumentSummaries();
                stats.put("totalDocuments", summaries.size());
                
                List<Map<String, Object>> documentDetails = summaries.stream()
                        .map(row -> {
                            Map<String, Object> doc = new HashMap<>();
                            doc.put("documentId", row[0]);
                            doc.put("filename", row[1]);
                            doc.put("chunkCount", row[2]);
                            doc.put("createdAt", row[3]);
                            return doc;
                        })
                        .collect(Collectors.toList());
                
                stats.put("documents", documentDetails);
            } catch (Exception e) {
                logger.debug("findDocumentSummaries method not available, using basic stats");
                // Fallback: count unique documents
                long uniqueDocuments = documentChunkRepository.findAll().stream()
                        .map(DocumentChunk::getDocumentId)
                        .distinct()
                        .count();
                stats.put("totalDocuments", uniqueDocuments);
                stats.put("documents", Collections.emptyList());
            }
            
            // Add processing performance metrics
            stats.put("averageChunkSize", totalChunks > 0 ? 
                documentChunkRepository.findAll().stream()
                    .mapToInt(chunk -> chunk.getContent().length())
                    .average().orElse(0.0) : 0.0);
            
            stats.put("embeddedChunks", documentChunkRepository.findAll().stream()
                    .filter(chunk -> chunk.getEmbedding() != null && !chunk.getEmbedding().isEmpty())
                    .count());
            
            stats.put("generatedAt", LocalDateTime.now());
            
            return stats;
            
        } catch (Exception e) {
            logger.error("Error generating processing statistics: {}", e.getMessage(), e);
            Map<String, Object> errorStats = new HashMap<>();
            errorStats.put("error", "Failed to generate statistics: " + e.getMessage());
            errorStats.put("generatedAt", LocalDateTime.now());
            return errorStats;
        }
    }

    /**
     * Reindex all documents (simulate async operation)
     */
    public String reindexAllDocuments(boolean force) {
        String taskId = "reindex_" + System.currentTimeMillis();
        
        // Submit async task
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting reindexing of all documents (force: {})", force);
                
                List<DocumentChunk> allChunks = documentChunkRepository.findAll();
                int processed = 0;
                int updated = 0;
                
                for (DocumentChunk chunk : allChunks) {
                    try {
                        // Skip if already has embedding and not forcing
                        if (!force && chunk.getEmbedding() != null && !chunk.getEmbedding().isEmpty()) {
                            processed++;
                            continue;
                        }
                        
                        // Generate new embedding
                        List<Double> newEmbedding = embeddingService.generateEmbedding(chunk.getContent());
                        chunk.setEmbedding(newEmbedding);
                        documentChunkRepository.save(chunk);
                        
                        processed++;
                        updated++;
                        
                        if (processed % 10 == 0) {
                            logger.info("Reindexing progress: {}/{} chunks processed, {} updated", 
                                       processed, allChunks.size(), updated);
                        }
                        
                    } catch (Exception e) {
                        logger.error("Error reindexing chunk {}: {}", chunk.getId(), e.getMessage());
                    }
                }
                
                logger.info("Reindexing completed: {}/{} chunks processed, {} updated", 
                           processed, allChunks.size(), updated);
                          
            } catch (Exception e) {
                logger.error("Error during reindexing: {}", e.getMessage(), e);
            }
        }, executorService);
        
        return taskId;
    }
}
