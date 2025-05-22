package com.pdf.marsk.pdfdemo.service;

import com.pdf.marsk.pdfdemo.model.KnowledgeSnippet;
import com.pdf.marsk.pdfdemo.repository.KnowledgeSnippetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class KnowledgeExtractorService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeExtractorService.class);

    private final OcrService ocrService;
    private final OllamaService ollamaService;
    private final KnowledgeSnippetRepository knowledgeSnippetRepository;
    private final ProgressTrackingService progressTrackingService; // Added

    // Define a pattern to extract snippets if LLM returns them in a structured way
    private static final Pattern SNIPPET_BLOCK_PATTERN = Pattern.compile("```snippet\\s*\\n(.*?)\\n```", Pattern.DOTALL);
    private static final String DEFAULT_SNIPPET_SEPARATOR = "\n\n---SNIPPET---\n\n";
    static final int MAX_TEXT_LENGTH_FOR_LLM = 15000; // Example: Max characters to send to LLM in one go (package-private)


    @Autowired
    public KnowledgeExtractorService(OcrService ocrService,
                                     OllamaService ollamaService,
                                     KnowledgeSnippetRepository knowledgeSnippetRepository,
                                     ProgressTrackingService progressTrackingService) { // Added
        this.ocrService = ocrService;
        this.ollamaService = ollamaService;
        this.knowledgeSnippetRepository = knowledgeSnippetRepository;
        this.progressTrackingService = progressTrackingService; // Added
    }

    /**
     * Initiates asynchronous knowledge extraction.
     * @return Task ID for progress tracking.
     */
    public String extractKnowledgeAsync(MultipartFile pdfFile, String query, String modelName, String ocrLanguage) {
        if (pdfFile == null || pdfFile.isEmpty()) {
            throw new IllegalArgumentException("PDF file must be provided.");
        }
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Query must be provided.");
        }
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IllegalArgumentException("LLM model name must be provided.");
        }
        String lang = (ocrLanguage == null || ocrLanguage.trim().isEmpty()) ? "eng" : ocrLanguage;

        String taskId = progressTrackingService.createKnowledgeExtractionTask(pdfFile.getOriginalFilename(), query, modelName);
        performExtractionAsync(taskId, pdfFile, query, modelName, lang);
        return taskId;
    }

    @Async // Marks this method to be executed in a separate thread
    public CompletableFuture<List<String>> performExtractionAsync(String taskId, MultipartFile pdfFile, String query, String modelName, String ocrLanguage) {
        List<String> finalSnippets = new ArrayList<>();
        try {
            progressTrackingService.updateTaskProgress(taskId, "OCR Processing", 10, "Starting OCR...");
            String fullPdfText = ocrService.performOcr(pdfFile, ocrLanguage); // Assuming performOcr is synchronous for now
            
            if (fullPdfText == null || fullPdfText.trim().isEmpty()) {
                logger.warn("OCR resulted in empty text for PDF: {}", pdfFile.getOriginalFilename());
                progressTrackingService.completeTask(taskId, true, "OCR resulted in empty text. No snippets extracted.");
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
            progressTrackingService.updateTaskProgress(taskId, "LLM Processing", 30, "OCR complete. Preparing for LLM.");

            // Chunking logic for large text
            List<String> textChunks = splitTextIntoChunks(fullPdfText, MAX_TEXT_LENGTH_FOR_LLM);
            int totalChunks = textChunks.size();
            logger.info("Split OCR text into {} chunks for LLM processing.", totalChunks);

            for (int i = 0; i < totalChunks; i++) {
                String chunk = textChunks.get(i);
                String chunkMessage = String.format("Processing chunk %d of %d with LLM...", i + 1, totalChunks);
                int currentProgress = 30 + (int) (((float) (i + 1) / totalChunks) * 60); // LLM processing takes 60% of progress
                progressTrackingService.updateTaskProgress(taskId, "LLM Processing", currentProgress, chunkMessage);

                String prompt = String.format(
                    "From the following document text segment, please extract all distinct text segments or snippets " +
                    "that are directly relevant to the query: \"%s\". " +
                    "Each extracted snippet should be a contiguous block of text from the document. " +
                    "Present each snippet clearly separated. For example, you can use '---SNIPPET---' as a separator between snippets, " +
                    "or enclose each snippet in its own markdown code block like ```snippet\\n[SNIPPET_TEXT]\\n```. " +
                    "Avoid summarization; extract verbatim segments. If no relevant snippets are found in this segment, respond with \"No relevant snippets found in this segment.\".\n\n" +
                    "DOCUMENT TEXT SEGMENT:\n%s",
                    query, chunk
                );

                OllamaService.EnhancementResult llmResult = ollamaService.enhanceText(prompt, modelName, null, false); // Chunking handled here, so pass false to ollamaService
                String llmResponse = llmResult.getEnhancedText();

                if (llmResponse != null && !llmResponse.trim().isEmpty() && !llmResponse.contains("No relevant snippets found in this segment.")) {
                    finalSnippets.addAll(parseSnippetsFromLlmResponse(llmResponse));
                }
            }
            
            progressTrackingService.updateTaskProgress(taskId, "Finalizing", 95, "Aggregating results.");
            if (finalSnippets.isEmpty()) {
                progressTrackingService.completeTask(taskId, true, "No relevant snippets found in the document.");
            } else {
                // For simplicity, storing the result message as "Snippets extracted"
                // In a real app, you might store the snippets themselves or a reference.
                progressTrackingService.completeTask(taskId, true, finalSnippets.size() + " snippet(s) extracted.");
            }
            return CompletableFuture.completedFuture(finalSnippets);

        } catch (Exception e) {
            logger.error("Error during asynchronous knowledge extraction for task {}: {}", taskId, e.getMessage(), e);
            progressTrackingService.completeTask(taskId, false, "Error during extraction: " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private List<String> splitTextIntoChunks(String text, int maxLength) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        for (int i = 0; i < text.length(); i += maxLength) {
            String chunk = text.substring(i, Math.min(text.length(), i + maxLength));
            // Add debug logging to help diagnose issues
            logger.debug("Created chunk {} with first 50 chars: {}", chunks.size() + 1, 
                     chunk.substring(0, Math.min(50, chunk.length())));
            chunks.add(chunk);
        }
        return chunks;
    }


    private List<String> parseSnippetsFromLlmResponse(String llmResponse) {
        List<String> snippets = new ArrayList<>();

        // Try parsing with markdown code block pattern first
        Matcher matcher = SNIPPET_BLOCK_PATTERN.matcher(llmResponse);
        while (matcher.find()) {
            String snippet = matcher.group(1).trim();
            if (!snippet.isEmpty()) {
                snippets.add(snippet);
            }
        }

        // If no snippets found with code blocks, try splitting by the custom separator
        if (snippets.isEmpty()) {
            String[] parts = llmResponse.split(Pattern.quote(DEFAULT_SNIPPET_SEPARATOR));
            for (String part : parts) {
                String snippet = part.trim();
                // Further clean-up: remove any "Snippet X:" or "Snippet X." type prefixes if LLM adds them.
                snippet = snippet.replaceAll("(?i)^snippet\\s*\\d+\\s*[:.-]\\s*", "").trim();
                if (!snippet.isEmpty()) {
                    snippets.add(snippet);
                }
            }
        }
        
        if (snippets.isEmpty() && !llmResponse.trim().isEmpty()) {
            // If still no snippets and response is not empty, consider the whole response as one snippet (fallback)
            // or apply more sophisticated parsing. For now, let's assume it might be a single block.
            logger.warn("Could not parse distinct snippets using defined patterns. Treating response as a single block or no relevant snippets found.");
            // We could add the whole llmResponse.trim() here if it's likely a single snippet without markers.
            // However, this might be too broad. Let's be conservative.
            // If the prompt is followed, there should be separators or it means no relevant snippets.
        }

        return snippets.stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    // --- Methods for Phase 2 (Saving Snippets) ---

    public KnowledgeSnippet saveSnippet(String originalPdfFilename, String userQuery, String extractedSnippet, Integer sourcePage) {
        KnowledgeSnippet snippet = new KnowledgeSnippet(originalPdfFilename, userQuery, extractedSnippet, sourcePage);
        return knowledgeSnippetRepository.save(snippet);
    }
    
    public List<KnowledgeSnippet> saveSnippets(String originalPdfFilename, String userQuery, List<String> extractedSnippets) {
        if (extractedSnippets == null || extractedSnippets.isEmpty()) {
            return Collections.emptyList();
        }
        List<KnowledgeSnippet> savedSnippets = new ArrayList<>();
        for (String snippetText : extractedSnippets) {
            if (snippetText != null && !snippetText.trim().isEmpty()) {
                // Source page is null for now as we don't determine it yet
                savedSnippets.add(saveSnippet(originalPdfFilename, userQuery, snippetText.trim(), null));
            }
        }
        logger.info("Saved {} snippets for PDF: {} with query: '{}'", savedSnippets.size(), originalPdfFilename, userQuery);
        return savedSnippets;
    }

    public List<KnowledgeSnippet> getAllSavedSnippets() {
        return knowledgeSnippetRepository.findAllByOrderByCreatedAtDesc();
    }

    public void deleteSnippet(Long snippetId) {
        if (!knowledgeSnippetRepository.existsById(snippetId)) {
            throw new IllegalArgumentException("Snippet with ID " + snippetId + " not found.");
        }
        knowledgeSnippetRepository.deleteById(snippetId);
        logger.info("Deleted knowledge snippet with ID: {}", snippetId);
    }
}