package com.pdf.marsk.pdfdemo.controller;

import com.pdf.marsk.pdfdemo.config.RagConfigurationProperties;
import com.pdf.marsk.pdfdemo.service.KnowledgeExtractorService;
import com.pdf.marsk.pdfdemo.service.OcrService;
import com.pdf.marsk.pdfdemo.service.OllamaService;
import com.pdf.marsk.pdfdemo.service.ProgressTrackingService;
import com.pdf.marsk.pdfdemo.service.TaskProgressInfo;
import com.pdf.marsk.pdfdemo.service.KnowledgeExtractionProgressInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/rag") // Changed from "/extract" to reflect new focus
public class KnowledgeExtractorController {
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeExtractorController.class);
    
    private final KnowledgeExtractorService ragService; // Renamed for clarity, this is the new RAG service
    private final OllamaService ollamaService;
    private final RagConfigurationProperties ragConfig;
    private final ProgressTrackingService progressTrackingService; // Added

    // Updated constructor
    public KnowledgeExtractorController(KnowledgeExtractorService knowledgeExtractorService,
                                      OllamaService ollamaService,
                                      RagConfigurationProperties ragConfig,
                                      ProgressTrackingService progressTrackingService) { // Added
        this.ragService = knowledgeExtractorService;
        this.ollamaService = ollamaService;
        this.ragConfig = ragConfig;
        this.progressTrackingService = progressTrackingService; // Added
    }

    @GetMapping
    public String ragPage(Model model, 
                          @RequestParam(name = "question", required = false) String question,
                          @RequestParam(name = "answer", required = false) String answer) {
        
        logger.info("RAG page requested. Question: '{}', Answer: '{}'", question, answer);

        // Add data for the Q&A chat interface
        if (question != null && answer != null) {
            model.addAttribute("lastQuestion", question);
            model.addAttribute("lastAnswer", answer);
        }
        
        if (!model.containsAttribute("chatHistory")) {
            model.addAttribute("chatHistory", new ArrayList<Map<String, String>>());
        }

        model.addAttribute("ragEnabled", ragConfig.isEnabled());
        if (ragConfig.isEnabled()) {
            model.addAttribute("embeddingModel", ragConfig.getEmbeddingModelName());
            model.addAttribute("chatModel", ragConfig.getChatModelName());
            try {
                model.addAttribute("availableModels", ollamaService.getAvailableModels());
                List<Object[]> docSummaries = ragService.getLoadedDocumentSummaries();
                logger.info("Controller received {} document summaries from service.", docSummaries.size());
                model.addAttribute("availableDocuments", docSummaries);
            } catch (Exception e) {
                logger.error("Failed to get available Ollama models or document summaries", e);
                model.addAttribute("availableModels", Collections.emptyList());
                model.addAttribute("availableDocuments", Collections.emptyList());
            }
        } else {
            logger.warn("RAG is disabled, not populating availableModels or availableDocuments.");
            model.addAttribute("availableModels", Collections.emptyList());
            model.addAttribute("availableDocuments", Collections.emptyList());
        }
        
        return "extract";
    }

    @PostMapping("/ask")
    @ResponseBody // Ensure response is JSON
    public ResponseEntity<Map<String, String>> askQuestion(@RequestParam("question") String question) {
        logger.info("Received question for RAG: {}", question);
        Map<String, String> response = new HashMap<>();

        if (!ragConfig.isEnabled()) {
            logger.warn("RAG system is not enabled. Cannot process question.");
            response.put("error", "RAG system is not enabled.");
            // It's better to return a specific HTTP status for client-side errors if possible,
            // but for now, a JSON error message with 200 OK will be handled by the client.
            // Or consider: return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            return ResponseEntity.ok(response);
        }

        try {
            String answer = ragService.chat(question);
            response.put("answer", answer);
            // Optionally, include the question back if the client needs it, though it already has it.
            // response.put("question", question);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during RAG chat processing", e);
            response.put("error", "Error processing your question: " + e.getMessage());
            // Consider ResponseEntity.internalServerError().body(response); for actual server errors
            return ResponseEntity.ok(response); // Keep 200 OK for now, client handles 'error' field
        }
    }

    // --- Methods from the old KnowledgeExtractorService functionality ---
    // These are now commented out as the service has changed.
    // They need to be re-evaluated: either removed, or the old functionality
    // needs to be re-integrated or moved to a new service.    // Handles document uploads to the RAG knowledge base
    @PostMapping("/process")
    public String handleDocumentUpload(@RequestParam("pdfFile") org.springframework.web.multipart.MultipartFile pdfFile,
                                       @RequestParam(name = "modelName", required = false) String selectedModel, // model for processing
                                       @RequestParam(name = "useOcr", defaultValue = "false") boolean useOcr, // OCR checkbox
                                       RedirectAttributes redirectAttributes) {
        if (pdfFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a PDF file to upload.");
            return "redirect:/rag";
        }

        String taskId = progressTrackingService.createKnowledgeExtractionTask(
            pdfFile.getOriginalFilename(),
            "Document Ingestion", // Generic query/purpose
            selectedModel != null ? selectedModel : ragConfig.getChatModelName() // Use selected or default
        );
        progressTrackingService.updateTaskProgress(taskId, "Upload Received", 10, "File upload received by server.");
        
        // Log the OCR selection
        logger.info("Processing document with OCR: {}", useOcr);
        
        try {            // Call service method to process the PDF with OCR parameter
            ragService.addPdfDocumentAndReinitializeAsync(
                pdfFile.getOriginalFilename(),
                pdfFile.getInputStream(),
                progressTrackingService,
                taskId,
                useOcr
            );
        } catch (java.io.IOException ioe) {
            logger.error("Error getting InputStream from uploaded file for task {}: {}", taskId, pdfFile.getOriginalFilename(), ioe);
            progressTrackingService.completeTask(taskId, false, "Error accessing file content: " + ioe.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Could not access uploaded file content: " + ioe.getMessage());
            return "redirect:/rag";
        }
        // Any other synchronous RuntimeException before this point would be caught by global error handlers.
        // The async task itself handles its own exceptions and progress completion.
            
        logger.info("Task {} submitted for asynchronous PDF processing: {}", taskId, pdfFile.getOriginalFilename());
        redirectAttributes.addFlashAttribute("infoMessage", "File '" + pdfFile.getOriginalFilename() + "' is being processed in the background.");
        redirectAttributes.addAttribute("taskId", taskId); // Pass taskId to URL for client-side polling
        return "redirect:/rag";
    }

    
    @GetMapping("/progress/{taskId}")
    @ResponseBody
    public ResponseEntity<TaskProgressInfo> getProgress(@PathVariable String taskId) {
        TaskProgressInfo progress = progressTrackingService.getProgress(taskId);
        if (progress == null) {
            // Return a default "not found" or "initializing" state to prevent JS errors
            // Use KnowledgeExtractionProgressInfo as a concrete type
            KnowledgeExtractionProgressInfo notFoundProgress = new KnowledgeExtractionProgressInfo(
                taskId,
                "N/A", // filename
                "N/A", // query
                "N/A"  // modelName
            );
            notFoundProgress.setMessage("Task not found or initializing...");
            notFoundProgress.setProgressPercent(5); // Small initial progress
            return ResponseEntity.ok(notFoundProgress); // Return 200 with this payload
        }
        return ResponseEntity.ok(progress);
    }

    @PostMapping("/documents/delete/{documentId}")
    public String deleteDocument(@PathVariable String documentId, RedirectAttributes redirectAttributes) {
        // In KnowledgeExtractorService, documentId is effectively the filename stored in "source" metadata
        logger.info("Request to delete document with ID (filename): {}", documentId);
        try {
            boolean removed = ragService.removeDocumentAndReinitialize(documentId);
            if (removed) {
                redirectAttributes.addFlashAttribute("successMessage", "Document '" + documentId + "' removed successfully.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Document '" + documentId + "' not found or could not be removed.");
            }
        } catch (Exception e) {
            logger.error("Error deleting document '{}': {}", documentId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting document: " + e.getMessage());
        }
        return "redirect:/rag";
    }

    @PostMapping("/documents/delete-all")
    public String deleteAllDocuments(RedirectAttributes redirectAttributes) {
        logger.info("Request to delete all documents from in-memory RAG.");
        try {
            ragService.removeAllDocumentsAndReinitialize();
            redirectAttributes.addFlashAttribute("successMessage", "All documents removed successfully from the current session.");
        } catch (Exception e) {
            logger.error("Error deleting all documents: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting all documents: " + e.getMessage());
        }
        return "redirect:/rag";
    }
    

}