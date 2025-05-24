package com.pdf.marsk.pdfdemo.controller;

import com.pdf.marsk.pdfdemo.config.RagConfigurationProperties;
import com.pdf.marsk.pdfdemo.config.DocumentProcessingProperties;
import com.pdf.marsk.pdfdemo.model.DocumentChunk;
import com.pdf.marsk.pdfdemo.service.KnowledgeExtractorService;
import com.pdf.marsk.pdfdemo.service.OllamaService;
import com.pdf.marsk.pdfdemo.service.ProgressTrackingService;
import com.pdf.marsk.pdfdemo.service.TaskProgressInfo;
import com.pdf.marsk.pdfdemo.service.KnowledgeExtractionProgressInfo; // Added import
import com.pdf.marsk.pdfdemo.service.SimpleLangChain4jRagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/extract")
public class KnowledgeExtractorController {    private static final Logger logger = LoggerFactory.getLogger(KnowledgeExtractorController.class);
    
    private final KnowledgeExtractorService knowledgeExtractorService;
    private final OllamaService ollamaService;
    private final ProgressTrackingService progressTrackingService;
    private final SimpleLangChain4jRagService ragService;
    private final RagConfigurationProperties ragConfig;
    private final DocumentProcessingProperties documentConfig;
      public KnowledgeExtractorController(KnowledgeExtractorService knowledgeExtractorService, 
                                      OllamaService ollamaService, 
                                      ProgressTrackingService progressTrackingService,
                                      SimpleLangChain4jRagService ragService,
                                      RagConfigurationProperties ragConfig,
                                      DocumentProcessingProperties documentConfig) {
        this.knowledgeExtractorService = knowledgeExtractorService;
        this.ollamaService = ollamaService;
        this.progressTrackingService = progressTrackingService;
        this.ragService = ragService;
        this.ragConfig = ragConfig;
        this.documentConfig = documentConfig;
    }

    @GetMapping
    public String extractPage(Model model, @RequestParam(name = "taskId", required = false) String taskId,
                              @RequestParam(name = "completedTaskId", required = false) String completedTaskId,
                              @RequestParam(name = "question", required = false) String question,
                              @RequestParam(name = "answer", required = false) String answer) {
        
        // Handle results from a completed knowledge extraction task (original functionality)
        if (completedTaskId != null) {
            TaskProgressInfo progressInfo = progressTrackingService.getProgress(completedTaskId);
            if (progressInfo instanceof KnowledgeExtractionProgressInfo keInfo && keInfo.isCompleted() && keInfo.isSuccess()) {
                model.addAttribute("extractedSnippets", keInfo.getExtractedSnippets() != null ? keInfo.getExtractedSnippets() : Collections.emptyList());
                model.addAttribute("infoMessage", keInfo.getMessage());
                model.addAttribute("originalPdfFilename", keInfo.getFilename());
                model.addAttribute("userQuery", keInfo.getQuery());
            } else if (progressInfo != null && progressInfo.isCompleted() && !progressInfo.isSuccess()) {
                model.addAttribute("errorMessage", "Knowledge extraction task " + completedTaskId + " failed: " + progressInfo.getMessage());
            } else if (progressInfo == null) {
                model.addAttribute("errorMessage", "Could not retrieve results for task " + completedTaskId + ".");
            } else {
                 model.addAttribute("infoMessage", "Task " + completedTaskId + " is still processing or has an unknown status.");
            }
        }

        // Add data for the new Q&A chat interface
        if (question != null && answer != null) {
            // This is a simple way to pass back the last Q&A. A real chat would need a list.
            model.addAttribute("lastQuestion", question);
            model.addAttribute("lastAnswer", answer);
        }
        
        // Initialize chatHistory if not present
        if (!model.containsAttribute("chatHistory")) {
            model.addAttribute("chatHistory", new java.util.ArrayList<Map<String, String>>());
        }


        if (!model.containsAttribute("extractedSnippets")) {
            model.addAttribute("extractedSnippets", Collections.emptyList());
        }
        model.addAttribute("availableModels", ollamaService.getAvailableModels());
        // model.addAttribute("savedSnippets", knowledgeExtractorService.getAllSavedSnippets()); // This might be replaced or rethought for Q&A
        model.addAttribute("availableDocuments", ragService.getAvailableDocumentSummaries()); // New: list of docs
        model.addAttribute("currentTaskId", taskId);
        
        model.addAttribute("ragEnabled", ragConfig.isEnabled());
        model.addAttribute("embeddingModel", ragConfig.getEmbedding().getModelName());
        
        return "extract"; // This will be refactored to a new Q&A page or this page will be heavily modified
    }

