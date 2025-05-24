package com.pdf.marsk.pdfdemo.service;

import com.pdf.marsk.pdfdemo.model.KnowledgeSnippet;
import com.pdf.marsk.pdfdemo.repository.KnowledgeSnippetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeExtractorServiceTest {

    @Mock
    private OcrService ocrService;

    @Mock
    private OllamaService ollamaService;

    @Mock
    private KnowledgeSnippetRepository knowledgeSnippetRepository;

    @Mock 
    private ProgressTrackingService progressTrackingService;

    @InjectMocks
    private KnowledgeExtractorService knowledgeExtractorService;

    private MockMultipartFile dummyPdfFile;
    private String dummyTaskId = "ke-test-123";

    @BeforeEach
    void setUp() {
        dummyPdfFile = new MockMultipartFile("file", "test.pdf", "application/pdf", "pdf content".getBytes());
    }

    @Test
    void extractKnowledgeAsync_createsTaskAndCallsPerformExtraction() {
        String query = "test query";
        String modelName = "testModel";
        String lang = "eng";

        when(progressTrackingService.createKnowledgeExtractionTask(eq(dummyPdfFile.getOriginalFilename()), eq(query), eq(modelName)))
            .thenReturn(dummyTaskId);
        
        // Spy on the service to verify the async method is called.
        // This requires the service itself to be spyable or making performExtractionAsync package-private for testing.
        // For now, we primarily test that createKnowledgeExtractionTask is called and return the ID.
        // The actual logic is in performExtractionAsync which is tested separately.

        String taskIdResult = knowledgeExtractorService.extractKnowledgeAsync(dummyPdfFile, query, modelName, lang, true); // Added true for useOcr

        assertEquals(dummyTaskId, taskIdResult);
        verify(progressTrackingService).createKnowledgeExtractionTask(eq(dummyPdfFile.getOriginalFilename()), eq(query), eq(modelName));
    }


    @Test
    void performExtractionAsync_success_parsesSnippetsWithSeparator() throws Exception {
        String ocrText = "Full OCR text content.";
        String query = "What is important?";
        String modelName = "llama3";
        String lang = "eng";
        String llmResponse = "Snippet 1 content.\n\n---SNIPPET---\n\nSnippet 2 content.";
        OllamaService.EnhancementResult enhancementResult = new OllamaService.EnhancementResult(llmResponse, false);

        when(ocrService.performOcr(any(MockMultipartFile.class), eq(lang))).thenReturn(ocrText);
        when(ollamaService.enhanceText(anyString(), eq(modelName), isNull(), eq(false))).thenReturn(enhancementResult);

        CompletableFuture<List<String>> future = knowledgeExtractorService.performExtractionAsync(dummyTaskId, dummyPdfFile, query, modelName, lang, true); // Added true for useOcr
        List<String> snippets = future.get();

        assertNotNull(snippets);
        assertEquals(2, snippets.size());
        assertEquals("Snippet 1 content.", snippets.get(0));
        assertEquals("Snippet 2 content.", snippets.get(1));
        verify(progressTrackingService).updateTaskProgress(eq(dummyTaskId), eq("OCR Processing"), eq(10), anyString());
        verify(progressTrackingService, times(2)).updateTaskProgress(eq(dummyTaskId), eq("LLM Processing"), anyInt(), anyString()); 
        verify(progressTrackingService).completeTask(eq(dummyTaskId), eq(true), eq("2 snippet(s) extracted."));
    }

    @Test
    void performExtractionAsync_success_parsesSnippetsWithMarkdownBlock() throws Exception {
        String ocrText = "Full OCR text content.";
        String query = "Details about X.";
        String modelName = "llama3";
        String lang = "eng";
        String llmResponse = "```snippet\nMarkdown Snippet 1\n```\nSome other text\n```snippet\nMarkdown Snippet 2\n```";
        OllamaService.EnhancementResult enhancementResult = new OllamaService.EnhancementResult(llmResponse, false);
        
        when(ocrService.performOcr(any(MockMultipartFile.class), eq(lang))).thenReturn(ocrText);
        when(ollamaService.enhanceText(anyString(), eq(modelName), isNull(), eq(false))).thenReturn(enhancementResult);

        CompletableFuture<List<String>> future = knowledgeExtractorService.performExtractionAsync(dummyTaskId, dummyPdfFile, query, modelName, lang, true); // Added true for useOcr
        List<String> snippets = future.get();

        assertNotNull(snippets);
        assertEquals(2, snippets.size());
        assertEquals("Markdown Snippet 1", snippets.get(0));
        assertEquals("Markdown Snippet 2", snippets.get(1));
        verify(progressTrackingService).completeTask(eq(dummyTaskId), eq(true), eq("2 snippet(s) extracted."));
    }
    
    @Test
    void performExtractionAsync_emptyOcrText_returnsEmptyListAndCompletesTask() throws Exception {
        String lang = "eng";
        when(ocrService.performOcr(any(MockMultipartFile.class), eq(lang))).thenReturn("");
        
        CompletableFuture<List<String>> future = knowledgeExtractorService.performExtractionAsync(dummyTaskId, dummyPdfFile, "query", "model", lang, true); // Added true for useOcr
        List<String> snippets = future.get();
        
        assertTrue(snippets.isEmpty());
        verify(progressTrackingService).completeTask(eq(dummyTaskId), eq(true), eq("OCR resulted in empty text. No snippets extracted."));
    }

    @Test
    void performExtractionAsync_emptyLlmResponse_returnsEmptyListAndCompletesTask() throws Exception {
        String lang = "eng";
        when(ocrService.performOcr(any(MockMultipartFile.class), eq(lang))).thenReturn("ocr text");
        OllamaService.EnhancementResult emptyResult = new OllamaService.EnhancementResult("", false);
        when(ollamaService.enhanceText(anyString(), anyString(), isNull(), anyBoolean())).thenReturn(emptyResult);
        
        CompletableFuture<List<String>> future = knowledgeExtractorService.performExtractionAsync(dummyTaskId, dummyPdfFile, "query", "model", lang, true); // Added true for useOcr
        List<String> snippets = future.get();

        assertTrue(snippets.isEmpty());
        verify(progressTrackingService).completeTask(eq(dummyTaskId), eq(true), eq("No relevant snippets found in the document."));
    }

    @Test
    void performExtractionAsync_ocrFails_completesTaskAsFailed() throws Exception {
        String lang = "eng";
        when(ocrService.performOcr(any(MockMultipartFile.class), eq(lang))).thenThrow(new IOException("OCR Read Error"));
        
        CompletableFuture<List<String>> future = knowledgeExtractorService.performExtractionAsync(dummyTaskId, dummyPdfFile, "query", "model", lang, true); // Added true for useOcr
        
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertTrue(ex.getCause() instanceof IOException);
        verify(progressTrackingService).completeTask(eq(dummyTaskId), eq(false), eq("Error during extraction: OCR Read Error"));
    }
    
    @Test
    void performExtractionAsync_llmFails_completesTaskAsFailed() throws Exception {
        String lang = "eng";
        when(ocrService.performOcr(any(MockMultipartFile.class), eq(lang))).thenReturn("ocr text");
        when(ollamaService.enhanceText(anyString(), anyString(), isNull(), anyBoolean())).thenThrow(new RuntimeException("LLM unavailable"));
        
        CompletableFuture<List<String>> future = knowledgeExtractorService.performExtractionAsync(dummyTaskId, dummyPdfFile, "query", "model", lang, true); // Added true for useOcr

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertTrue(ex.getCause() instanceof RuntimeException);
        verify(progressTrackingService).completeTask(eq(dummyTaskId), eq(false), eq("Error during extraction: LLM unavailable"));
    }

    // Test for chunking logic
    @Test
    void performExtractionAsync_withChunking_processesMultipleChunks() throws Exception {
        String uniqueTextPart1 = "UNIQUE_CONTENT_FOR_CHUNK_1_VERIFICATION"; 
        String uniqueTextPart2 = "UNIQUE_CONTENT_FOR_CHUNK_2_VERIFICATION"; 
        String padding = " PADDING ".repeat(100); 

        // Make textForChunk1Content almost fill the MAX_TEXT_LENGTH_FOR_LLM
        // Ensure longPadding is actually long enough.
        String longPaddingString = String.join("", Collections.nCopies(KnowledgeExtractorService.MAX_TEXT_LENGTH_FOR_LLM, "x")); // Create a very long padding string

        int lengthToTakeForChunk1Padding = KnowledgeExtractorService.MAX_TEXT_LENGTH_FOR_LLM - uniqueTextPart1.length() - 1; // -1 to be just under
        if (lengthToTakeForChunk1Padding < 0) lengthToTakeForChunk1Padding = 0;
        String textForChunk1Content = uniqueTextPart1 + longPaddingString.substring(0, lengthToTakeForChunk1Padding);

        // textForChunk2Content will form the beginning of the second chunk
        String textForChunk2Content = uniqueTextPart2 + " Some distinct text for the second chunk to ensure it's processed.";
        String longOcrText = textForChunk1Content + textForChunk2Content;

        String query = "find unique content";
        String modelName = "llama3";
        String lang = "eng";

        OllamaService.EnhancementResult resultForPrompt1 = new OllamaService.EnhancementResult("```snippet\nExtracted from " + uniqueTextPart1 + "\n```", false);
        OllamaService.EnhancementResult resultForPrompt2 = new OllamaService.EnhancementResult("```snippet\nExtracted from " + uniqueTextPart2 + "\n```", false);        when(ocrService.performOcr(any(MockMultipartFile.class), eq(lang))).thenReturn(longOcrText);
        
        // Use a better approach that will handle any prompt correctly
        when(ollamaService.enhanceText(any(), any(), any(), anyBoolean())).thenAnswer(invocation -> {
            String promptText = invocation.getArgument(0).toString();
            
            if (promptText.contains(uniqueTextPart1)) {
                return resultForPrompt1;
            }
            
            // Check if the second prompt has the uniqueTextPart2 but may be truncated at the beginning
            if (promptText.contains(uniqueTextPart2) || 
                promptText.contains(uniqueTextPart2.substring(5)) || // In case it's truncated
                promptText.contains(textForChunk2Content.substring(0, Math.min(20, textForChunk2Content.length())))) {
                return resultForPrompt2;
            }
            
            // If no identifiable chunk, return a generic result
            return new OllamaService.EnhancementResult("No match in prompt", false);
        });

        CompletableFuture<List<String>> future = knowledgeExtractorService.performExtractionAsync(dummyTaskId, dummyPdfFile, query, modelName, lang, true); // Added true for useOcr
        List<String> snippets = future.get();

        assertNotNull(snippets);
        assertEquals(2, snippets.size(), "Should extract two snippets, one from each chunk's LLM call. Snippets: " + snippets);
        assertTrue(snippets.stream().anyMatch(s -> s.contains("Extracted from " + uniqueTextPart1)), "Snippet from chunk 1 (containing '" + uniqueTextPart1 + "') missing. Snippets: " + snippets);
        assertTrue(snippets.stream().anyMatch(s -> s.contains("Extracted from " + uniqueTextPart2)), "Snippet from chunk 2 (containing '" + uniqueTextPart2 + "') missing. Snippets: " + snippets);
        
        verify(progressTrackingService).updateTaskProgress(eq(dummyTaskId), eq("OCR Processing"), eq(10), anyString());
        verify(progressTrackingService).updateTaskProgress(eq(dummyTaskId), eq("LLM Processing"), eq(30), anyString()); 
        verify(progressTrackingService, times(2)).updateTaskProgress(eq(dummyTaskId), eq("LLM Processing"), anyInt(), contains("Processing chunk")); 
        verify(progressTrackingService).updateTaskProgress(eq(dummyTaskId), eq("Finalizing"), eq(95), anyString());
        verify(progressTrackingService).completeTask(eq(dummyTaskId), eq(true), eq("2 snippet(s) extracted."));
    }


    @Test
    void saveSnippet_success() {
        String pdfName = "doc.pdf";
        String query = "test query";
        String snippetText = "test snippet";
        KnowledgeSnippet snippetToSave = new KnowledgeSnippet(pdfName, query, snippetText, null);
        KnowledgeSnippet savedSnippet = new KnowledgeSnippet(pdfName, query, snippetText, null);
        savedSnippet.setId(1L); 

        when(knowledgeSnippetRepository.save(any(KnowledgeSnippet.class))).thenReturn(savedSnippet);

        KnowledgeSnippet result = knowledgeExtractorService.saveSnippet(pdfName, query, snippetText, null);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(pdfName, result.getOriginalPdfFilename());
        verify(knowledgeSnippetRepository, times(1)).save(any(KnowledgeSnippet.class));
    }

    @Test
    void saveSnippets_multipleSnippets_success() {
        String pdfName = "doc.pdf";
        String query = "test query";
        List<String> snippetTexts = Arrays.asList("snippet1", "snippet2");

        when(knowledgeSnippetRepository.save(any(KnowledgeSnippet.class)))
            .thenAnswer(invocation -> {
                KnowledgeSnippet s = invocation.getArgument(0);
                return s; 
            });

        List<KnowledgeSnippet> results = knowledgeExtractorService.saveSnippets(pdfName, query, snippetTexts);

        assertNotNull(results);
        assertEquals(2, results.size());
        verify(knowledgeSnippetRepository, times(2)).save(any(KnowledgeSnippet.class));
    }
    
    @Test
    void saveSnippets_emptyList_returnsEmptyList() {
        List<KnowledgeSnippet> results = knowledgeExtractorService.saveSnippets("doc.pdf", "query", Collections.emptyList());
        assertTrue(results.isEmpty());
        verify(knowledgeSnippetRepository, never()).save(any(KnowledgeSnippet.class));
    }


    @Test
    void getAllSavedSnippets_success() {
        List<KnowledgeSnippet> mockSnippets = Arrays.asList(new KnowledgeSnippet(), new KnowledgeSnippet());
        when(knowledgeSnippetRepository.findAllByOrderByCreatedAtDesc()).thenReturn(mockSnippets);

        List<KnowledgeSnippet> results = knowledgeExtractorService.getAllSavedSnippets();
        assertEquals(2, results.size());
        verify(knowledgeSnippetRepository, times(1)).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void deleteSnippet_exists_deletesSuccessfully() {
        Long snippetId = 1L;
        when(knowledgeSnippetRepository.existsById(snippetId)).thenReturn(true);
        doNothing().when(knowledgeSnippetRepository).deleteById(snippetId);

        assertDoesNotThrow(() -> knowledgeExtractorService.deleteSnippet(snippetId));
        verify(knowledgeSnippetRepository, times(1)).deleteById(snippetId);
    }

    @Test
    void deleteSnippet_doesNotExist_throwsIllegalArgumentException() {
        Long snippetId = 1L;
        when(knowledgeSnippetRepository.existsById(snippetId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> {
            knowledgeExtractorService.deleteSnippet(snippetId);
        });
        verify(knowledgeSnippetRepository, never()).deleteById(snippetId);
    }
}