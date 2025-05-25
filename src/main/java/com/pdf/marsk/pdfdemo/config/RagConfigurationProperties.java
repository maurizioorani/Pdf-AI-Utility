package com.pdf.marsk.pdfdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for RAG (Retrieval Augmented Generation) functionality.
 */
@ConfigurationProperties(prefix = "rag")
public class RagConfigurationProperties {
    
    private final boolean enabled;
    private final Context context;
    private final Chunking chunking;
    private final Embedding embedding;
    private final double similarityThreshold;
    private final int maxContextSize;
    private final int contextWindowOverlap;
    private final String documentsPath;
    private final String chatModelName; // Added chatModelName field

    public RagConfigurationProperties(boolean enabled, Context context, Chunking chunking, 
                                    Embedding embedding, double similarityThreshold,
                                    int maxContextSize, int contextWindowOverlap,
                                    String documentsPath,
                                    String chatModelName) { // Added chatModelName to constructor
        this.enabled = enabled;
        this.context = context != null ? context : new Context(5);
        this.chunking = chunking != null ? chunking : new Chunking(200);
        this.embedding = embedding != null ? embedding : new Embedding("all-MiniLM-L6-v2", 384); // Default embedding model
        this.similarityThreshold = similarityThreshold > 0 ? similarityThreshold : 0.3;
        this.maxContextSize = maxContextSize > 0 ? maxContextSize : 2000;
        this.contextWindowOverlap = contextWindowOverlap >= 0 ? contextWindowOverlap : 200;
        this.documentsPath = documentsPath;
        this.chatModelName = chatModelName != null && !chatModelName.isEmpty() ? chatModelName : "gpt-4o-mini"; // Initialize chatModelName with a default
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public Context getContext() {
        return context;
    }
    
    public Chunking getChunking() {
        return chunking;
    }
    
    public Embedding getEmbedding() {
        return embedding;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }
    
    public int getMaxContextSize() {
        return maxContextSize;
    }
    
    public int getContextWindowOverlap() {
        return contextWindowOverlap;
    }

    public String getDocumentsPath() {
        return documentsPath;
    }

    public String getChatModelName() { // Added getter for chatModelName
        return chatModelName;
    }

    public String getEmbeddingModelName() { // Convenience getter for embedding model name
        return (embedding != null && embedding.getModelName() != null) ? embedding.getModelName() : "default-embedding-model";
    }
    
    public static class Context {
        private final int maxChunks;
        
        public Context(int maxChunks) {
            this.maxChunks = maxChunks > 0 ? maxChunks : 20;
        }
        
        public int getMaxChunks() {
            return maxChunks;
        }
    }
    
    public static class Chunking {
        private final int overlapSize;
        
        public Chunking(int overlapSize) {
            this.overlapSize = overlapSize >= 0 ? overlapSize : 200;
        }
        
        public int getOverlapSize() {
            return overlapSize;
        }
    }
    
    public static class Embedding {
        private final String modelName;
        private final int dimensions;
        
        public Embedding(String modelName, int dimensions) {
            this.modelName = (modelName != null && !modelName.isEmpty()) ? modelName : "all-MiniLM-L6-v2";
            this.dimensions = dimensions > 0 ? dimensions : 384;
        }
        
        public String getModelName() {
            return modelName;
        }
        
        public int getDimensions() {
            return dimensions;
        }
    }
}