    @PostMapping("/process")
    public String handleKnowledgeExtraction(@RequestParam("pdfFile") MultipartFile pdfFile,
                                            @RequestParam("query") String query,
                                            @RequestParam("modelName") String modelName,
                                            @RequestParam(name="ocrLanguage", defaultValue="eng") String ocrLanguage,
                                            @RequestParam(name="useOcr", defaultValue="false") boolean useOcr, // Changed default to false
                                            @RequestParam(name="useRag", defaultValue="true") boolean useRag, // Changed default to true for uploads
                                            @RequestParam(name="maxResults", defaultValue="5") int maxResults,
                                            RedirectAttributes redirectAttributes) {
        if (pdfFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a PDF file.");
            return "redirect:/extract";
        }
        if (query == null || query.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please enter a query.");
            return "redirect:/extract";
        }
         if (modelName == null || modelName.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select an LLM model.");
            return "redirect:/extract";
        }

        try {
            String taskId = knowledgeExtractorService.extractKnowledgeAsync(pdfFile, query, modelName, ocrLanguage, useOcr); // Pass useOcr to service
            redirectAttributes.addFlashAttribute("infoMessage", "Knowledge extraction started" +
                (useOcr ? " (with OCR)" : " (direct text extraction)") +
                (useRag ? " with RAG enhancement" : "") + ". Task ID: " + taskId);
            // Redirect to the extract page with the taskId to initiate polling
            return "redirect:/extract?taskId=" + taskId;
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument for knowledge extraction: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            logger.error("Error starting knowledge extraction for PDF: {}", pdfFile.getOriginalFilename(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An error occurred while starting extraction: " + e.getMessage());
        }
        return "redirect:/extract";
    }

    // Endpoint for polling progress (similar to OCR)
    @GetMapping("/progress/{taskId}")
    @ResponseBody
    public ResponseEntity<?> getExtractionProgress(@PathVariable String taskId) {
        TaskProgressInfo progress = progressTrackingService.getProgress(taskId);
        if (progress == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(progress);
    }

    // --- RAG Enhancement Endpoints ---

    /**
     * Semantic search endpoint for finding similar document chunks
     */
    @PostMapping("/search/semantic")
    @ResponseBody
    public ResponseEntity<?> semanticSearch(@RequestParam("query") String query,
                                          @RequestParam(name="limit", defaultValue="10") int limit,
                                          @RequestParam(name="similarityThreshold", required=false) Double similarityThreshold) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Query cannot be empty"));
            }            double threshold = similarityThreshold != null ? similarityThreshold : ragConfig.getSimilarityThreshold();
            List<DocumentChunk> chunks = ragService.findSimilarChunks(query, limit, threshold);
            
            Map<String, Object> response = new HashMap<>();
            response.put("query", query);
            response.put("similarityThreshold", threshold);
            response.put("totalResults", chunks.size());
            response.put("chunks", chunks);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error performing semantic search: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to perform semantic search: " + e.getMessage()));
        }
    }

    /**
     * Get similar documents based on a document ID
     */
    @GetMapping("/documents/{documentId}/similar")
    @ResponseBody
    public ResponseEntity<?> getSimilarDocuments(@PathVariable String documentId,
                                                @RequestParam(name="limit", defaultValue="5") int limit) {        try {
            List<DocumentChunk> similarChunks = ragService.findSimilarDocuments(documentId, limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("documentId", documentId);
            response.put("totalResults", similarChunks.size());
            response.put("similarChunks", similarChunks);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error finding similar documents for ID {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to find similar documents: " + e.getMessage()));
        }
    }

    /**
     * RAG-enhanced query endpoint
     */
    @PostMapping("/query/rag")
    @ResponseBody
    public ResponseEntity<?> ragQuery(@RequestParam("query") String query,
                                    @RequestParam("modelName") String modelName,
                                    @RequestParam(name="maxContext", required=false) Integer maxContext,
                                    @RequestParam(name="includeMetadata", defaultValue="true") boolean includeMetadata) {
        try {
            if (!ragConfig.isEnabled()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "RAG functionality is not enabled"));
            }

            int contextSize = maxContext != null ? maxContext : ragConfig.getMaxContextSize();
              // Find relevant chunks
            List<DocumentChunk> relevantChunks = ragService.findSimilarChunks(
                query, contextSize, ragConfig.getSimilarityThreshold());
            
            if (relevantChunks.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "query", query,
                    "answer", "No relevant information found in the knowledge base.",
                    "relevantChunks", Collections.emptyList()
                ));
            }

            // Build context from chunks
            StringBuilder context = new StringBuilder();
            for (DocumentChunk chunk : relevantChunks) {
                context.append(chunk.getContent()).append("\n\n");
            }

            // Generate RAG-enhanced response
            String ragPrompt = buildRagPrompt(query, context.toString());
            OllamaService.EnhancementResult result = ollamaService.enhanceText(
                context.toString(), modelName, ragPrompt, false);

            Map<String, Object> response = new HashMap<>();
            response.put("query", query);
            response.put("answer", result.getEnhancedText());
            response.put("contextUsed", relevantChunks.size());
            response.put("totalContextLength", context.length());
            
