package com.pdf.marsk.pdfdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for document processing functionality.
 */
@ConfigurationProperties(prefix = "document.processing")
public class DocumentProcessingProperties {
    
    private final int chunkSize;
    private final int chunkOverlap;
    private final int maxParallelJobs;
    
    public DocumentProcessingProperties(int chunkSize, int chunkOverlap, int maxParallelJobs) {
        this.chunkSize = chunkSize > 0 ? chunkSize : 1000;
        this.chunkOverlap = chunkOverlap >= 0 ? chunkOverlap : 200;
        this.maxParallelJobs = maxParallelJobs > 0 ? maxParallelJobs : 4;
    }
    
    public int getChunkSize() {
        return chunkSize;
    }
    
    public int getChunkOverlap() {
        return chunkOverlap;
    }
    
    public int getMaxParallelJobs() {
        return maxParallelJobs;
    }
}
