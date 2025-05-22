package com.pdf.marsk.pdfdemo.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pdf.marsk.pdfdemo.model.OcrTextDocument;
import com.pdf.marsk.pdfdemo.repository.OcrTextDocumentRepository;
import com.pdf.marsk.pdfdemo.service.OcrService;
import com.pdf.marsk.pdfdemo.service.OllamaService;
import com.pdf.marsk.pdfdemo.service.ProgressTrackingService;
import com.pdf.marsk.pdfdemo.service.TaskProgressInfo;
import com.pdf.marsk.pdfdemo.service.ProgressTrackingService.OcrProgressInfo;

import net.sourceforge.tess4j.TesseractException;

@Controller
@RequestMapping("/ocr")
public class OcrController implements DisposableBean, ApplicationListener<ContextClosedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(OcrController.class);
    private final ExecutorService taskExecutor = Executors.newFixedThreadPool(2);

    private final OcrService ocrService;
    private final ProgressTrackingService progressTrackingService;
    private final OcrTextDocumentRepository ocrTextDocumentRepository;
    private final OllamaService ollamaService;

    @Autowired
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
                          @RequestParam(name = "model", required = false) String modelName, // For enhancement
                          @RequestParam(name = "documentType", required = false, defaultValue = "generic") String documentType, // For enhancement
                          @RequestParam(name = "enableChunking", required = false) Boolean enableChunking, // For enhancement & initial OCR
                          @RequestParam(name = "pollDone", required = false) Boolean pollDone, // To prevent re-polling
                          @RequestParam(name = "originalFilename", required = false) String originalFilenameFromParam,
                          @RequestParam(name = "language", required = false) String languageFromParam
                          ) {

       if (!model.containsAttribute("isEnhanced")) {
           model.addAttribute("isEnhanced", false);
       }
       if (!model.containsAttribute("showComparison")) {
           model.addAttribute("showComparison", false);
       }
        if (!model.containsAttribute("chunkingEnabled")) { // Set based on param or default true
           model.addAttribute("chunkingEnabled", enableChunking != null ? enableChunking : true);
       }

       model.addAttribute("documentTypes", List.of("generic", "business", "academic", "technical", "legal", "literary", "italian-literary"));

       if (completedTaskId != null) {
           TaskProgressInfo generalProgressInfo = progressTrackingService.getProgress(completedTaskId);
           if (generalProgressInfo instanceof OcrProgressInfo progressInfo) { // Check and cast
               if (progressInfo.isCompleted() && progressInfo.isSuccess()) {
                   String ocrText = progressInfo.getMessage();
                   
                   if (Boolean.FALSE.equals(pollDone)) {
                        model.addAttribute("ocrTaskId", completedTaskId);
                   }
                   
                   if (!model.containsAttribute("originalFilename")) model.addAttribute("originalFilename", progressInfo.getFilename());
                   if (!model.containsAttribute("language")) {
                       String langCode = progressInfo.getLanguage();
                       String languageDisplay = "Unknown";
                       if ("eng".equalsIgnoreCase(langCode)) languageDisplay = "English";
                       else if ("ita".equalsIgnoreCase(langCode)) languageDisplay = "Italian";
                       model.addAttribute("language", languageDisplay);
                   }

                   if (Boolean.TRUE.equals(enhance) && modelName != null && !modelName.isEmpty()) {
                       try {
                           logger.info("Enhancing OCR result with Ollama model: {} for task ID: {} with chunking: {}", modelName, completedTaskId, enableChunking);
                           String langCode = progressInfo.getLanguage();
                           String effectiveDocType = documentType;
                           if ("ita".equalsIgnoreCase(langCode) && "literary".equals(documentType)) {
                               effectiveDocType = "italian-literary";
                           }
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
                           logger.error("Error enhancing OCR text for task ID {}: {}", completedTaskId, e.getMessage());
                           model.addAttribute("ocrResult", ocrText);
                           model.addAttribute("ocrError", "Failed to enhance OCR text: " + e.getMessage());
                           model.addAttribute("isEnhanced", false);
                       }
                   } else {
                       model.addAttribute("ocrResult", ocrText);
                       model.addAttribute("isEnhanced", false);
                       model.addAttribute("originalOcrText", null);
                       model.addAttribute("showComparison", false);
                   }
               } else if (progressInfo.isCompleted() && !progressInfo.isSuccess()) {
                   model.addAttribute("ocrError", "OCR processing failed for task " + completedTaskId + ". Error: " + progressInfo.getMessage());
               }
           } else if (generalProgressInfo == null) {
               if (!model.containsAttribute("ocrResult")) {
                   model.addAttribute("ocrError", "Could not find results for OCR task ID: " + completedTaskId);
               }
           } else {
                model.addAttribute("ocrError", "Task " + completedTaskId + " is not an OCR task.");
           }
       } else if (originalFilenameFromParam != null && languageFromParam != null && model.containsAttribute("ocrResult")) {
            // Handles redirect from showOriginalTransientText
            if(!model.containsAttribute("originalFilename")) model.addAttribute("originalFilename", originalFilenameFromParam);
            if(!model.containsAttribute("language")) model.addAttribute("language", languageFromParam);
        }
        
        if (!model.containsAttribute("ocrResult") && !model.containsAttribute("ocrError") && 
            !model.containsAttribute("ocrTaskId") && completedTaskId == null && 
            !Boolean.TRUE.equals(pollDone)) { // Don't show initial info if poll just finished
             if (!model.containsAttribute("ocrInfo")) {
                model.addAttribute("ocrInfo", "Upload an image file (PNG, JPG, TIFF, PDF) to extract text using OCR.");
            }
        }
        
        if (Boolean.FALSE.equals(model.getAttribute("isEnhanced"))) {
            model.addAttribute("originalOcrText", null);
            model.addAttribute("showComparison", false);
        }

        List<OcrTextDocument> savedOcrDocuments = ocrTextDocumentRepository.findAllByOrderByCreatedAtDesc();
        model.addAttribute("savedOcrDocuments", savedOcrDocuments);
        model.addAttribute("availableModels", ollamaService.getAvailableModels());

        model.addAttribute("hasOcrResult", model.containsAttribute("ocrResult"));
        model.addAttribute("hasOcrError", model.containsAttribute("ocrError"));
        // hasOcrTaskId for JS polling trigger should be false if pollDone is true
        model.addAttribute("hasOcrTaskId", Boolean.FALSE.equals(pollDone) && model.containsAttribute("ocrTaskId"));
        
        return "ocr";
    }

    @PostMapping("/process")
    public String handleOcrUpload(@RequestParam("imageFile") MultipartFile imageFile,
                                 @RequestParam(value = "language", defaultValue = "eng") String language,
                                 @RequestParam(value = "enableChunking", required = false) Boolean enableChunking,
                                 RedirectAttributes redirectAttributes,
                                 Model model) {
        if (imageFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("ocrError", "Please select an image file to upload.");
            return "redirect:/ocr";
        }

        String originalFilename = imageFile.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty() ||
            !isSupportedImageType(originalFilename.toLowerCase())) {
            redirectAttributes.addFlashAttribute("ocrError", "Unsupported file type. Please upload a PNG, JPG, JPEG, TIFF, or PDF file.");
            logger.warn("OCR attempt with unsupported file type: {}", originalFilename);
            return "redirect:/ocr";
        }
        
        try {
            logger.info("Received file for OCR: {} with language: {}, chunking: {}", 
                       imageFile.getOriginalFilename(), language, enableChunking);
            
            if (enableChunking != null) {
                redirectAttributes.addFlashAttribute("chunkingEnabled", enableChunking);
            }

            String taskId;
            boolean isPdf = originalFilename.toLowerCase().endsWith(".pdf");
            
            if (isPdf) {
                taskId = startAsyncOcrProcess(imageFile, language);
                redirectAttributes.addFlashAttribute("ocrTaskId", taskId); // This is for JS to pick up for polling
                redirectAttributes.addFlashAttribute("originalFilename", imageFile.getOriginalFilename());
                redirectAttributes.addFlashAttribute("language", language.equals("eng") ? "English" : "Italian");
                return "redirect:/ocr";
            } else {
                String extractedText = ocrService.performOcr(imageFile, language);
                redirectAttributes.addFlashAttribute("ocrResult", extractedText);
                redirectAttributes.addFlashAttribute("originalFilename", imageFile.getOriginalFilename());
                redirectAttributes.addFlashAttribute("language", language.equals("eng") ? "English" : "Italian");
                redirectAttributes.addFlashAttribute("isEnhanced", false);
                logger.info("OCR processed successfully for {} using language {}", imageFile.getOriginalFilename(), language);
                return "redirect:/ocr";
            }

        } catch (IOException e) {
            logger.error("File I/O error during OCR for {}: {}", imageFile.getOriginalFilename(), e.getMessage());
            redirectAttributes.addFlashAttribute("ocrError", "File processing error: " + e.getMessage());
        } catch (TesseractException e) {
            logger.error("Tesseract OCR error for {}: {}", imageFile.getOriginalFilename(), e.getMessage());
            String userFriendlyError = "OCR processing failed. Ensure Tesseract is installed correctly and the image is clear. Error: " + e.getMessage();
            if (e.getMessage() != null && e.getMessage().contains("Unable to load library 'tesseract'")) {
                userFriendlyError = "OCR processing failed: Tesseract library not found. Please ensure Tesseract is installed and configured correctly on the server.";
            } else if (e.getMessage() != null && e.getMessage().contains("Data path does not exist")) {
                userFriendlyError = "OCR processing failed: Tesseract 'tessdata' (language files) not found. Please check if 'eng.traineddata' is present in the tessdata directory.";
            } else if (e.getMessage() != null && e.getMessage().contains("Error during processing")) {
                userFriendlyError = "OCR processing failed: Error during text extraction. The image may be too complex or low quality.";
            }
            redirectAttributes.addFlashAttribute("ocrError", userFriendlyError);
        } catch (Exception e) {
            logger.error("Unexpected error during OCR for {}: {}", imageFile.getOriginalFilename(), e.getMessage(), e);
            redirectAttributes.addFlashAttribute("ocrError", "An unexpected error occurred during OCR processing.");
        }
        return "redirect:/ocr";
    }
    
    @GetMapping("/progress/{taskId}")
    @ResponseBody
    public ResponseEntity<?> getProgress(@PathVariable String taskId) {
        TaskProgressInfo generalProgress = progressTrackingService.getProgress(taskId);
        if (generalProgress == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Task not found");
            return ResponseEntity.status(404).body(errorResponse);
        }
        return ResponseEntity.ok(generalProgress);
    }
    
    @GetMapping("/result/{taskId}")
    @ResponseBody
    public ResponseEntity<?> getResult(@PathVariable String taskId) {
        TaskProgressInfo generalProgress = progressTrackingService.getProgress(taskId);
        if (generalProgress == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Task not found");
            return ResponseEntity.status(404).body(errorResponse);
        }
        if (!generalProgress.isCompleted()) {
            Map<String, Object> response = new HashMap<>();
            response.put("completed", false);
            response.put("progress", generalProgress.getProgressPercent());
            return ResponseEntity.ok(response);
        }
        Map<String, Object> response = new HashMap<>();
        response.put("completed", true);
        response.put("success", generalProgress.isSuccess());
        response.put("result", generalProgress.getMessage());
        return ResponseEntity.ok(response);
    }
    
    private String startAsyncOcrProcess(MultipartFile file, String language) throws IOException {
        String originalFilename = file.getOriginalFilename();
        byte[] fileBytes = file.getBytes();
        String taskId = progressTrackingService.createOcrTask(originalFilename, 0, language); 
        progressTrackingService.updateOcrTaskProgress(taskId, 0, "Preparing OCR processing...");
        CompletableFuture.runAsync(() -> {
            try {
                MultipartFile tempFile = new ByteArrayMultipartFile(fileBytes, originalFilename);
                String result = ocrService.performOcr(tempFile, language, taskId);
                progressTrackingService.completeTask(taskId, true, result);
            } catch (Exception e) {
                logger.error("Error in async OCR processing: {}", e.getMessage(), e);
                try {
                    progressTrackingService.completeTask(taskId, false, "Error: " + e.getMessage());
                } catch (Exception ex) {
                    logger.warn("Could not update task status due to application context being closed", ex);
                }
            }
        }, taskExecutor);
        return taskId;
    }

    @Override
    public void destroy() throws Exception {
        logger.info("Shutting down OCR task executor");
        shutdownTaskExecutor();
    }
    
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        logger.info("Application context is closing, shutting down OCR task executor");
        shutdownTaskExecutor();
    }
    
    private void shutdownTaskExecutor() {
        taskExecutor.shutdown();
        try {
            if (!taskExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                taskExecutor.shutdownNow();
                if (!taskExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    logger.error("Task executor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            taskExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    @PostMapping("/enhance")
    public String enhanceOcrText(@RequestParam("ocrText") String ocrText,
                                @RequestParam("originalFilename") String originalFilename,
                                @RequestParam("language") String language,
                                @RequestParam("modelName") String modelName,
                                @RequestParam(value = "documentType", required = false, defaultValue = "generic") String documentType,
                                @RequestParam(value = "enableChunking", required = false) Boolean enableChunking,
                                @RequestParam(value = "documentId", required = false) Long documentId, 
                                @RequestParam(value = "ocrTaskId", required = false) String ocrTaskId,
                                RedirectAttributes redirectAttributes) {
        
        if (ocrText == null || ocrText.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("ocrError", "No OCR text provided for enhancement.");
            return "redirect:/ocr";
        }
        if (modelName == null || modelName.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("ocrError", "Please select an LLM model for text enhancement.");
            return "redirect:/ocr";
        }
        
        redirectAttributes.addFlashAttribute("chunkingEnabled", enableChunking != null && enableChunking);
        try {            
            logger.info("Enhancing OCR text with model: {} and document type: {}, chunking: {}", 
                        modelName, documentType, enableChunking);
            
            String langCodeForPrompt = "eng"; 
            if ("Italian".equalsIgnoreCase(language)) langCodeForPrompt = "ita";

            String effectiveDocType = documentType;
            if ("ita".equalsIgnoreCase(langCodeForPrompt) && "literary".equals(documentType)) {
                effectiveDocType = "italian-literary";
            }
            String customPrompt = "generic".equals(effectiveDocType) ? null : 
                ollamaService.getSpecializedPrompt(effectiveDocType, ocrText);
            
            OllamaService.EnhancementResult result = ollamaService.enhanceText(
                ocrText, modelName, customPrompt, enableChunking);
            
            redirectAttributes.addFlashAttribute("originalOcrText", ocrText); 
            redirectAttributes.addFlashAttribute("ocrResult", result.getEnhancedText()); 
            redirectAttributes.addFlashAttribute("originalFilename", originalFilename);
            redirectAttributes.addFlashAttribute("language", language); 
            redirectAttributes.addFlashAttribute("isEnhanced", true); 
            redirectAttributes.addFlashAttribute("enhancementModel", modelName);
            redirectAttributes.addFlashAttribute("documentType", effectiveDocType);
            redirectAttributes.addFlashAttribute("showComparison", true);

            if (ocrTaskId != null && documentId == null) { 
                redirectAttributes.addFlashAttribute("ocrTaskId", ocrTaskId);
            }
            
            if (result.wasAnalysisFixed()) {
                redirectAttributes.addFlashAttribute("analysisDetected", true);
            }

            if (documentId != null) {
                OcrTextDocument existingDoc = ocrTextDocumentRepository.findById(documentId).orElse(null);
                if (existingDoc != null) {
                    existingDoc.setEnhancedText(result.getEnhancedText());
                    existingDoc.setEnhancementModel(modelName);
                    existingDoc.setDocumentType(effectiveDocType);
                    existingDoc.setIsEnhanced(true);
                    if (existingDoc.getExtractedText() == null || existingDoc.getExtractedText().isEmpty() || !existingDoc.getExtractedText().equals(ocrText)) {
                         existingDoc.setExtractedText(ocrText); 
                    }
                    ocrTextDocumentRepository.save(existingDoc);
                    logger.info("Updated saved document ID {} with new enhancement.", documentId);
                    return "redirect:/ocr/documents/" + documentId; // Keep this redirect for now
                } else {
                    logger.warn("Document ID {} provided for enhancement, but document not found.", documentId);
                    redirectAttributes.addFlashAttribute("ocrError", "Failed to update saved document: Document not found.");
                    return "redirect:/ocr"; 
                }
            } else {
                return "redirect:/ocr"; 
            }
        } catch (Exception e) {
            logger.error("Error enhancing OCR text: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("ocrError", "Failed to enhance text: " + e.getMessage());
            redirectAttributes.addFlashAttribute("isEnhanced", false); 
            if (documentId != null) {
                return "redirect:/ocr/documents/" + documentId; // Keep this redirect
            }
            return "redirect:/ocr";
        }
    }

    @PostMapping("/show-original-transient")
    public String showOriginalTransientText(
            @RequestParam("textToShow") String textToShow,
            @RequestParam(name = "originalFilename", required = false) String originalFilename,
            @RequestParam(name = "language", required = false) String language,
            @RequestParam(name = "ocrTaskId", required = false) String ocrTaskId,
            @RequestParam(name = "currentModelName", required = false) String currentModelName, 
            @RequestParam(name = "currentDocType", required = false) String currentDocType,
            @RequestParam(name = "currentChunking", required = false) Boolean currentChunking,
            RedirectAttributes redirectAttributes) {

        redirectAttributes.addFlashAttribute("ocrResult", textToShow);
        redirectAttributes.addFlashAttribute("originalFilename", originalFilename);
        redirectAttributes.addFlashAttribute("language", language);
        redirectAttributes.addFlashAttribute("isEnhanced", false);
        redirectAttributes.addFlashAttribute("showComparison", false);
        redirectAttributes.addFlashAttribute("originalOcrText", null); 

        if (ocrTaskId != null) { 
            redirectAttributes.addFlashAttribute("ocrTaskId", ocrTaskId); 
        }
        if (currentModelName != null) redirectAttributes.addFlashAttribute("enhancementModel", currentModelName);   
        if (currentDocType != null) redirectAttributes.addFlashAttribute("documentType", currentDocType);
        if (currentChunking != null) redirectAttributes.addFlashAttribute("chunkingEnabled", currentChunking);
        
        return "redirect:/ocr";
    }
    
    private boolean isSupportedImageType(String filename) {
        return filename.endsWith(".png") || filename.endsWith(".jpg") || filename.endsWith(".jpeg") || 
               filename.endsWith(".tif") || filename.endsWith(".tiff") || filename.endsWith(".pdf");
    }
    
    private static class ByteArrayMultipartFile implements MultipartFile {
        private final byte[] content;
        private final String name;
        
        public ByteArrayMultipartFile(byte[] content, String name) {
            this.content = content;
            this.name = name;
        }
        
        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() {
            if (name.toLowerCase().endsWith(".pdf")) return "application/pdf";
            else if (name.toLowerCase().endsWith(".png")) return "image/png";
            else if (name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".jpeg")) return "image/jpeg";
            else if (name.toLowerCase().endsWith(".tif") || name.toLowerCase().endsWith(".tiff")) return "image/tiff";
            return "application/octet-stream";
        }
        @Override public boolean isEmpty() { return content == null || content.length == 0; }
        @Override public long getSize() { return content != null ? content.length : 0; }
        @Override public byte[] getBytes() throws IOException { return content; }
        @Override public java.io.InputStream getInputStream() throws IOException { return new java.io.ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {
                fos.write(content);
            }
        }
    }

    // Test endpoints
    @GetMapping("/test-literary")
    @ResponseBody
    public ResponseEntity<?> testLiteraryEnhancement() {
        String literaryText = """
            a book of 7 pages: wWwwWw.picatrix.com
            
            Un'aviditàinfinita,unaseteedunagioiadivivereindicibiliabitavano1mieisguardie1mieinervi,illuminandoilmondocircostanteconunachiarezzaabbagliante!21.Lesentoilpolso.Eratornatoquasinormale.Iobanchiattornoa_Livianoneranoaltrocheombre.Dietroqueste
             nnegativioscuri,trascurabili,sostituibili,stavaunaltroaltareeunaltraLivia.No.Glioriginalideglioggettiedellepersonelacuivitaordinariacimostralefotomalriuscite.Questeimmagini_luminose,eteree,rendevanol'ideadellaleggerezza,l'eleganzadellaballerine,abolentiognipesantezza.Sprofondavonelsensodimeravigliadavantiquestasecondarealtàchesi
             aprivaaimieiocchi.
            """;
        logger.info("Testing specialized Italian literary text enhancement");
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("original", literaryText);
            String italianLiteraryPrompt = ollamaService.getSpecializedPrompt("italian-literary", literaryText);
            OllamaService.EnhancementResult italianResult = ollamaService.enhanceText(literaryText, "llama3", italianLiteraryPrompt, false);
            response.put("enhanced_italian_literary", italianResult.getEnhancedText());
            response.put("italian_literary_fix_applied", italianResult.wasAnalysisFixed());
            String standardLiteraryPrompt = ollamaService.getSpecializedPrompt("literary", literaryText);
            OllamaService.EnhancementResult standardResult = ollamaService.enhanceText(literaryText, "llama3", standardLiteraryPrompt, false);
            response.put("enhanced_standard_literary", standardResult.getEnhancedText());
            response.put("standard_literary_fix_applied", standardResult.wasAnalysisFixed());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error in testLiteraryEnhancement: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/test-chunking")
    @ResponseBody
    public ResponseEntity<?> testChunking() {
        StringBuilder largeText = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            largeText.append("This is sentence number ").append(i).append(" in a very long document. ");
            largeText.append("It needs to be chunked for proper processing by the LLM. ");
            largeText.append("Each chunk should be processed independently and then recombined. ");
            largeText.append("L'elefante è un animale molto grande con una proboscide. ");
            largeText.append("The quick brown fox jumps over the lazy dog. ");
            largeText.append("Questa è una frase in italiano per testare il chunking multilingue. \n\n");
        }
        String testText = largeText.toString();
        logger.info("Testing chunking functionality with a simulated {} character document", testText.length());
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("original_length", testText.length());
            OllamaService.EnhancementResult chunkedResult = ollamaService.enhanceText(testText, "llama3", null, true);
            response.put("enhanced_with_chunking", chunkedResult.getEnhancedText());
            response.put("enhanced_with_chunking_length", chunkedResult.getEnhancedText().length());
            response.put("chunking_fix_applied", chunkedResult.wasAnalysisFixed());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error in testChunking: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    // --- Merged from OcrDocumentController ---

    @PostMapping("/documents/save")
    @ResponseBody
    public ResponseEntity<?> saveDocument(
            @RequestParam("originalFilename") String originalFilename,
            @RequestParam("extractedText") String extractedText,
            @RequestParam("enhancedText") String enhancedText,
            @RequestParam("language") String language,
            @RequestParam("enhancementModel") String enhancementModel,
            @RequestParam("documentType") String documentType) {
        
        try {
            logger.info("Saving OCR document: {}, language: {}, model: {}, type: {}", 
                    originalFilename, language, enhancementModel, documentType);
            
            String languageCode = language;
            if ("English".equalsIgnoreCase(language)) {
                languageCode = "eng";
            } else if ("Italian".equalsIgnoreCase(language)) {
                languageCode = "ita";
            }
            
            OcrTextDocument document = new OcrTextDocument(
                    originalFilename, 
                    extractedText, 
                    enhancedText, 
                    languageCode, 
                    enhancementModel, 
                    documentType);
            
            OcrTextDocument savedDoc = ocrTextDocumentRepository.save(document);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("documentId", savedDoc.getId());
            response.put("message", "Document saved successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error saving OCR document: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to save document: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/documents/save-original")
    @ResponseBody
    public ResponseEntity<?> saveOriginalDocument(
            @RequestParam("originalFilename") String originalFilename,
            @RequestParam("extractedText") String extractedText,
            @RequestParam("language") String language) {
        
        try {
            logger.info("Saving original OCR document: {}, language: {}", originalFilename, language);
            
            String languageCode = language;
            if ("English".equalsIgnoreCase(language)) {
                languageCode = "eng";
            } else if ("Italian".equalsIgnoreCase(language)) {
                languageCode = "ita";
            }
            
            OcrTextDocument document = new OcrTextDocument(originalFilename, extractedText, languageCode);
            OcrTextDocument savedDoc = ocrTextDocumentRepository.save(document);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("documentId", savedDoc.getId());
            response.put("message", "Original document saved successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error saving original OCR document: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to save document: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping("/documents/{id}")
    public String viewDocument(@PathVariable Long id,
                               @RequestParam(name = "showOriginal", required = false) Boolean showOriginal,
                               Model model, RedirectAttributes redirectAttributes) {
        Optional<OcrTextDocument> documentOpt = ocrTextDocumentRepository.findById(id);
        
        if (documentOpt.isPresent()) {
            OcrTextDocument document = documentOpt.get();
            logger.info("Viewing OCR document: {}, ID: {}, Show Original: {}", document.getOriginalFilename(), id, showOriginal);
            
            boolean displayEnhanced = document.getIsEnhanced() && (showOriginal == null || !showOriginal);

            String languageDisplay = document.getLanguageUsed();
            if ("eng".equalsIgnoreCase(document.getLanguageUsed())) {
                languageDisplay = "English";
            } else if ("ita".equalsIgnoreCase(document.getLanguageUsed())) {
                languageDisplay = "Italian";
            }
            
            model.addAttribute("ocrResult", displayEnhanced ?
                    document.getEnhancedText() : document.getExtractedText());
            model.addAttribute("originalFilename", document.getOriginalFilename());
            model.addAttribute("language", languageDisplay);
            model.addAttribute("createdAt", document.getCreatedAt());
            model.addAttribute("currentDocumentId", document.getId()); 
            
            if (displayEnhanced) {
                model.addAttribute("isEnhanced", true);
                model.addAttribute("enhancementModel", document.getEnhancementModel());
                model.addAttribute("documentType", document.getDocumentType());
                model.addAttribute("originalOcrText", document.getExtractedText()); 
                model.addAttribute("showComparison", true);
            } else {
                model.addAttribute("isEnhanced", false);
                model.addAttribute("originalOcrText", null);
                model.addAttribute("showComparison", false);
            }
            
            model.addAttribute("availableModels", ollamaService.getAvailableModels());
            model.addAttribute("hasOcrResult", model.containsAttribute("ocrResult"));
            model.addAttribute("hasOcrError", false);
            model.addAttribute("hasOcrTaskId", false); 
            
            return "ocr"; 
        } else {
            logger.warn("OCR document not found: ID {}", id);
            redirectAttributes.addFlashAttribute("ocrError", "Document not found with ID: " + id);
            return "redirect:/ocr";
        }
    }
    
    @PostMapping("/documents/{id}/delete")
    @ResponseBody
    public ResponseEntity<?> deleteDocument(@PathVariable Long id) {
        try {
            Optional<OcrTextDocument> documentOpt = ocrTextDocumentRepository.findById(id);
            
            if (documentOpt.isPresent()) {
                OcrTextDocument document = documentOpt.get();
                logger.info("Deleting OCR document: {}, ID: {}", document.getOriginalFilename(), id);
                
                ocrTextDocumentRepository.deleteById(id);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Document deleted successfully");
                
                return ResponseEntity.ok(response);
            } else {
                logger.warn("OCR document not found for deletion: ID {}", id);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Document not found with ID: " + id);
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Error deleting OCR document: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to delete document: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}