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
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
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
    private final OllamaService ollamaService;
    private final ContentRetriever contentRetriever;

    public SimpleLangChain4jRagService(EmbeddingModel embeddingModel,
                                       EmbeddingStore<TextSegment> embeddingStore,
                                       DocumentChunkRepository documentChunkRepository,
                                       RagConfigurationProperties ragConfig,
                                       DocumentProcessingProperties documentConfig,
                                       OllamaService ollamaService) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.documentChunkRepository = documentChunkRepository;
        this.ragConfig = ragConfig;
        this.documentConfig = documentConfig;
        this.ollamaService = ollamaService;
        this.contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(ragConfig.getContext().getMaxChunks()) // Use maxChunks from Context for retriever
                .minScore(ragConfig.getSimilarityThreshold()) // Default min score for retriever
                .build();
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
            logger.info("Finding similar chunks for query: '{}' using ContentRetriever (maxResults: {}, minScore: {} from config)", query, maxResults, minScore);
            // maxResults and minScore parameters for this method are now effectively ignored if retriever is configured with defaults.
            // For more dynamic control, ContentRetriever could be built on-the-fly or this method could adjust retriever's settings if API allows.

            List<dev.langchain4j.rag.content.Content> relevantContent = contentRetriever.retrieve(dev.langchain4j.rag.query.Query.from(query)); // Wrap String in Query object

            if (relevantContent == null || relevantContent.isEmpty()) {
                logger.info("No relevant content found by ContentRetriever for query: '{}'", query);
                return Collections.emptyList();
            }
            logger.info("Found {} relevant content items from ContentRetriever for query: '{}'", relevantContent.size(), query);

            List<DocumentChunk> results = new ArrayList<>();
            for (dev.langchain4j.rag.content.Content contentItem : relevantContent) {
                if (!(contentItem instanceof TextSegment)) {
                    logger.warn("Skipping non-TextSegment content item: {}", contentItem);
                    continue;
                }
                TextSegment segment = (TextSegment) contentItem;
                Metadata metadata = segment.metadata();
                if (metadata == null) {
                    logger.warn("Encountered a TextSegment with null metadata from ContentRetriever. Skipping. Segment text: {}", segment.text().substring(0, Math.min(50, segment.text().length())));
                    continue;
                }

                DocumentChunk chunk = new DocumentChunk();
                String documentId = metadata.getString("document_id");
                String filename = metadata.getString("filename");
                String chunkIndexStr = metadata.getString("chunk_index");
                // ContentRetriever might not directly provide a score in the TextSegment metadata
                // If score is needed, it might require a custom ContentRetriever or post-processing

                if (documentId == null || filename == null || chunkIndexStr == null) {
                    logger.warn("TextSegment metadata from ContentRetriever is missing required fields (document_id, filename, or chunk_index). Segment: {}", segment);
                }
                
                chunk.setDocumentId(documentId);
                chunk.setFilename(filename);
                try {
                    if (chunkIndexStr != null) {
                        chunk.setChunkIndex(Integer.parseInt(chunkIndexStr));
                    } else {
                         logger.warn("chunk_index is null for segment from document_id: {}", documentId);
                         chunk.setChunkIndex(-1);
                    }
                } catch (NumberFormatException e) {
                    logger.error("Failed to parse chunk_index '{}' for document_id: {}. Error: {}", chunkIndexStr, documentId, e.getMessage());
                    chunk.setChunkIndex(-1);
                }
                chunk.setContent(segment.text());
                // Similarity score might not be readily available from all ContentRetriever implementations
                // For EmbeddingStoreContentRetriever, it's not directly in the returned TextSegment.
                // If score is crucial, the lower-level EmbeddingStore.findRelevant might be needed,
                // or a custom retriever that preserves/calculates score.
                // For now, we'll set a default or leave it.
                // chunk.setSimilarityScore(0.0); // Placeholder

                results.add(chunk);
                logger.debug("Converted segment to DocumentChunk: docId={}, file={}, index={}",
                    chunk.getDocumentId(), chunk.getFilename(), chunk.getChunkIndex());
            }
            
            logger.info("Successfully converted {} segments to DocumentChunks for query: '{}'", results.size(), query);
            return results;
            
        } catch (Exception e) {
            logger.error("Error finding similar chunks: {}", e.getMessage(), e);
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
                return Collections.emptyList();
            }
            
            // Use the first chunk's content for similarity search
            String representativeContent = documentChunks.get(0).getContent();
            return findSimilarChunks(representativeContent, maxResults, ragConfig.getSimilarityThreshold());
            
        } catch (Exception e) {
            logger.error("Error finding similar documents for ID {}: {}", documentId, e.getMessage(), e);
            return Collections.emptyList();
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
        String promptForLlm = """
            Based on the following context, please answer the user's question.
            If the context doesn't contain enough information, state that clearly.

            Context:
            """ + context + """

            User's Question: """ + userQuery + """

            Answer:""";

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
