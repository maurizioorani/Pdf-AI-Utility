package com.pdf.marsk.pdfdemo.controller;

import com.pdf.marsk.pdfdemo.model.OcrTextDocument;
import com.pdf.marsk.pdfdemo.repository.OcrTextDocumentRepository;
import com.pdf.marsk.pdfdemo.service.OllamaService;
import com.pdf.marsk.pdfdemo.service.OcrService;
import com.pdf.marsk.pdfdemo.service.ProgressTrackingService;
import com.pdf.marsk.pdfdemo.service.OcrProgressInfo; 
import com.pdf.marsk.pdfdemo.service.TaskProgressInfo; 

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Controller
@RequestMapping("/ocr")
public class OcrController implements DisposableBean, ApplicationListener<ContextClosedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(OcrController.class);
    private final ExecutorService taskExecutor = Executors.newFixedThreadPool(2); // Keep for now, might be used in unshown methods

    private final OcrService ocrService; // Keep for now
    private final ProgressTrackingService progressTrackingService;
    private final OcrTextDocumentRepository ocrTextDocumentRepository;
    private final OllamaService ollamaService;

    public OcrController(OcrService ocrService,
                         ProgressTrackingService progressTrackingService,
                         OcrTextDocumentRepository ocrTextDocumentRepository,
                         OllamaService ollamaService) {
        this.ocrService = ocrService;
        this.progressTrackingService = progressTrackingService;
        this.ocrTextDocumentRepository = ocrTextDocumentRepository;
        this.ollamaService = ollamaService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String ocrPage(Model model,
                          @RequestParam(name = "completedTaskId", required = false) String completedTaskId,
                          @RequestParam(name = "enhance", required = false) Boolean enhance,
                          @RequestParam(name = "modelName", required = false) String modelName, // Renamed from "model" to avoid conflict if Model type was also named "model"
                          @RequestParam(name = "documentType", required = false, defaultValue = "generic") String documentType,
                          @RequestParam(name = "enableChunking", required = false) Boolean enableChunking,
                          @RequestParam(name = "pollDone", required = false) Boolean pollDone,
                          @RequestParam(name = "originalFilename", required = false) String originalFilenameFromParam,
                          @RequestParam(name = "language", required = false) String languageFromParam) {

       initializeModelDefaults(model, enableChunking);

        if (completedTaskId != null) {
            processCompletedTask(model, completedTaskId, enhance, modelName, documentType, enableChunking, pollDone);
        } else {
            handlePageLoadWithoutActiveTask(model, originalFilenameFromParam, languageFromParam);
        }

        setInformationalMessages(model, completedTaskId, pollDone);
        ensureComparisonDefaults(model);
        loadPersistentAndAvailableData(model);
        setFinalStatusFlags(model, pollDone);
        
        return "ocr";
    }

    private void initializeModelDefaults(Model model, Boolean enableChunkingParam) {
        if (!model.containsAttribute("isEnhanced")) {
            model.addAttribute("isEnhanced", false);
        }
        if (!model.containsAttribute("showComparison")) {
            model.addAttribute("showComparison", false);
        }
        if (!model.containsAttribute("chunkingEnabled")) { // Set based on param or default true
            model.addAttribute("chunkingEnabled", enableChunkingParam != null ? enableChunkingParam : true);
        }

        model.addAttribute("documentTypes", List.of("generic", "business", "academic", "technical", "legal", "literary", "italian-literary"));
    }

    private void processCompletedTask(Model model, String completedTaskId, Boolean enhance, String modelName, String documentType, Boolean enableChunking, Boolean pollDone) {
        TaskProgressInfo generalProgressInfo = progressTrackingService.getProgress(completedTaskId);

        if (generalProgressInfo instanceof OcrProgressInfo progressInfo) { // Check and cast
            if (progressInfo.isCompleted() && progressInfo.isSuccess()) {
                handleSuccessfulOcrTask(model, progressInfo, enhance, modelName, documentType, enableChunking, completedTaskId, pollDone);
            } else if (progressInfo.isCompleted() && !progressInfo.isSuccess()) {
                model.addAttribute("ocrError", "OCR processing failed for task " + completedTaskId + ". Error: " + progressInfo.getMessage());
            }
        } else if (generalProgressInfo == null) {
            if (!model.containsAttribute("ocrResult")) { // Avoid overwriting if result already set by other means
                model.addAttribute("ocrError", "Could not find results for OCR task ID: " + completedTaskId);
            }
        } else {
             model.addAttribute("ocrError", "Task " + completedTaskId + " is not an OCR task.");
        }
    }
    
    private void handleSuccessfulOcrTask(Model model, OcrProgressInfo progressInfo, Boolean enhance, String modelName, String documentType, Boolean enableChunking, String completedTaskId, Boolean pollDone) {
        String ocrText = progressInfo.getMessage();
        setAttributesFromProgressInfo(model, progressInfo, completedTaskId, pollDone);

        if (Boolean.TRUE.equals(enhance) && modelName != null && !modelName.isEmpty()) {
            attemptOcrEnhancement(model, progressInfo, ocrText, modelName, documentType, enableChunking, completedTaskId);
        } else {
            setNonEnhancedOcrResult(model, ocrText);
        }
    }

    private void setAttributesFromProgressInfo(Model model, OcrProgressInfo progressInfo, String completedTaskId, Boolean pollDone) {
        if (Boolean.FALSE.equals(pollDone)) {
             model.addAttribute("ocrTaskId", completedTaskId);
        }
        
        if (!model.containsAttribute("originalFilename")) model.addAttribute("originalFilename", progressInfo.getFilename());
        if (!model.containsAttribute("language")) {
            model.addAttribute("language", getDisplayLanguage(progressInfo.getLanguage()));
        }
    }
    
    private String getDisplayLanguage(String langCode) {
        if ("eng".equalsIgnoreCase(langCode)) return "English";
        if ("ita".equalsIgnoreCase(langCode)) return "Italian";
        return "Unknown";
    }

    private void attemptOcrEnhancement(Model model, OcrProgressInfo progressInfo, String ocrText, String modelName, String documentType, Boolean enableChunking, String completedTaskId) {
        try {
            logger.info("Enhancing OCR result with Ollama model: {} for task ID: {} with chunking: {}", modelName, completedTaskId, enableChunking);
            String langCode = progressInfo.getLanguage();
            String effectiveDocType = determineEffectiveDocumentType(langCode, documentType);
            
            String customPrompt = "generic".equals(effectiveDocType) ? null : ollamaService.getSpecializedPrompt(effectiveDocType, ocrText);
            OllamaService.EnhancementResult result = ollamaService.enhanceText(ocrText, modelName, customPrompt, enableChunking);
            
            model.addAttribute("ocrResult", result.getEnhancedText());
            model.addAttribute("isEnhanced", true);
            model.addAttribute("enhancementModel", modelName);
            model.addAttribute("documentType", effectiveDocType);
            model.addAttribute("originalOcrText", ocrText);
            model.addAttribute("showComparison", true);
            if (result.wasAnalysisFixed()) model.addAttribute("analysisDetected", true);
        } catch (Exception e) {
            logger.error("Error enhancing OCR text for task ID {}: {}", completedTaskId, e.getMessage(), e);
            model.addAttribute("ocrResult", ocrText); // Fallback to original OCR text
            model.addAttribute("ocrError", "Failed to enhance OCR text: " + e.getMessage());
            model.addAttribute("isEnhanced", false);
        }
    }
    
    private String determineEffectiveDocumentType(String langCode, String documentType) {
        if ("ita".equalsIgnoreCase(langCode) && "literary".equals(documentType)) {
            return "italian-literary";
        }
        return documentType;
    }

    private void setNonEnhancedOcrResult(Model model, String ocrText) {
        model.addAttribute("ocrResult", ocrText);
        model.addAttribute("isEnhanced", false);
        model.addAttribute("originalOcrText", null);
        model.addAttribute("showComparison", false);
    }

    private void handlePageLoadWithoutActiveTask(Model model, String originalFilenameFromParam, String languageFromParam) {
         if (originalFilenameFromParam != null && languageFromParam != null && model.containsAttribute("ocrResult")) {
            // Handles redirect from showOriginalTransientText
            if(!model.containsAttribute("originalFilename")) model.addAttribute("originalFilename", originalFilenameFromParam);
            if(!model.containsAttribute("language")) model.addAttribute("language", languageFromParam); // Assuming languageFromParam is already display-ready
        }
    }
    
    private void setInformationalMessages(Model model, String completedTaskId, Boolean pollDone) {
        // Don't show initial info if poll just finished or other relevant attributes are present
        if (!model.containsAttribute("ocrResult") && !model.containsAttribute("ocrError") && 
            !model.containsAttribute("ocrTaskId") && completedTaskId == null && 
            !Boolean.TRUE.equals(pollDone)) { 
             if (!model.containsAttribute("ocrInfo")) {
                model.addAttribute("ocrInfo", "Upload an image file (PNG, JPG, TIFF, PDF) to extract text using OCR.");
            }
        }
    }
    
    private void ensureComparisonDefaults(Model model) {
        if (Boolean.FALSE.equals(model.getAttribute("isEnhanced"))) {
            model.addAttribute("originalOcrText", null);
            model.addAttribute("showComparison", false);
        }
    }

    private void loadPersistentAndAvailableData(Model model) {
        List<OcrTextDocument> savedOcrDocuments = ocrTextDocumentRepository.findAllByOrderByCreatedAtDesc();
        model.addAttribute("savedOcrDocuments", savedOcrDocuments != null ? savedOcrDocuments : Collections.emptyList());
        model.addAttribute("ollamaModels", ollamaService.getAvailableModels()); 
    }

    private void setFinalStatusFlags(Model model, Boolean pollDone) {
        if (Boolean.TRUE.equals(pollDone) && !model.containsAttribute("ocrTaskId") && !model.containsAttribute("completedTaskId")) {
            model.addAttribute("pageLoadAfterPolling", true);
        }
    }

    @Override
    public void destroy() throws Exception {
        logger.info("Shutting down OcrController's task executor.");
        if (taskExecutor != null && !taskExecutor.isShutdown()) {
            taskExecutor.shutdown();
            // Optionally, add taskExecutor.awaitTermination() here
        }
    }

    @Override
    public void onApplicationEvent(@NonNull ContextClosedEvent event) {
        logger.info("Application context closed. OcrController performing cleanup.");
        if (taskExecutor != null && !taskExecutor.isShutdown()) {
             taskExecutor.shutdownNow(); // Force shutdown on context close
        }
    }
}