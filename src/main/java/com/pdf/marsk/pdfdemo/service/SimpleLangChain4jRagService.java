package com.pdf.marsk.pdfdemo.service;

import com.pdf.marsk.pdfdemo.config.DocumentProcessingProperties;
import com.pdf.marsk.pdfdemo.config.RagConfigurationProperties;
import com.pdf.marsk.pdfdemo.model.DocumentChunk;
import com.pdf.marsk.pdfdemo.repository.DocumentChunkRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch; // Added
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired; // Added
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Simplified LangChain4j RAG Service for document processing and semantic search.
 * This version focuses on basic functionality while maintaining compatibility.
 */
@Service
public class SimpleLangChain4jRagService {
    
    private static final Logger logger = LoggerFactory.getLogger(SimpleLangChain4jRagService.class);
    
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentChunkRepository documentChunkRepository;
    private final RagConfigurationProperties ragConfig;
    private final DocumentProcessingProperties documentConfig;
    private final OllamaService ollamaService; // Added

    @Autowired
    public SimpleLangChain4jRagService(EmbeddingModel embeddingModel,
                                       EmbeddingStore<TextSegment> embeddingStore,
                                       DocumentChunkRepository documentChunkRepository,
                                       RagConfigurationProperties ragConfig,
                                       DocumentProcessingProperties documentConfig,
                                       OllamaService ollamaService) { // Added
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.documentChunkRepository = documentChunkRepository;
        this.ragConfig = ragConfig;
        this.documentConfig = documentConfig;
        this.ollamaService = ollamaService; // Added
    }
    
    /**
     * Process a document for RAG functionality
     */
    public CompletableFuture<String> processDocument(String documentId, String filename, String content) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Processing document {} for RAG", documentId);
                
                // Create document
                Document document = Document.from(content);
                
                // Split into chunks
                var splitter = DocumentSplitters.recursive(
                    documentConfig.getChunkSize(),
                    documentConfig.getChunkOverlap()
                );
                List<TextSegment> segments = splitter.split(document);
                
                // Process each segment
                List<DocumentChunk> documentChunksForRepo = new ArrayList<>();
                List<TextSegment> segmentsForEmbeddingStore = new ArrayList<>();
                List<Embedding> embeddingsForEmbeddingStore = new ArrayList<>();

                for (int i = 0; i < segments.size(); i++) {
                    TextSegment segment = segments.get(i);

                    // Add metadata to the segment for the embedding store
                    Metadata metadata = new Metadata();
                    metadata.put("document_id", documentId);
                    metadata.put("filename", filename);
                    metadata.put("chunk_index", String.valueOf(i));
                    TextSegment segmentWithMetadata = TextSegment.from(segment.text(), metadata);
                    segmentsForEmbeddingStore.add(segmentWithMetadata);
                    
                    // Generate embedding
                    Embedding embedding = embeddingModel.embed(segmentWithMetadata).content(); // Embed segment with metadata
                    embeddingsForEmbeddingStore.add(embedding);

                    List<Double> embeddingVector = new ArrayList<>();
                    for (float value : embedding.vector()) {
                        embeddingVector.add((double) value);
                    }
                    
                    // Create DocumentChunk for JPA repository (optional, could be phased out)
                    DocumentChunk chunk = new DocumentChunk();
                    chunk.setDocumentId(documentId);
                    chunk.setFilename(filename);
                    chunk.setChunkIndex(i);
                    chunk.setContent(segment.text());
                    chunk.setEmbedding(embeddingVector); // Store embedding in DocumentChunk as well for now
                    documentChunksForRepo.add(chunk);
                }
                
                // Add embeddings to the embedding store
                if (!embeddingsForEmbeddingStore.isEmpty()) {
                    embeddingStore.addAll(embeddingsForEmbeddingStore, segmentsForEmbeddingStore);
                    logger.info("Added {} segments with embeddings to the EmbeddingStore for document {}", segmentsForEmbeddingStore.size(), documentId);
                }

