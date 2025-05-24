package com.pdf.marsk.pdfdemo.service;

import com.pdf.marsk.pdfdemo.config.RagConfigurationProperties;
import com.pdf.marsk.pdfdemo.config.DocumentProcessingProperties;
import com.pdf.marsk.pdfdemo.model.KnowledgeSnippet;
import com.pdf.marsk.pdfdemo.repository.KnowledgeSnippetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class KnowledgeExtractorService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeExtractorService.class);    private final OcrService ocrService;
    private final OllamaService ollamaService;
    private final KnowledgeSnippetRepository knowledgeSnippetRepository;
    private final ProgressTrackingService progressTrackingService;
    private final SimpleLangChain4jRagService ragService;
    private final EmbeddingService embeddingService;
    private final RagConfigurationProperties ragConfig;
    private final DocumentProcessingProperties documentConfig;

    // Define a pattern to extract snippets if LLM returns them in a structured way
    private static final Pattern SNIPPET_BLOCK_PATTERN = Pattern.compile("```snippet\\s*\\n(.*?)\\n```", Pattern.DOTALL);    private static final String DEFAULT_SNIPPET_SEPARATOR = "\n\n---SNIPPET---\n\n";
    static final int MAX_TEXT_LENGTH_FOR_LLM = 15000; // Example: Max characters to send to LLM in one go (package-private)
      public KnowledgeExtractorService(OcrService ocrService,
                                     OllamaService ollamaService,
                                     KnowledgeSnippetRepository knowledgeSnippetRepository,
                                     ProgressTrackingService progressTrackingService,
                                     SimpleLangChain4jRagService ragService,
                                     EmbeddingService embeddingService,
                                     RagConfigurationProperties ragConfig,
                                     DocumentProcessingProperties documentConfig) {
        this.ocrService = ocrService;
        this.ollamaService = ollamaService;
        this.knowledgeSnippetRepository = knowledgeSnippetRepository;
        this.progressTrackingService = progressTrackingService;
        this.ragService = ragService;
        this.embeddingService = embeddingService;
        this.ragConfig = ragConfig;
        this.documentConfig = documentConfig;
    }

    /**
     * Initiates asynchronous knowledge extraction.
     * @return Task ID for progress tracking.
     */
    public String extractKnowledgeAsync(MultipartFile pdfFile, String query, String modelName, String ocrLanguage, boolean useOcr) {
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
        performExtractionAsync(taskId, pdfFile, query, modelName, lang, useOcr);
        return taskId;
    }

    @Async // Marks this method to be executed in a separate thread
    public CompletableFuture<List<String>> performExtractionAsync(String taskId, MultipartFile pdfFile, String query, String modelName, String ocrLanguage, boolean useOcr) {
        List<String> finalSnippets = new ArrayList<>();
        try {
            // Generate document ID for caching and RAG
            String documentId = generateDocumentId(pdfFile.getOriginalFilename(), pdfFile.getSize());
            String fullPdfText;

            if (useOcr) {
                progressTrackingService.updateTaskProgress(taskId, "OCR Processing", 10, "Starting OCR...");
                fullPdfText = ocrService.performOcr(pdfFile, ocrLanguage);
                if (fullPdfText == null || fullPdfText.trim().isEmpty()) {
                    logger.warn("OCR resulted in empty text for PDF: {}", pdfFile.getOriginalFilename());
                    progressTrackingService.completeTask(taskId, true, "OCR resulted in empty text. No snippets extracted.");
                    return CompletableFuture.completedFuture(Collections.emptyList());
                }
                progressTrackingService.updateTaskProgress(taskId, "LLM Processing", 30, "OCR complete. Preparing for LLM.");
            } else {
                progressTrackingService.updateTaskProgress(taskId, "Text Extraction", 10, "Starting direct text extraction...");
                // Assuming OcrService will have a method for direct text extraction
                // For now, let's placeholder this. In a real scenario, you'd call something like:
                // fullPdfText = ocrService.extractTextDirectly(pdfFile);
                // This might involve using a library like PDFBox to get text without OCR.
                // For the purpose of this change, we'll assume performOcr can handle a null/empty language for direct extraction
                // or a new method like extractTextDirectly(pdfFile) is added to OcrService.
                // Let's simulate this by calling a hypothetical method or adapting performOcr.
                // For now, we will call a hypothetical 'extractTextDirectly' method.
                // You will need to implement this method in OcrService.java
                fullPdfText = ocrService.extractTextDirectly(pdfFile); // This method needs to be added to OcrService
                if (fullPdfText == null || fullPdfText.trim().isEmpty()) {
                    logger.warn("Direct text extraction resulted in empty text for PDF: {}", pdfFile.getOriginalFilename());
                    progressTrackingService.completeTask(taskId, true, "Direct text extraction resulted in empty text. No snippets extracted.");
                    return CompletableFuture.completedFuture(Collections.emptyList());
                }
                progressTrackingService.updateTaskProgress(taskId, "LLM Processing", 30, "Text extraction complete. Preparing for LLM.");
            }
            
            // Process document for RAG if enabled and not already processed
            if (ragConfig.isEnabled() && !ragService.isDocumentProcessed(documentId)) {
                progressTrackingService.updateTaskProgress(taskId, "Document Processing", 20, "Processing document for semantic search...");
                ragService.processDocument(documentId, pdfFile.getOriginalFilename(), fullPdfText);
            }
            // Note: The progress update for LLM (30%) is now inside the if/else block.

            // Chunking logic for large text
            List<String> textChunks = splitTextIntoChunks(fullPdfText, MAX_TEXT_LENGTH_FOR_LLM);
            int totalChunks = textChunks.size();
            logger.info("Split OCR text into {} chunks for LLM processing.", totalChunks);

            for (int i = 0; i < totalChunks; i++) {
                String chunk = textChunks.get(i);
                String chunkMessage = String.format("Processing chunk %d of %d with LLM...", i + 1, totalChunks);
                int currentProgress = 30 + (int) (((float) (i + 1) / totalChunks) * 60); // LLM processing takes 60% of progress
                progressTrackingService.updateTaskProgress(taskId, "LLM Processing", currentProgress, chunkMessage);

                String extractionPrompt = createEnhancedPrompt(query, chunk); // This is the specific prompt for extraction

                // Pass the extractionPrompt as the customPrompt to OllamaService.
                // The 'text' parameter for enhanceText in this context is the extractionPrompt itself,
                // as OllamaService's enhanceText expects the full prompt content if customPrompt is used this way.
                // However, OllamaService.enhanceText's first parameter is 'text to be enhanced', and the third is 'customPrompt'.
                // The current createEnhancedPrompt already includes the chunk.
                // So, we should pass the 'chunk' as the text to be enhanced, and 'extractionPrompt' as the custom prompt.
                // NO, the `extractionPrompt` IS the full prompt. `ollamaService.enhanceText`'s first param is the text to operate on if no custom prompt,
                // or it's the full prompt string if customPrompt is null.
                // If customPrompt is NOT null, then the first param `text` to `ollamaService.enhanceText` is the actual text segment.
                // The `createEnhancedPrompt` already formats the query and chunk into a single prompt string.
                // So, we should pass `extractionPrompt` as the first argument to `ollamaService.enhanceText` and `null` for its `customPrompt` argument,
                // OR, we need to refactor `OllamaService.enhanceText` to better distinguish.

                // Let's re-evaluate:
                // `createEnhancedPrompt(query, chunk)` creates the full prompt string.
                // `ollamaService.enhanceText(String text, String modelName, String customPrompt, Boolean enableChunking)`
                // If `customPrompt` is provided to `enhanceText`, it uses that. If not, it builds one using `getSpecializedPrompt` with the `text` param.
                // The `KnowledgeExtractorService` wants to use its *own* crafted prompt.
                // So, the `extractionPrompt` (which contains the chunk and query) should be the thing sent to the LLM.
                // The `ollamaService.enhanceText` method's first parameter `text` is somewhat ambiguous here.
                // Let's assume `ollamaService.enhanceText` should receive the full prompt as its first argument if `customPrompt` is null.
                // The `createEnhancedPrompt` already creates the full prompt.
                // The `OllamaService.processSingleText` uses `customPromptToUse` if present, otherwise builds one.
                // The `KnowledgeExtractorService` should pass its fully formed prompt as the `customPromptToUse` argument.
                // The first argument to `ollamaService.enhanceText` should then be the `chunk` itself, if `customPromptToUse` is designed to take the chunk via String.format.
                // The `createEnhancedPrompt` already includes the chunk. So, the `extractionPrompt` is self-contained.
                // We should pass `extractionPrompt` as the `text` to `ollamaService.enhanceText` and `null` for `customPrompt` in `ollamaService.enhanceText`
                // This way, `ollamaService.processSingleText` will use `promptText = customPromptToUse` (which will be null)
                // then `promptText = getSpecializedPrompt("generic", extractionPrompt)` -- this is NOT what we want.

                // Correct approach:
                // The `extractionPrompt` from `createEnhancedPrompt` is the complete prompt.
                // `ollamaService.enhanceText` should be called with this complete prompt.
                // The `customPrompt` parameter in `ollamaService.enhanceText` is for overriding its internal prompt generation.
                // So, we pass `extractionPrompt` as the `text` (first param) and `null` as `customPrompt` (third param).
                // This means `OllamaService` will use `getSpecializedPrompt("generic", extractionPrompt)` which is not right.

                // The `createEnhancedPrompt` method in `KnowledgeExtractorService` creates the *entire* prompt.
                // This entire prompt should be sent to the LLM.
                // `OllamaService.enhanceText`'s `text` parameter is the content to be worked on, and `customPrompt` is the template.
                // If `customPrompt` is null, `OllamaService` uses its default "generic" template with the passed `text`.
                // If `customPrompt` is NOT null, `OllamaService` uses `customPrompt` (formatting `text` into it if %s exists).

                // So, `KnowledgeExtractorService` should pass its `extractionPrompt` as the `customPrompt` argument to `ollamaService.enhanceText`,
                // and the `chunk` as the `text` argument.
                // The `createEnhancedPrompt` method should then be modified to be a template that accepts the chunk.

                // Simpler: `createEnhancedPrompt` already creates the full prompt.
                // We need `OllamaService.enhanceText` to just send this prompt if it's provided, without trying to format it further or use its own templates.
                // Let's modify `OllamaService.enhanceText` and `processSingleText` to accept a pre-formatted prompt.
                // For now, the existing call to `ollamaService.enhanceText(prompt, modelName, null, false)` means that `prompt` (which is `extractionPrompt`)
                // is passed as the `text` argument to `ollamaService.enhanceText`.
                // Inside `ollamaService.enhanceText`, if `customPrompt` is null (which it is), then `processSingleText` is called.
                // Inside `processSingleText`, `customPromptToUse` is null. So it calls `promptText = getSpecializedPrompt("generic", text);`
                // where `text` is our `extractionPrompt`. This means our carefully crafted `extractionPrompt` is being wrapped by another generic OCR correction prompt. THIS IS THE BUG.

                // The fix is to pass `extractionPrompt` as the `customPrompt` to `ollamaService.enhanceText`, and the `chunk` as the `text`.
                // Then `createEnhancedPrompt` needs to be a template.
                // OR, a simpler fix: if `customPrompt` is passed to `ollamaService.enhanceText` and it *doesn't* contain "%s",
                // then `ollamaService.processSingleText` should use it directly as `promptText`.

                // Let's adjust `KnowledgeExtractorService` call first.
                // The `extractionPrompt` is already the full message for the LLM.
                // We need `OllamaService` to just send it.
                // The `OllamaService.enhanceText(String text, ...)`: `text` is the primary content.
                // `customPrompt` is the template.
                // If `KnowledgeExtractorService` has already built the full prompt, it should be passed as `customPrompt`, and `text` can be minimal or even the chunk again.
                // The `createEnhancedPrompt` already includes the chunk.

                String finalPromptForLlm = createEnhancedPrompt(query, chunk);

                // Call OllamaService.enhanceText. Since finalPromptForLlm is the *entire* prompt,
                // we pass it as the 'text' argument, and 'null' for 'customPrompt' in enhanceText.
                // OllamaService.processSingleText will then use this 'text' with its own generic prompt, which is wrong.

                // The most direct fix is to ensure that if a `customPrompt` is passed to `enhanceText`
                // and that `customPrompt` is intended to be the *final, complete* prompt,
                // then `OllamaService` should use it directly.
                // The current logic in `OllamaService.processSingleText` is:
                // if (customPromptToUse != null && !customPromptToUse.trim().isEmpty()) {
                // promptText = customPromptToUse.contains("%s") ? String.format(customPromptToUse, text) : customPromptToUse + "\n\n" + text;
                // } else { promptText = getSpecializedPrompt("generic", text); }
                // This means if `customPromptToUse` doesn't have "%s", it appends the `text` argument.
                // We want `KnowledgeExtractorService` to provide the *exact* prompt.

                // So, `KnowledgeExtractorService` should call `ollamaService.enhanceText` with the `chunk` as the first `text` argument,
                // and the result of `createEnhancedPrompt(query, chunk)` as the `customPrompt` argument.
                // Then, `createEnhancedPrompt` must be a template string with "%s" for the chunk.
                // This is getting complicated.

                // Let's simplify. The `prompt` from `createEnhancedPrompt` IS the final prompt.
                // `OllamaService.enhanceText` should have a way to take a raw, final prompt.
                // The current `enhanceText(text, modelName, customPrompt)`:
                // - `text` is the core data.
                // - `customPrompt` is a template that might use `text`.
                // The `KnowledgeExtractorService` has already made the full prompt.
                // The simplest change is to ensure `OllamaService.enhanceText` uses the `customPrompt` as the *entire* prompt if it's provided and doesn't need formatting.

                // The call `ollamaService.enhanceText(prompt, modelName, null, false)` is problematic.
                // `prompt` (our full extraction prompt) is treated as `text` by `OllamaService`,
                // and then `OllamaService` wraps it with its own generic OCR correction prompt.

                // Corrected call: Pass the chunk as `text` and the generated prompt as `customPrompt`.
                // `createEnhancedPrompt` should then be a template expecting the chunk.
                // This requires changing `createEnhancedPrompt` to return a template string.

                // Alternative: Modify `OllamaService.enhanceText` to have a signature like `enhanceTextWithFullPrompt(String fullPrompt, String modelName, Boolean enableChunking)`
                // This is cleaner. Let's assume we add this to `OllamaService`.
                // For now, to fix the immediate issue with minimal changes to OllamaService:
                // The `createEnhancedPrompt` already creates the full prompt.
                // We need `OllamaService.processSingleText` to use this full prompt directly.
                // If `customPromptToUse` is passed and does NOT contain "%s", it should be used as is.

                String fullPromptForLlm = createEnhancedPrompt(query, chunk);
                // The `null` for customPrompt means OllamaService will use its generic prompt, wrapping our `fullPromptForLlm`.
                // This is the issue.
                // We need to pass `fullPromptForLlm` AS the custom prompt, and make the `text` param to enhanceText irrelevant or the chunk.
                // Let's modify `OllamaService.processSingleText` to prioritize `customPromptToUse` as the full prompt if it doesn't contain "%s".

                // The call from KnowledgeExtractorService:
                OllamaService.EnhancementResult llmResult = ollamaService.enhanceText(
                    chunk, // Pass the raw chunk as the 'text'
                    modelName,
                    fullPromptForLlm, // Pass the fully formed prompt as 'customPrompt'
                    false // Chunking is handled by KnowledgeExtractorService
                );
                String llmResponse = llmResult.getEnhancedText();

                if (llmResponse != null && !llmResponse.trim().isEmpty() && !llmResponse.contains("No relevant snippets found in this segment.")) {
                    finalSnippets.addAll(parseSnippetsFromLlmResponse(llmResponse));
                }
            }
            
            progressTrackingService.updateTaskProgress(taskId, "Finalizing", 95, "Aggregating results.");
            if (finalSnippets.isEmpty()) {
                // Use the overloaded completeTask for KnowledgeExtractionProgressInfo
                progressTrackingService.completeTask(taskId, true, finalSnippets, "No relevant snippets found in the document.");
            } else {
                // Use the overloaded completeTask for KnowledgeExtractionProgressInfo
                progressTrackingService.completeTask(taskId, true, finalSnippets, finalSnippets.size() + " snippet(s) extracted.");
            }
            return CompletableFuture.completedFuture(finalSnippets);

        } catch (Exception e) {
            logger.error("Error during asynchronous knowledge extraction for task {}: {}", taskId, e.getMessage(), e);
            // For error cases, we can still use the generic completeTask or create another overload if needed
            // to pass an empty list for snippets in case of failure.
            // For now, keeping the original error message handling.
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
        
        if (snippets.isEmpty() && !llmResponse.trim().isEmpty() && !llmResponse.contains("No relevant snippets found in this segment.")) {
            // If no structured snippets were found by the patterns, and it's not the "no snippets" message,
            // consider the whole response as a single snippet (e.g., a summary).
            logger.info("No structured snippets found. Treating the entire LLM response as a single snippet/summary.");
            snippets.add(llmResponse.trim());
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

    /**
     * Generate a unique document ID based on filename and size
     */
    private String generateDocumentId(String filename, long fileSize) {
        return embeddingService.generateContentHash(filename + ":" + fileSize);
    }
    
    /**
     * Create an enhanced prompt with RAG context if available
     */
    private String createEnhancedPrompt(String query, String chunk) {
        StringBuilder promptBuilder = new StringBuilder();
          // Add relevant context from RAG if enabled
        if (ragConfig.isEnabled()) {
            String relevantContext = ragService.retrieveRelevantContext(query, ragConfig.getContext().getMaxChunks());
            if (!relevantContext.isEmpty()) {
                promptBuilder.append(relevantContext).append("\n\n");
            }
        }
        
        // Add the main extraction prompt
        promptBuilder.append(String.format(
            "From the following document text segment, please extract distinct text segments or snippets " +
            "that are directly relevant to the query: \"%s\". " +
            "If direct verbatim snippets are appropriate, each extracted snippet should be a contiguous block of text from the document. " +
            "Present each snippet clearly separated. For example, you can use '---SNIPPET---' as a separator between snippets, " +
            "or enclose each snippet in its own markdown code block like ```snippet\\n[SNIPPET_TEXT]\\n```. " +
            "If a summary is more appropriate than verbatim snippets for answering the query, provide a concise summary. " +
            "If no relevant information (neither snippets nor summary) can be found in this segment, respond with \"No relevant snippets found in this segment.\".\n\n" +
            "DOCUMENT TEXT SEGMENT:\n%s",
            query, chunk
        ));
        
        return promptBuilder.toString();
    }
}