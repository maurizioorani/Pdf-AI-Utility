package com.pdf.marsk.pdfdemo.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caching configuration for improved performance
 * Uses Caffeine for in-memory caching of embeddings and document checks
 */
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Configure cache for embeddings (large cache, long expiration)
        cacheManager.registerCustomCache("embeddings", 
            Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .recordStats()
                .build());
        
        // Configure cache for document existence checks
        cacheManager.registerCustomCache("documentExists",
            Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .recordStats()
                .build());
        
        // Configure cache for OCR results
        cacheManager.registerCustomCache("ocrResults",
            Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(6, TimeUnit.HOURS)
                .recordStats()
                .build());
        
        // Configure cache for LLM responses
        cacheManager.registerCustomCache("llmResponses",
            Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(2, TimeUnit.HOURS)
                .recordStats()
                .build());
        
        return cacheManager;
    }
}