            if (includeMetadata) {
                response.put("relevantChunks", relevantChunks);
                response.put("model", modelName);
                response.put("similarityThreshold", ragConfig.getSimilarityThreshold());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing RAG query: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to process RAG query: " + e.getMessage()));
        }
    }

    /**
     * Get RAG configuration information
     */
    @GetMapping("/config/rag")
    @ResponseBody
    public ResponseEntity<?> getRagConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", ragConfig.isEnabled());
        config.put("maxContextSize", ragConfig.getMaxContextSize());
        config.put("contextWindowOverlap", ragConfig.getContextWindowOverlap());
        config.put("similarityThreshold", ragConfig.getSimilarityThreshold());
        config.put("embeddingModel", ragConfig.getEmbedding().getModelName());
        config.put("embeddingDimensions", ragConfig.getEmbedding().getDimensions());
        config.put("chunkSize", documentConfig.getChunkSize());
        config.put("chunkOverlap", documentConfig.getChunkOverlap());
        config.put("maxParallelJobs", documentConfig.getMaxParallelJobs());
        
        return ResponseEntity.ok(config);
    }

    /**
     * Get document processing statistics
     */
    @GetMapping("/stats/documents")
    @ResponseBody
    public ResponseEntity<?> getDocumentStats() {        try {
            Map<String, Object> stats = ragService.getProcessingStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error retrieving document statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve statistics: " + e.getMessage()));
        }
    }

    /**
     * Re-index documents for embedding search
     */
    @PostMapping("/admin/reindex")
    @ResponseBody
    public ResponseEntity<?> reindexDocuments(@RequestParam(name="force", defaultValue="false") boolean force) {        try {
            String taskId = ragService.reindexAllDocuments(force);
            return ResponseEntity.ok(Map.of(
                "message", "Document re-indexing started",
                "taskId", taskId,
                "force", force
            ));
        } catch (Exception e) {
            logger.error("Error starting document re-indexing: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to start re-indexing: " + e.getMessage()));
        }
    }

    // --- Existing Endpoints ---

    @PostMapping("/ask")
    @ResponseBody // Indicates the return value should be bound to the web response body
    public ResponseEntity<?> askQuestion(@RequestParam("question") String question,
                                         @RequestParam(name = "modelName", required = false) String modelName) {
        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Question cannot be empty."));
        }
        String llmModel = (modelName != null && !modelName.trim().isEmpty()) ? modelName : ollamaService.getAvailableModels().stream().findFirst().orElse("llama3"); // Default model

        try {
            String answer = ragService.answerQuery(question, llmModel);
            Map<String, String> response = new HashMap<>();
            response.put("question", question);
            response.put("answer", answer);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error answering question '{}': {}", question, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Error processing your question: " + e.getMessage()));
        }
    }

    @PostMapping("/save")
    public String saveSelectedSnippets(@RequestParam("originalPdfFilename") String originalPdfFilename,
                                       @RequestParam("userQuery") String userQuery,
                                       @RequestParam(value = "snippetsToSave", required = false) List<String> snippetsToSave,
                                       RedirectAttributes redirectAttributes) {
        if (snippetsToSave == null || snippetsToSave.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No snippets selected to save.");
            return "redirect:/extract";
        }

        try {
            knowledgeExtractorService.saveSnippets(originalPdfFilename, userQuery, snippetsToSave);
            redirectAttributes.addFlashAttribute("successMessage", "Successfully saved " + snippetsToSave.size() + " snippet(s).");
        } catch (Exception e) {
            logger.error("Error saving snippets for PDF: {}, query: '{}'", originalPdfFilename, userQuery, e);
            redirectAttributes.addFlashAttribute("errorMessage", "An error occurred while saving snippets: " + e.getMessage());
        }
        return "redirect:/extract";
    }

    @PostMapping("/snippets/delete/{id}")
    public String deleteSnippet(@PathVariable("id") Long snippetId, RedirectAttributes redirectAttributes) {
        try {
            knowledgeExtractorService.deleteSnippet(snippetId);
            redirectAttributes.addFlashAttribute("successMessage", "Snippet deleted successfully.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            logger.error("Error deleting snippet with ID {}: {}", snippetId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An error occurred while deleting the snippet.");
        }
        return "redirect:/extract";
    }

    @PostMapping("/documents/delete/{documentId}")
    public String deleteDocument(@PathVariable String documentId, RedirectAttributes redirectAttributes) {
        try {
            ragService.deleteDocument(documentId);
            redirectAttributes.addFlashAttribute("successMessage", "Document with ID '" + documentId + "' and its chunks have been deleted from the repository.");
        } catch (Exception e) {
            logger.error("Error deleting document ID {}: {}", documentId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting document: " + e.getMessage());
        }
        return "redirect:/extract";
    }

    @PostMapping("/documents/delete-all")
    public String deleteAllDocuments(RedirectAttributes redirectAttributes) {
        try {
            ragService.deleteAllDocuments();
            redirectAttributes.addFlashAttribute("successMessage", "All documents and their chunks have been deleted from the repository.");
        } catch (Exception e) {
            logger.error("Error deleting all documents: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting all documents: " + e.getMessage());
        }
        return "redirect:/extract";
    }

    // --- Helper Methods ---

    private String buildRagPrompt(String query, String context) {
        return String.format("""
            Based on the following context from the knowledge base, please answer the user's question.
            If the context doesn't contain relevant information, please state that clearly.
            
            Context:
            %s
            
            Question: %s
            
            Answer:""", context, query);
    }
}