package com.pdf.marsk.pdfdemo.controller;

import com.pdf.marsk.pdfdemo.config.RagConfigurationProperties;
import com.pdf.marsk.pdfdemo.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KnowledgeExtractorController.class)
class KnowledgeExtractorControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private KnowledgeExtractorService knowledgeExtractorService; // This is now the RAG service

    @MockBean
    private OllamaService ollamaService; // Kept if needed for future UI elements
      @MockBean
    private RagConfigurationProperties ragConfig;

    @MockBean
    private ProgressTrackingService progressTrackingService;

    @BeforeEach
    void setUp() {
        // Common mock setups for ragConfig
        when(ragConfig.isEnabled()).thenReturn(true);
        when(ragConfig.getEmbeddingModelName()).thenReturn("test-embedding-model");
        when(ragConfig.getChatModelName()).thenReturn("test-chat-model");
        
        // Mock OllamaService methods
        when(ollamaService.getAvailableModels()).thenReturn(Arrays.asList("model1", "model2"));
        
        // Mock KnowledgeExtractorService methods
        when(knowledgeExtractorService.getLoadedDocumentSummaries()).thenReturn(Collections.emptyList());
    }

    @Test
    void ragPage_shouldLoadPageSuccessfully() throws Exception {
        // The controller currently does not add 'availableModels' or 'availableDocuments'
        // when(ollamaService.getAvailableModels()).thenReturn(Arrays.asList("model1", "model2"));

        mockMvc.perform(get("/rag"))
                .andExpect(status().isOk())
                .andExpect(view().name("extract"))
                .andExpect(model().attributeExists("chatHistory"))
                .andExpect(model().attribute("ragEnabled", true))
                .andExpect(model().attribute("embeddingModel", "test-embedding-model"))
                .andExpect(model().attribute("chatModel", "test-chat-model"));
    }
    
    @Test
    void askQuestion_success_redirectsWithAnswer() throws Exception {
        String question = "What is the capital of France?";
        String expectedAnswer = "The capital of France is Paris.";

        when(knowledgeExtractorService.chat(eq(question))).thenReturn(expectedAnswer);        mockMvc.perform(post("/rag/ask")
                        .param("question", question)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(expectedAnswer));
    }    @Test
    void askQuestion_whenRagDisabled_returnsErrorJson() throws Exception {
        when(ragConfig.isEnabled()).thenReturn(false); // Override setup for this test
        String question = "Does this work?";

        mockMvc.perform(post("/rag/ask")
                        .param("question", question)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("RAG system is not enabled."));

        verify(knowledgeExtractorService, never()).chat(anyString());
    }
    
    @Test
    void askQuestion_serviceThrowsException_returnsErrorJson() throws Exception {
        String question = "Tell me a secret.";
        String errorMessage = "LLM unavailable";

        when(knowledgeExtractorService.chat(eq(question))).thenThrow(new RuntimeException(errorMessage));

        mockMvc.perform(post("/rag/ask")
                        .param("question", question)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("Error processing your question: " + errorMessage));
    }
}