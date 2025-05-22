package com.pdf.marsk.pdfdemo.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "ocr_documents")
public class OcrTextDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @NotBlank(message = "Original filename cannot be blank")
    private String originalFilename;

    @Lob // For potentially large text content
    @Column(nullable = false, columnDefinition = "TEXT")
    @NotBlank(message = "Extracted text cannot be blank")
    private String extractedText;
    
    @Lob // For potentially large enhanced text content
    @Column(columnDefinition = "TEXT")
    private String enhancedText;
    
    @Column
    private Boolean isEnhanced = false;
    
    @Column
    private String enhancementModel;
    
    @Column
    private String documentType;

    @Column(nullable = false)
    private String languageUsed; // e.g., "eng", "ita"

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Constructors
    public OcrTextDocument() {
    }

    public OcrTextDocument(String originalFilename, String extractedText, String languageUsed) {
        this.originalFilename = originalFilename;
        this.extractedText = extractedText;
        this.languageUsed = languageUsed;
    }
    
    public OcrTextDocument(String originalFilename, String extractedText, String enhancedText, 
                          String languageUsed, String enhancementModel, String documentType) {
        this.originalFilename = originalFilename;
        this.extractedText = extractedText;
        this.enhancedText = enhancedText;
        this.languageUsed = languageUsed;
        this.enhancementModel = enhancementModel;
        this.documentType = documentType;
        this.isEnhanced = enhancedText != null && !enhancedText.isEmpty();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }
    
    public String getEnhancedText() {
        return enhancedText;
    }

    public void setEnhancedText(String enhancedText) {
        this.enhancedText = enhancedText;
    }
    
    public Boolean getIsEnhanced() {
        return isEnhanced;
    }

    public void setIsEnhanced(Boolean isEnhanced) {
        this.isEnhanced = isEnhanced;
    }
    
    public String getEnhancementModel() {
        return enhancementModel;
    }

    public void setEnhancementModel(String enhancementModel) {
        this.enhancementModel = enhancementModel;
    }
    
    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getLanguageUsed() {
        return languageUsed;
    }

    public void setLanguageUsed(String languageUsed) {
        this.languageUsed = languageUsed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}