                // Save all DocumentChunk entities to JPA repository
                documentChunkRepository.saveAll(documentChunksForRepo);
                
                logger.info("Successfully processed document {} into {} chunks (JPA) and {} segments (EmbeddingStore)",
                    documentId, documentChunksForRepo.size(), segmentsForEmbeddingStore.size());
                return "SUCCESS";
                
            } catch (Exception e) {
                logger.error("Error processing document {}: {}", documentId, e.getMessage(), e);
                return "ERROR: " + e.getMessage();
            }
        });
    }
    
    /**
     * Find similar chunks using semantic search
     */
    public List<DocumentChunk> findSimilarChunks(String query, int maxResults, double minScore) {
        try {
            // REVERTING to manual cosine similarity search as EmbeddingStore.findRelevant is not resolving.
            // This bypasses the vector store for searching and uses the DocumentChunkRepository.
            logger.warn("Reverted to manual cosine similarity search due to issues with EmbeddingStore.findRelevant(). This will be inefficient.");

            TextSegment querySegment = TextSegment.from(query);
            Embedding queryEmbeddingObj = embeddingModel.embed(querySegment).content();
            
            List<Double> queryEmbeddingVector = new ArrayList<>();
            for (float value : queryEmbeddingObj.vector()) {
                queryEmbeddingVector.add((double) value);
            }
            
            List<DocumentChunk> allChunks = documentChunkRepository.findAll();
            logger.info("Found {} total chunks in repository for manual search.", allChunks.size());
            if (allChunks.isEmpty()) {
                logger.warn("No chunks found in DocumentChunkRepository. Cannot find similar chunks.");
                return Collections.emptyList();
            }

            List<SimilarityResult> similarities = new ArrayList<>();
            int processedCount = 0;
            int matchedSizeCount = 0;
            int passedThresholdCount = 0;

            for (DocumentChunk chunk : allChunks) {
                processedCount++;
                if (chunk.getEmbedding() != null && !chunk.getEmbedding().isEmpty()) {
                    if (chunk.getEmbedding().size() == queryEmbeddingVector.size()) {
                        matchedSizeCount++;
                        double similarity = calculateCosineSimilarity(queryEmbeddingVector, chunk.getEmbedding());
                        // Log individual similarity scores for debugging
                        logger.debug("Chunk ID: {}, Doc ID: {}, Index: {}, Similarity: {}", chunk.getId(), chunk.getDocumentId(), chunk.getChunkIndex(), similarity);
                        if (similarity >= minScore) {
                            passedThresholdCount++;
                            similarities.add(new SimilarityResult(chunk, similarity));
                        }
                    } else {
                        logger.warn("Chunk ID: {} (Doc ID: {}) has embedding size {} but query embedding size is {}. Skipping.",
                                chunk.getId(), chunk.getDocumentId(), chunk.getEmbedding().size(), queryEmbeddingVector.size());
                    }
                } else {
                    logger.warn("Chunk ID: {} (Doc ID: {}) has null or empty embedding. Skipping.", chunk.getId(), chunk.getDocumentId());
                }
            }
            logger.info("Manual search: Processed {} chunks, {} had matching embedding sizes, {} passed similarity threshold {}.",
                    processedCount, matchedSizeCount, passedThresholdCount, minScore);
            
            similarities.sort((a, b) -> Double.compare(b.similarity, a.similarity));
            int limit = Math.min(maxResults, similarities.size());
            
            List<DocumentChunk> results = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                SimilarityResult result = similarities.get(i);
                DocumentChunk chunk = result.chunk;
                chunk.setSimilarityScore(result.similarity);
                results.add(chunk);
            }
            
            logger.debug("Found {} similar chunks for query (manual search): {}", results.size(), query);
            return results;
            
        } catch (Exception e) {
            logger.error("Error finding similar chunks (manual search): {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Find similar documents by document ID
     */
    public List<DocumentChunk> findSimilarDocuments(String documentId, int maxResults) {
        try {
            // Get a representative chunk from the document
            List<DocumentChunk> documentChunks = documentChunkRepository.findByDocumentId(documentId);
            if (documentChunks.isEmpty()) {
                return new ArrayList<>();
            }
            
            // Use the first chunk's content for similarity search
            String representativeContent = documentChunks.get(0).getContent();
            return findSimilarChunks(representativeContent, maxResults, ragConfig.getSimilarityThreshold());
            
        } catch (Exception e) {
            logger.error("Error finding similar documents for ID {}: {}", documentId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Retrieve relevant context for a query
     */
    public String retrieveRelevantContext(String query, int maxContextSize) {
        // TEMPORARY DEBUGGING: Lower the minScore to see if any chunks are found
        double currentSimilarityThreshold = ragConfig.getSimilarityThreshold();
        // double debugMinScore = 0.1;
        // logger.warn("DEBUGGING: Using temporarily lowered minScore for findSimilarChunks: {}", debugMinScore);
        // List<DocumentChunk> relevantChunks = findSimilarChunks(query, maxContextSize, debugMinScore);
        
        // Using the configured threshold
        List<DocumentChunk> relevantChunks = findSimilarChunks(query, maxContextSize, currentSimilarityThreshold);
        logger.info("Retrieved {} relevant chunks for query '{}' with threshold {}", relevantChunks.size(), query, currentSimilarityThreshold);
        
        StringBuilder context = new StringBuilder();
        for (DocumentChunk chunk : relevantChunks) {
            context.append(chunk.getContent()).append("\n\n");
        }
        
        return context.toString().trim();
    }
    
    /**
     * Check if a document has been processed
     */
    public boolean isDocumentProcessed(String documentId) {
        return documentChunkRepository.existsByDocumentId(documentId);
    }
    
    /**
     * Get processing statistics
     */
    public Map<String, Object> getProcessingStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalChunks", documentChunkRepository.count());
        stats.put("uniqueDocuments", documentChunkRepository.countDistinctDocumentIds());
        stats.put("ragEnabled", ragConfig.isEnabled());
        stats.put("chunkSize", documentConfig.getChunkSize());
        stats.put("chunkOverlap", documentConfig.getChunkOverlap());
        return stats;
    }
    
    /**
     * Re-index all documents (simplified version)
     */
    public String reindexAllDocuments(boolean force) {
        try {
            if (force) {
                logger.info("Starting force re-indexing of all documents");
                return "reindex-task-" + UUID.randomUUID().toString();
            } else {
                logger.info("Starting incremental re-indexing");
                return "reindex-task-" + UUID.randomUUID().toString();
            }
        } catch (Exception e) {
            logger.error("Error starting re-indexing: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start re-indexing: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a list of document summaries (documentId, filename, chunk count, creation date).
     * This is used to list available documents in the vector store.
     * @return A list of Object arrays, where each array contains document metadata.
     */
    public List<Object[]> getAvailableDocumentSummaries() {
        try {
            return documentChunkRepository.findDocumentSummaries();
        } catch (Exception e) {
            logger.error("Error retrieving document summaries: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Deletes a document and all its associated chunks from the DocumentChunkRepository.
     * Note: This does not currently remove embeddings from the PgVectorEmbeddingStore.
     * @param documentId The ID of the document to delete.
     */
    public void deleteDocument(String documentId) {
        if (documentId == null || documentId.trim().isEmpty()) {
            logger.warn("Attempted to delete document with null or empty ID.");
            return;
        }
        try {
            if (documentChunkRepository.existsByDocumentId(documentId)) {
                documentChunkRepository.deleteByDocumentId(documentId);
                logger.info("Successfully deleted all chunks for document ID: {}", documentId);
                // TODO: Implement deletion from PgVectorEmbeddingStore if possible and necessary.
                // This would involve finding all TextSegments with metadata matching the documentId
                // and then removing them. LangChain4j's EmbeddingStore API might not directly support
                // deletion by metadata easily, so this might require custom PgVector queries or a different approach.
            } else {
                logger.warn("Attempted to delete non-existent document ID: {}", documentId);
            }
        } catch (Exception e) {
            logger.error("Error deleting document with ID {}: {}", documentId, e.getMessage(), e);
            // Optionally rethrow or handle as appropriate for your application's error strategy
        }
    }

    /**
     * Deletes all documents and their associated chunks from the DocumentChunkRepository.
     * Note: This does not currently remove embeddings from the PgVectorEmbeddingStore.
     */
    public void deleteAllDocuments() {
        try {
            documentChunkRepository.deleteAll();
            logger.info("Successfully deleted all document chunks from the repository.");
            // TODO: Implement deletion from PgVectorEmbeddingStore if possible and necessary.
            // This would likely involve clearing the entire table or store if supported.
        } catch (Exception e) {
            logger.error("Error deleting all documents: {}", e.getMessage(), e);
            // Optionally rethrow or handle as appropriate
        }
    }
    
    // Helper classes and methods
    private static class SimilarityResult {
        final DocumentChunk chunk;
        final double similarity;
        
        SimilarityResult(DocumentChunk chunk, double similarity) {
            this.chunk = chunk;
            this.similarity = similarity;
        }
    }
    
    private double calculateCosineSimilarity(List<Double> vectorA, List<Double> vectorB) {
        if (vectorA == null || vectorB == null || vectorA.isEmpty() || vectorB.isEmpty()) {
            logger.warn("Cannot calculate cosine similarity for null or empty vectors.");
            return 0.0;
        }
        if (vectorA.size() != vectorB.size()) {
            logger.warn("Cannot calculate cosine similarity for vectors of different sizes: {} vs {}", vectorA.size(), vectorB.size());
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vectorA.size(); i++) {
            dotProduct += vectorA.get(i) * vectorB.get(i);
            normA += Math.pow(vectorA.get(i), 2);
            normB += Math.pow(vectorB.get(i), 2);
        }
        
        if (normA == 0.0 || normB == 0.0) {
            // This case implies one or both vectors are zero vectors.
            // If both are zero, similarity could be 1 (identical) or undefined.
            // If one is zero and other is not, similarity is 0.
            // For simplicity, returning 0 if either norm is zero.
            logger.debug("One or both norms are zero, returning 0 similarity. NormA: {}, NormB: {}", normA, normB);
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Answers a user's query based on the content of documents in the vector store.
     * @param userQuery The user's question.
     * @param modelName The LLM model to use for generating the answer.
     * @return The LLM's answer.
     */
    public String answerQuery(String userQuery, String modelName) {
        logger.info("Answering query: '{}' using model: {}", userQuery, modelName);

        // 1. Retrieve relevant context
        String context = retrieveRelevantContext(userQuery, ragConfig.getContext().getMaxChunks());

        if (context.isEmpty()) {
            logger.info("No relevant context found for query: '{}'", userQuery);
            return "I could not find any relevant information in the documents to answer your question.";
        }

        // 2. Construct a prompt for question answering
        String promptForLlm = String.format(
            "Based on the following context, please answer the user's question.\n" +
            "If the context doesn't contain enough information, state that clearly.\n\n" +
            "Context:\n\"\"\"\n%s\n\"\"\"\n\n" +
            "User's Question: %s\n\n" +
            "Answer:",
            context,
            userQuery
        );

        logger.debug("Prompt for LLM: {}", promptForLlm);

        // 3. Call OllamaService to get the answer
        // We pass the full prompt as the `customPrompt` and the `userQuery` (or context) as the `text` argument.
        // Since `OllamaService.processSingleText` now uses `customPromptToUse` directly if it doesn't contain "%s",
        // this will send our `promptForLlm` directly to the LLM.
        // The `text` argument to `enhanceText` is less critical here as the `customPrompt` is complete.
        // Call the new generateResponse method that bypasses OCR-specific fixing logic
        String rawAnswer = ollamaService.generateResponse(promptForLlm, modelName);
        
        logger.info("Received answer from LLM for query: '{}'. Answer: '{}'", userQuery, rawAnswer);
        return rawAnswer;
    }
}
