package com.pdf.marsk.pdfdemo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentImprovementServiceTest {

    @Mock
    private OllamaService ollamaService;

    @InjectMocks
    private ContentImprovementService contentImprovementService;

    @Test
    void testImproveContent_Success() {
        // Given
        String originalContent = "<p>This is a test content that need improvement.</p>";
        String improvedContent = "<p>This is excellent test content that has been significantly improved.</p>";
        String modelName = "llama3";
        
        when(ollamaService.generateResponse(anyString(), eq(modelName)))
            .thenReturn(improvedContent);

        // When
        String result = contentImprovementService.improveContent(originalContent, modelName);

        // Then
        assertNotNull(result);
        assertEquals(improvedContent, result);
    }

    @Test
    void testImproveContent_EmptyContent() {
        // Given
        String emptyContent = "";
        String modelName = "llama3";

        // When
        String result = contentImprovementService.improveContent(emptyContent, modelName);

        // Then
        assertEquals("", result);
    }

    @Test
    void testImproveContent_NullContent() {
        // Given
        String nullContent = null;
        String modelName = "llama3";

        // When
        String result = contentImprovementService.improveContent(nullContent, modelName);

        // Then
        assertNull(result);
    }

    @Test
    void testImproveContent_WithException() {
        // Given
        String originalContent = "<p>Test content</p>";
        String modelName = "llama3";
        
        when(ollamaService.generateResponse(anyString(), eq(modelName)))
            .thenThrow(new RuntimeException("Ollama connection failed"));

        // When
        String result = contentImprovementService.improveContent(originalContent, modelName);

        // Then
        assertEquals(originalContent, result); // Should return original content on error
    }

    @Test
    void testCleanupImprovedContent_RemoveCodeBlocks() {
        // Given
        String contentWithCodeBlocks = "```html\n<p>Improved content</p>\n```";
        String modelName = "llama3";
        
        when(ollamaService.generateResponse(anyString(), eq(modelName)))
            .thenReturn(contentWithCodeBlocks);

        // When
        String result = contentImprovementService.improveContent("<p>Original</p>", modelName);

        // Then
        assertEquals("<p>Improved content</p>", result);
    }
}
