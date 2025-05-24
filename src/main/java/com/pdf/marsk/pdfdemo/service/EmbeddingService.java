package com.pdf.marsk.pdfdemo.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for generating text embeddings for semantic search
 * Uses caching to avoid regenerating embeddings for identical text
 */
@Service
public class EmbeddingService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);
    
    private final EmbeddingModel embeddingModel;
      public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
    
    /**
     * Generate embedding for a single text with caching
     */
    @Cacheable(value = "embeddings", key = "#textHash")
    public List<Double> generateEmbedding(String text, String textHash) {
        try {
            TextSegment segment = TextSegment.from(text);
            Response<Embedding> response = embeddingModel.embed(segment);
            
            // Convert LangChain4j embedding to List<Double>
            float[] embeddingVector = response.content().vector();
            List<Double> embedding = new java.util.ArrayList<>();
            for (float value : embeddingVector) {
                embedding.add((double) value);
            }
            
            logger.debug("Generated embedding for text hash: {} (dimension: {})", textHash, embedding.size());
            return embedding;
            
        } catch (Exception e) {
            logger.error("Error generating embedding for text hash {}: {}", textHash, e.getMessage(), e);
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }
    
    /**
     * Generate embedding for text (creates hash automatically)
     */
    public List<Double> generateEmbedding(String text) {
        String hash = generateContentHash(text);
        return generateEmbedding(text, hash);
    }
      /**
     * Generate embeddings for multiple texts asynchronously
     */
    public CompletableFuture<List<List<Double>>> generateEmbeddingsAsync(List<String> texts) {
        return CompletableFuture.supplyAsync(() -> {
            return texts.stream()
                    .map(this::generateEmbedding)
                    .collect(Collectors.toList());
        });
    }
    
    /**
     * Calculate cosine similarity between two embeddings
     */
    public double calculateCosineSimilarity(List<Double> embedding1, List<Double> embedding2) {
        if (embedding1.size() != embedding2.size()) {
            throw new IllegalArgumentException("Embeddings must have the same dimension");
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < embedding1.size(); i++) {
            dotProduct += embedding1.get(i) * embedding2.get(i);
            normA += Math.pow(embedding1.get(i), 2);
            normB += Math.pow(embedding2.get(i), 2);
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    /**
     * Generate a hash for text content (for caching and deduplication)
     */
    public String generateContentHash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available", e);
            return String.valueOf(text.hashCode());
        }
    }
}
