package com.pdf.marsk.pdfdemo.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for LangChain4j components
 */
@Configuration
public class LangChain4jConfig {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    /**
     * Creates the embedding model for text vectorization
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }    /**
     * Creates the PgVector embedding store for PostgreSQL profile
     */
    @Bean
    @Profile("postgres")
    public EmbeddingStore<TextSegment> pgVectorEmbeddingStore() {
        return PgVectorEmbeddingStore.builder()
                .host(extractHostFromUrl(datasourceUrl))
                .port(extractPortFromUrl(datasourceUrl))
                .database(extractDatabaseFromUrl(datasourceUrl))
                .user(datasourceUsername)
                .password(datasourcePassword)
                .table("embeddings")
                .dimension(384) // AllMiniLmL6V2 produces 384-dimensional embeddings
                .build();
    }

    /**
     * Creates an in-memory embedding store for dev/test profiles
     */
    @Bean
    @Profile({"dev", "test"})
    public EmbeddingStore<TextSegment> inMemoryEmbeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    /**
     * Extract host from JDBC URL
     */
    private String extractHostFromUrl(String url) {
        // jdbc:postgresql://localhost:5432/pdfai -> localhost
        String[] parts = url.split("//")[1].split(":");
        return parts[0];
    }

    /**
     * Extract port from JDBC URL
     */
    private int extractPortFromUrl(String url) {
        // jdbc:postgresql://localhost:5432/pdfai -> 5432
        try {
            String[] parts = url.split("//")[1].split(":");
            if (parts.length > 1) {
                String portPart = parts[1].split("/")[0];
                return Integer.parseInt(portPart);
            }
        } catch (Exception e) {
            // Fallback to default PostgreSQL port
        }
        return 5432;
    }

    /**
     * Extract database name from JDBC URL
     */
    private String extractDatabaseFromUrl(String url) {
        // jdbc:postgresql://localhost:5432/pdfai -> pdfai
        try {
            String[] parts = url.split("/");
            if (parts.length > 0) {
                String dbPart = parts[parts.length - 1];
                // Remove any query parameters
                return dbPart.split("\\?")[0];
            }
        } catch (Exception e) {
            // Fallback
        }
        return "pdfai";
    }
}
