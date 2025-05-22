package com.pdf.marsk.pdfdemo.controller;

import com.pdf.marsk.pdfdemo.model.KnowledgeSnippet;
import com.pdf.marsk.pdfdemo.service.KnowledgeExtractorService;
import com.pdf.marsk.pdfdemo.service.OllamaService; // For listing models
import com.pdf.marsk.pdfdemo.service.ProgressTrackingService; // Added
import com.pdf.marsk.pdfdemo.service.TaskProgressInfo; // Added
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity; // Added
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/extract")
public class KnowledgeExtractorController {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeExtractorController.class);

    private final KnowledgeExtractorService knowledgeExtractorService;
    private final OllamaService ollamaService;
    private final ProgressTrackingService progressTrackingService; // Added

    @Autowired
    public KnowledgeExtractorController(KnowledgeExtractorService knowledgeExtractorService, OllamaService ollamaService, ProgressTrackingService progressTrackingService) { // Added
        this.knowledgeExtractorService = knowledgeExtractorService;
        this.ollamaService = ollamaService;
        this.progressTrackingService = progressTrackingService; // Added
    }

    @GetMapping
    public String extractPage(Model model, @RequestParam(name = "taskId", required = false) String taskId,
                              @RequestParam(name = "completedTaskId", required = false) String completedTaskId) {
        
        // If a task was just completed, its results might be passed via RedirectAttributes or re-fetched
        if (completedTaskId != null) {
            // Potentially fetch final results/status for completedTaskId to display
            // For now, just acknowledge. The main display of snippets will be via polling or subsequent load.
            model.addAttribute("infoMessage", "Knowledge extraction task " + completedTaskId + " has finished processing.");
        }

        if (!model.containsAttribute("extractedSnippets")) {
            model.addAttribute("extractedSnippets", Collections.emptyList());
        }
        model.addAttribute("availableModels", ollamaService.getAvailableModels());
        model.addAttribute("savedSnippets", knowledgeExtractorService.getAllSavedSnippets());
        model.addAttribute("currentTaskId", taskId); // Pass current task ID for polling UI
        return "extract";
    }

    @PostMapping("/process")
    public String handleKnowledgeExtraction(@RequestParam("pdfFile") MultipartFile pdfFile,
                                            @RequestParam("query") String query,
                                            @RequestParam("modelName") String modelName,
                                            @RequestParam(name="ocrLanguage", defaultValue="eng") String ocrLanguage, // Added
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
            String taskId = knowledgeExtractorService.extractKnowledgeAsync(pdfFile, query, modelName, ocrLanguage);
            redirectAttributes.addFlashAttribute("infoMessage", "Knowledge extraction started. Task ID: " + taskId);
            // Redirect to the extract page with the taskId to initiate polling
            return "redirect:/extract?taskId=" + taskId;
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument for knowledge extraction: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) { // Catching a broader exception just in case async setup fails immediately
            logger.error("Error starting knowledge extraction for PDF: {}", pdfFile.getOriginalFilename(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An error occurred while starting extraction: " + e.getMessage());
        }
        return "redirect:/extract";
    }

    // Endpoint for polling progress (similar to OCR)
    @GetMapping("/progress/{taskId}")
    @ResponseBody
    public ResponseEntity<?> getExtractionProgress(@PathVariable String taskId) { // Return ResponseEntity
        TaskProgressInfo progress = progressTrackingService.getProgress(taskId); // Use injected service
        if (progress == null) {
            return ResponseEntity.notFound().build(); // Explicitly return 404
        }
        return ResponseEntity.ok(progress); // Return 200 OK with progress if found
    }
    
    // Endpoint to retrieve results of a completed task (if needed, or results are part of progress info)
    // For now, assuming the final message in TaskProgressInfo contains snippet count or error.
    // The actual snippets would be saved and listed separately.

    // --- Endpoints for Phase 2 (Saving and Deleting Snippets) ---

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
}