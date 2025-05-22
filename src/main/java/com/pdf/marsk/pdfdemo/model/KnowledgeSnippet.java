package com.pdf.marsk.pdfdemo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "knowledge_snippets")
public class KnowledgeSnippet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 512)
    private String originalPdfFilename;

    @Column(nullable = false, length = 1024)
    private String userQuery;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String extractedSnippet;

    // Optional: To store which page the snippet might have come from.
    // This might be hard to determine accurately from LLM output without further processing.
    private Integer sourcePage; 

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Constructors
    public KnowledgeSnippet() {
    }

    public KnowledgeSnippet(String originalPdfFilename, String userQuery, String extractedSnippet, Integer sourcePage) {
        this.originalPdfFilename = originalPdfFilename;
        this.userQuery = userQuery;
        this.extractedSnippet = extractedSnippet;
        this.sourcePage = sourcePage;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOriginalPdfFilename() {
        return originalPdfFilename;
    }

    public void setOriginalPdfFilename(String originalPdfFilename) {
        this.originalPdfFilename = originalPdfFilename;
    }

    public String getUserQuery() {
        return userQuery;
    }

    public void setUserQuery(String userQuery) {
        this.userQuery = userQuery;
    }

    public String getExtractedSnippet() {
        return extractedSnippet;
    }

    public void setExtractedSnippet(String extractedSnippet) {
        this.extractedSnippet = extractedSnippet;
    }

    public Integer getSourcePage() {
        return sourcePage;
    }

    public void setSourcePage(Integer sourcePage) {
        this.sourcePage = sourcePage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "KnowledgeSnippet{" +
               "id=" + id +
               ", originalPdfFilename='" + originalPdfFilename + '\'' +
               ", userQuery='" + userQuery + '\'' +
               ", extractedSnippet='" + (extractedSnippet != null ? extractedSnippet.substring(0, Math.min(extractedSnippet.length(), 50)) + "..." : "null") + '\'' +
               ", sourcePage=" + sourcePage +
               ", createdAt=" + createdAt +
               '}';
    }
}