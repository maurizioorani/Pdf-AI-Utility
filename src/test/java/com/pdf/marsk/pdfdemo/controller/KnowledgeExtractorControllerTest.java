package com.pdf.marsk.pdfdemo.controller;

import com.pdf.marsk.pdfdemo.model.KnowledgeSnippet;
import com.pdf.marsk.pdfdemo.model.KnowledgeSnippet;
import com.pdf.marsk.pdfdemo.service.*; // Combined imports
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.empty;


@WebMvcTest(KnowledgeExtractorController.class)
class KnowledgeExtractorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeExtractorService knowledgeExtractorService;

    @MockBean
    private OllamaService ollamaService;

    @MockBean // Added
    private ProgressTrackingService progressTrackingService;

    @MockBean
    private SimpleLangChain4jRagService ragService; // Added MockBean for RAG service

    @Test
    void extractPage_shouldLoadPageWithModelsAndAvailableDocs() throws Exception {
        List<String> mockModels = Arrays.asList("model1", "model2");
        List<Object[]> mockDocSummaries = Arrays.asList(
            new Object[]{"doc1", "file1.pdf", 10L, java.time.LocalDateTime.now()},
            new Object[]{"doc2", "file2.pdf", 5L, java.time.LocalDateTime.now()}
        );

        when(ollamaService.getAvailableModels()).thenReturn(mockModels);
        when(ragService.getAvailableDocumentSummaries()).thenReturn(mockDocSummaries);
        // when(knowledgeExtractorService.getAllSavedSnippets()).thenReturn(Collections.emptyList()); // Old snippet logic

        mockMvc.perform(get("/extract"))
                .andExpect(status().isOk())
                .andExpect(view().name("extract"))
                .andExpect(model().attribute("availableModels", mockModels))
                .andExpect(model().attribute("availableDocuments", mockDocSummaries))
                .andExpect(model().attributeExists("chatHistory")) // Check for chatHistory
                .andExpect(model().attributeDoesNotExist("currentTaskId"));
    }
    
    @Test
    void extractPage_withTaskId_shouldSetCurrentTaskId() throws Exception {
        String taskId = "ke-123";
        when(ollamaService.getAvailableModels()).thenReturn(Arrays.asList("model1"));
        when(knowledgeExtractorService.getAllSavedSnippets()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/extract").param("taskId", taskId))
                .andExpect(status().isOk())
                .andExpect(view().name("extract"))
                .andExpect(model().attribute("currentTaskId", taskId));
    }


    @Test
    void handleKnowledgeExtraction_success_redirectsWithTaskId() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile("pdfFile", "test.pdf", "application/pdf", "content".getBytes());
        String query = "test query";
        String modelName = "model1";
        String ocrLanguage = "eng";
        String expectedTaskId = "ke-task-123";

        // Test with useOcr explicitly false (new default)
        // The 'query' is now a default hidden value in the form, but the controller still accepts it.
        // The 'useRag' is also a hidden input in the form, defaulting to true.
        // KnowledgeExtractorService.extractKnowledgeAsync doesn't take useRag, it's an internal detail or config.
        when(knowledgeExtractorService.extractKnowledgeAsync(any(MockMultipartFile.class), eq(query), eq(modelName), eq(ocrLanguage), eq(false)))
                .thenReturn(expectedTaskId);

        mockMvc.perform(multipart("/extract/process")
                        .file(pdfFile)
                        .param("query", query) // Keep for controller signature, though form sends default
                        .param("modelName", modelName)
                        .param("ocrLanguage", ocrLanguage)
                        .param("useOcr", "false")) // Explicitly set to false to test this path
                        // useRag is sent as "true" by the form's hidden input
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/extract?taskId=" + expectedTaskId))
                .andExpect(flash().attribute("infoMessage", "Knowledge extraction started (direct text extraction) with RAG enhancement. Task ID: " + expectedTaskId));
    }

    @Test
    void handleKnowledgeExtraction_emptyFile_redirectsWithError() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("pdfFile", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/extract/process")
                        .file(emptyFile)
                        .param("query", "query")
                        .param("modelName", "model"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/extract"))
                .andExpect(flash().attribute("errorMessage", "Please select a PDF file."));
    }
    
    @Test
    void handleKnowledgeExtraction_emptyQuery_redirectsWithError() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile("pdfFile", "test.pdf", "application/pdf", "content".getBytes());
        
        mockMvc.perform(multipart("/extract/process")
                        .file(pdfFile)
                        .param("query", "")
                        .param("modelName", "model"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/extract"))
                .andExpect(flash().attribute("errorMessage", "Please enter a query."));
    }
    
    @Test
    void handleKnowledgeExtraction_noModelSelected_redirectsWithError() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile("pdfFile", "test.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/extract/process")
                        .file(pdfFile)
                        .param("query", "a query")
                        .param("modelName", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/extract"))
                .andExpect(flash().attribute("errorMessage", "Please select an LLM model."));
    }


    @Test
    void handleKnowledgeExtraction_serviceThrowsIllegalArgumentException_redirectsWithError() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile("pdfFile", "test.pdf", "application/pdf", "content".getBytes());
        String query = "test query";
        String modelName = "model1";

        // Test with useOcr explicitly false
        when(knowledgeExtractorService.extractKnowledgeAsync(any(MockMultipartFile.class), eq(query), eq(modelName), anyString(), eq(false)))
                .thenThrow(new IllegalArgumentException("Bad argument"));

        mockMvc.perform(multipart("/extract/process")
                        .file(pdfFile)
                        .param("query", query)
                        .param("modelName", modelName)
                        .param("useOcr", "false")) // Explicitly set to false
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/extract"))
                .andExpect(flash().attribute("errorMessage", "Bad argument"));
    }
    
    @Test
    void getExtractionProgress_taskFound_returnsProgress() throws Exception {
        String taskId = "ke-123";
        // Use a real object instead of a mock for Jackson serialization
        KnowledgeExtractionProgressInfo realProgress = new KnowledgeExtractionProgressInfo(taskId, "test.pdf", "test query", "test-model");
        realProgress.setProgressPercent(50);
        realProgress.setMessage("Processing...");
        realProgress.setCurrentStage("LLM Processing");

        when(progressTrackingService.getProgress(taskId)).thenReturn(realProgress);

        mockMvc.perform(get("/extract/progress/" + taskId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.taskId").value(taskId))
                .andExpect(jsonPath("$.progressPercent").value(50))
                .andExpect(jsonPath("$.message").value("Processing..."))
                .andExpect(jsonPath("$.currentStage").value("LLM Processing"));
    }

    @Test
    void getExtractionProgress_taskNotFound_returnsNotFound() throws Exception {
        String taskId = "ke-unknown";
        when(progressTrackingService.getProgress(taskId)).thenReturn(null);

        mockMvc.perform(get("/extract/progress/" + taskId))
                .andExpect(status().isNotFound());
    }


    @Test
    void saveSelectedSnippets_success() throws Exception {
        List<String> snippetsToSave = Arrays.asList("saved snippet 1", "saved snippet 2");
        when(knowledgeExtractorService.saveSnippets(eq("test.pdf"), eq("query"), eq(snippetsToSave)))
                .thenReturn(Arrays.asList(new KnowledgeSnippet(), new KnowledgeSnippet())); // Dummy saved snippets

        mockMvc.perform(post("/extract/save")
                        .param("originalPdfFilename", "test.pdf")
                        .param("userQuery", "query")
                        .param("snippetsToSave", "saved snippet 1", "saved snippet 2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/extract"))
                .andExpect(flash().attribute("successMessage", "Successfully saved 2 snippet(s)."));
    }

    @Test
    void saveSelectedSnippets_noSnippetsSelected_showsError() throws Exception {
        mockMvc.perform(post("/extract/save")
                        .param("originalPdfFilename", "test.pdf")
                        .param("userQuery", "query")) // No snippetsToSave param
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/extract"))
                .andExpect(flash().attribute("errorMessage", "No snippets selected to save."));
    }
    
    @Test
    void saveSelectedSnippets_serviceThrowsException_showsError() throws Exception {
        List<String> snippetsToSave = Arrays.asList("snippet1");
        when(knowledgeExtractorService.saveSnippets(anyString(), anyString(), anyList()))
            .thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(post("/extract/save")
                        .param("originalPdfFilename", "test.pdf")
                        .param("userQuery", "query")
                        .param("snippetsToSave", "snippet1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/extract"))
                .andExpect(flash().attribute("errorMessage", "An error occurred while saving snippets: DB error"));
    }


    @Test
    void deleteSnippet_success() throws Exception {
        Long snippetId = 1L;
        doNothing().when(knowledgeExtractorService).deleteSnippet(snippetId);

        mockMvc.perform(post("/extract/snippets/delete/" + snippetId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/extract"))
                .andExpect(flash().attribute("successMessage", "Snippet deleted successfully."));
    }

    @Test
    void deleteSnippet_notFound_showsError() throws Exception {
        Long snippetId = 99L;
        doThrow(new IllegalArgumentException("Snippet not found")).when(knowledgeExtractorService).deleteSnippet(snippetId);

        mockMvc.perform(post("/extract/snippets/delete/" + snippetId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/extract"))
                .andExpect(flash().attribute("errorMessage", "Snippet not found"));
    }

    // --- Tests for new Q&A and Document Management Endpoints ---

    @Test
    void askQuestion_success_returnsAnswer() throws Exception {
        String question = "What is the capital of France?";
        String expectedAnswer = "The capital of France is Paris.";
        String modelName = "test-model";

        when(ragService.answerQuery(eq(question), eq(modelName))).thenReturn(expectedAnswer);
        when(ollamaService.getAvailableModels()).thenReturn(Collections.singletonList(modelName)); // Ensure model is "available" for default selection logic

        mockMvc.perform(post("/extract/ask")
                        .param("question", question)
                        .param("modelName", modelName)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.question").value(question))
                .andExpect(jsonPath("$.answer").value(expectedAnswer));
    }

    @Test
    void askQuestion_emptyQuestion_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/extract/ask")
                        .param("question", "")
                        .param("modelName", "test-model")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Question cannot be empty."));
    }
    
    @Test
    void askQuestion_defaultModelUsedWhenNotProvided() throws Exception {
        String question = "What is AI?";
        String expectedAnswer = "AI is Artificial Intelligence.";
        String defaultModel = "llama3"; // As per controller logic

        when(ollamaService.getAvailableModels()).thenReturn(Collections.singletonList(defaultModel));
        when(ragService.answerQuery(eq(question), eq(defaultModel))).thenReturn(expectedAnswer);

        mockMvc.perform(post("/extract/ask")
                        .param("question", question)
                        // No modelName param
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(expectedAnswer));
    }

    @Test
    void askQuestion_serviceThrowsException_returnsInternalServerError() throws Exception {
        String question = "Tell me a secret.";
        String modelName = "secret-model";

        when(ollamaService.getAvailableModels()).thenReturn(Collections.singletonList(modelName));
        when(ragService.answerQuery(eq(question), eq(modelName))).thenThrow(new RuntimeException("LLM unavailable"));

        mockMvc.perform(post("/extract/ask")
                        .param("question", question)
                        .param("modelName", modelName)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Error processing your question: LLM unavailable"));
    }

    @Test
    void deleteDocument_success_redirectsWithSuccessMessage() throws Exception {
        String documentId = "doc-to-delete-123";
        doNothing().when(ragService).deleteDocument(documentId);

        mockMvc.perform(post("/extract/documents/delete/" + documentId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/extract"))
                .andExpect(flash().attribute("successMessage", "Document with ID '" + documentId + "' and its chunks have been deleted from the repository."));

        verify(ragService, times(1)).deleteDocument(documentId);
    }

    @Test
    void deleteDocument_serviceThrowsException_redirectsWithErrorMessage() throws Exception {
        String documentId = "doc-fail-delete-456";
        doThrow(new RuntimeException("DB connection failed")).when(ragService).deleteDocument(documentId);

        mockMvc.perform(post("/extract/documents/delete/" + documentId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/extract"))
                .andExpect(flash().attribute("errorMessage", "Error deleting document: DB connection failed"));
    }

    @Test
    void deleteAllDocuments_success_redirectsWithSuccessMessage() throws Exception {
        doNothing().when(ragService).deleteAllDocuments();

        mockMvc.perform(post("/extract/documents/delete-all"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/extract"))
                .andExpect(flash().attribute("successMessage", "All documents and their chunks have been deleted from the repository."));
        
        verify(ragService, times(1)).deleteAllDocuments();
    }

    @Test
    void deleteAllDocuments_serviceThrowsException_redirectsWithErrorMessage() throws Exception {
        doThrow(new RuntimeException("Catastrophic failure")).when(ragService).deleteAllDocuments();

        mockMvc.perform(post("/extract/documents/delete-all"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/extract"))
                .andExpect(flash().attribute("errorMessage", "Error deleting all documents: Catastrophic failure"));
    }
}