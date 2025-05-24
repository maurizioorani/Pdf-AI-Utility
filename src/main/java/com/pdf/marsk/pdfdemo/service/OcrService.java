package com.pdf.marsk.pdfdemo.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper; // Added for direct text extraction
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.pdf.marsk.pdfdemo.model.OcrTextDocument; // Added import
import com.pdf.marsk.pdfdemo.repository.OcrTextDocumentRepository; // Added import

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

@Service
public class OcrService {

    private static final Logger logger = LoggerFactory.getLogger(OcrService.class);
    private final ITesseract tesseractInstance;
    
    @Autowired
    private ProgressTrackingService progressTrackingService;

    @Autowired // Added repository injection
    private OcrTextDocumentRepository ocrTextDocumentRepository;

    public OcrService() {
        tesseractInstance = new Tesseract();
        
        // Set the path to the Tesseract data directory (where tessdata is located)
        try {
            // Set tessdata directory path - first try app root directory
            String userDir = System.getProperty("user.dir");
            File tessDataDir = new File(userDir, "tessdata");
            
            if (tessDataDir.exists() && tessDataDir.isDirectory()) {
                logger.info("Using tessdata directory: {}", tessDataDir.getAbsolutePath());
                tesseractInstance.setDatapath(tessDataDir.getAbsolutePath());
            } else {
                logger.warn("Tessdata directory not found at: {}. Using system default.", tessDataDir.getAbsolutePath());
            }
            
            tesseractInstance.setLanguage("eng"); // Default to English, can be made configurable
            
            // Add additional options to improve PDF processing
            tesseractInstance.setPageSegMode(1); // Automatic page segmentation with OSD
            tesseractInstance.setOcrEngineMode(1); // Neural net based LSTM engine only
            
            logger.info("Initialized Tesseract OCR service");
        } catch (Exception e) {
            logger.error("Error configuring Tesseract OCR: {}", e.getMessage());
        }
    }    
    
    public String performOcr(MultipartFile file) throws IOException, TesseractException {
        return performOcr(file, "eng"); // Default to English
    }
    
    /**
     * Performs OCR on a file using the specified language.
     * 
     * @param file The file to process
     * @param language The language to use for OCR
     * @return The extracted text
     * @throws IOException If there is an error reading/writing the file
     * @throws TesseractException If there is an error during OCR processing
     */
    public String performOcr(MultipartFile file, String language) throws IOException, TesseractException {
        return performOcr(file, language, null);
    }
    
    /**
     * Performs OCR on a file using the specified language and tracks progress.
     * 
     * @param file The file to process
     * @param language The language to use for OCR
     * @param taskId The task ID for progress tracking (optional)
     * @return The extracted text
     * @throws IOException If there is an error reading/writing the file
     * @throws TesseractException If there is an error during OCR processing
     */
    public String performOcr(MultipartFile file, String language, String taskId) throws IOException, TesseractException {
        Path tempFile = null;
        
        try {
            // Set the language
            tesseractInstance.setLanguage(language);
            logger.info("Setting OCR language to: {}", language);
            
            // Create a temporary file from the MultipartFile
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                originalFilename = "unknown_file";
            }
            
            tempFile = Files.createTempFile("ocr_temp_", "_" + originalFilename);
            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                fos.write(file.getBytes());
            }
            
            logger.info("Performing OCR on file: {} with language: {}", originalFilename, language);
            
            if (originalFilename.toLowerCase().endsWith(".pdf")) {
                return processPdfFile(tempFile.toFile(), language, taskId);
            } else {
                // For single image files, create a simple task with one page
                String trackingTaskId = taskId;
                if (trackingTaskId == null && progressTrackingService != null) {
                    // Pass the language when creating the task for a single image
                    trackingTaskId = progressTrackingService.createOcrTask(originalFilename, 1, language);
                    progressTrackingService.updateOcrTaskProgress(trackingTaskId, 0, "Processing image...");
                }
                
                // Process as regular image file
                String result = tesseractInstance.doOCR(tempFile.toFile());
                
                // Update progress if we're tracking
                if (trackingTaskId != null && progressTrackingService != null) {
                    progressTrackingService.updateOcrTaskProgress(trackingTaskId, 1, "Image processing complete");
                    // The 'result' (extracted text) is now also the message for completion
                    progressTrackingService.completeTask(trackingTaskId, true, result);
                }

                // Save the OCR result
                if (result != null && !result.isEmpty()) {
                    OcrTextDocument doc = new OcrTextDocument(originalFilename, result, language);
                    ocrTextDocumentRepository.save(doc);
                    logger.info("Saved OCR result for file: {}", originalFilename);
                }
                
                return result;
            }
        } catch (IOException e) {
            logger.error("IOException during OCR file handling for {}: {}", 
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown file", 
                    e.getMessage());
            
            // Update progress if we're tracking
            if (taskId != null && progressTrackingService != null) {
                progressTrackingService.completeTask(taskId, false, "Error: " + e.getMessage());
            }
            
            throw e; // Re-throw to be handled by controller
        } catch (TesseractException e) {
            logger.error("TesseractException during OCR for {}: {}", 
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown file", 
                    e.getMessage());
            logger.error("Tesseract error details: {}", e.getMessage());
            if (e.getCause() != null) {
                logger.error("Tesseract cause: {}", e.getCause().getMessage());
            }
            
            // Update progress if we're tracking
            if (taskId != null && progressTrackingService != null) {
                progressTrackingService.completeTask(taskId, false, "OCR Error: " + e.getMessage());
            }
            
            throw e; // Re-throw
        } finally {
            // Clean up the temporary file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    logger.debug("Temporary OCR file deleted: {}", tempFile.toString());
                } catch (IOException e) {
                    logger.warn("Could not delete temporary OCR file {}: {}", tempFile.toString(), e.getMessage());
                }
            }
        }
    }

    /**
     * Extracts text directly from a PDF file without performing OCR.
     * Uses PDFBox's PDFTextStripper.
     *
     * @param file The MultipartFile representing the PDF.
     * @return The extracted text, or an empty string if no text could be extracted or an error occurred.
     * @throws IOException If there is an error reading the file.
     */
    public String extractTextDirectly(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            logger.warn("Attempted to extract text directly from a null or empty file.");
            return "";
        }
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.pdf";
        logger.info("Attempting direct text extraction from PDF: {}", originalFilename);

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("direct_text_extract_", "_" + originalFilename);
            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                fos.write(file.getBytes());
            }

            try (PDDocument document = PDDocument.load(tempFile.toFile())) {
                if (document.isEncrypted()) {
                    logger.warn("PDF file {} is encrypted. Cannot extract text directly.", originalFilename);
                    // Optionally, you could try to decrypt with an empty password, but this often fails.
                    // document.setAllSecurityToBeRemoved(true); // Requires BouncyCastle
                    return ""; // Or throw a specific exception
                }
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                logger.info("Successfully extracted text directly from PDF: {}. Length: {} chars.", originalFilename, text.length());
                
                // Optionally save this extracted text similar to how OCR results are saved
                // OcrTextDocument doc = new OcrTextDocument(originalFilename, text, "N/A_DIRECT_EXTRACTION");
                // ocrTextDocumentRepository.save(doc);

                return text;
            }
        } catch (IOException e) {
            logger.error("IOException during direct text extraction for {}: {}", originalFilename, e.getMessage(), e);
            throw e; // Re-throw to be handled by the caller
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    logger.warn("Could not delete temporary file {} used for direct text extraction: {}", tempFile.toString(), e.getMessage());
                }
            }
        }
    }
    
    private String processPdfFile(File pdfFile) throws IOException, TesseractException {
        return processPdfFile(pdfFile, "eng", null); // Default to English, no progress tracking
    }
      private String processPdfFile(File pdfFile, String language) throws IOException, TesseractException {
        return processPdfFile(pdfFile, language, null); // No progress tracking
    }
    
    /**
     * Process a PDF file for OCR with progress tracking.
     * 
     * @param pdfFile The PDF file to process
     * @param language The language to use for OCR
     * @param taskId The task ID for progress tracking (optional)
     * @return The extracted text
     * @throws IOException If there is an error reading/writing the file
     * @throws TesseractException If there is an error during OCR processing
     */
    private String processPdfFile(File pdfFile, String language, String taskId) throws IOException, TesseractException {
        StringBuilder extractedText = new StringBuilder();
        String trackingTaskId = taskId;
        
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            
            logger.info("Processing PDF with {} pages using language: {}", pageCount, language);
            
            // Create or update task for progress tracking
            if (trackingTaskId != null && progressTrackingService != null) {
                // Task already exists (created by controller with initial totalPages=0), update it
                progressTrackingService.updateOcrTaskTotalPages(trackingTaskId, pageCount);
                progressTrackingService.updateOcrTaskProgress(trackingTaskId, 0, "Starting PDF processing...");
            } else if (progressTrackingService != null) {
                // This case should ideally not be hit if taskId is always passed from controller for PDFs
                // Pass the language when creating the task
                trackingTaskId = progressTrackingService.createOcrTask(pdfFile.getName(), pageCount, language);
                progressTrackingService.updateOcrTaskProgress(trackingTaskId, 0, "Starting PDF processing...");
            }
            
            // Process each page of the PDF
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                // Update progress
                if (trackingTaskId != null && progressTrackingService != null) {
                    progressTrackingService.updateOcrTaskProgress(
                            trackingTaskId,
                            pageIndex,
                            "Processing page " + (pageIndex + 1) + " of " + pageCount
                    );
                }
                
                logger.debug("Processing page {} of {}", pageIndex + 1, pageCount);
                
                // Render PDF page to image with higher DPI for better OCR results (increase from 300 to 400)
                BufferedImage image = pdfRenderer.renderImageWithDPI(pageIndex, 400);
                
                // Apply image pre-processing if needed (for scanned documents)
                BufferedImage processedImage = preprocessImageForOcr(image);
                
                // Save the image temporarily
                Path tempImageFile = Files.createTempFile("pdf_page_" + pageIndex + "_", ".png");
                try {
                    ImageIO.write(processedImage, "PNG", tempImageFile.toFile());
                    
                    // Configure Tesseract for better accuracy
                    configureTesseractForPage(language);
                    
                    // Perform OCR on the image
                    String pageText = tesseractInstance.doOCR(tempImageFile.toFile());
                    
                    // Add page number if multiple pages
                    if (pageCount > 1) {
                        extractedText.append("--- Page ").append(pageIndex + 1).append(" ---\n");
                    }
                    
                    extractedText.append(pageText).append("\n");
                    
                    // Update progress to include the just-completed page
                    if (trackingTaskId != null && progressTrackingService != null) {
                        progressTrackingService.updateOcrTaskProgress(
                                trackingTaskId,
                                pageIndex + 1,
                                "Completed page " + (pageIndex + 1) + " of " + pageCount
                        );
                    }
                } catch (TesseractException e) {
                    logger.error("Error during OCR processing of PDF page {}: {}", pageIndex + 1, e.getMessage());
                    
                    // Update progress to reflect the error
                    if (trackingTaskId != null && progressTrackingService != null) {
                        progressTrackingService.updateOcrTaskProgress(
                                trackingTaskId,
                                pageIndex,
                                "Error processing page " + (pageIndex + 1) + ": " + e.getMessage()
                        );
                    }
                    
                    throw e;
                } finally {
                    // Clean up temporary image file
                    Files.deleteIfExists(tempImageFile);
                }
            }
              logger.info("Successfully processed PDF with {} pages", pageCount);
              
            // Mark task as completed
            if (trackingTaskId != null && progressTrackingService != null) {
                // The 'extractedText.toString()' is the message for completion
                progressTrackingService.completeTask(trackingTaskId, true, extractedText.toString());
            }

            // Save the OCR result for PDF
            if (extractedText.length() > 0) {
                OcrTextDocument doc = new OcrTextDocument(pdfFile.getName(), extractedText.toString(), language);
                ocrTextDocumentRepository.save(doc);
                logger.info("Saved OCR result for PDF file: {}", pdfFile.getName());
            }
            
            return extractedText.toString();
        } catch (IOException e) {
            logger.error("Error processing PDF file: {}", e.getMessage());
            
            // Update progress to reflect the error
            if (trackingTaskId != null && progressTrackingService != null) {
                progressTrackingService.completeTask(trackingTaskId, false, "Error: " + e.getMessage());
            }
            
            throw new IOException("Error processing PDF file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Preprocesses an image to enhance OCR accuracy.
     * Applies various filters and adjustments to improve text recognition.
     * 
     * @param image The original image to process
     * @return The processed image optimized for OCR
     */
    private BufferedImage preprocessImageForOcr(BufferedImage image) {
        // Create a copy of the image to work with
        BufferedImage processedImage = new BufferedImage(
                image.getWidth(), 
                image.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        
        Graphics2D g2d = processedImage.createGraphics();
        
        // Fill background with white for better contrast
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
        
        // Draw the original image over the white background
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        
        // Apply a light sharpen filter to enhance text edges
        float[] sharpenKernel = {
             0.0f, -0.2f,  0.0f,
            -0.2f,  1.8f, -0.2f,
             0.0f, -0.2f,  0.0f
        };
        
        BufferedImageOp sharpenOp = new ConvolveOp(new Kernel(3, 3, sharpenKernel));
        processedImage = sharpenOp.filter(processedImage, null);
        
        return processedImage;
    }
      /**
     * Configures Tesseract parameters for optimal OCR based on the current page being processed.
     * 
     * @param language The OCR language
     */
    private void configureTesseractForPage(String language) {
        // Language is set at the beginning of performOcr or processPdfFile.
        // No need to set it again here per page if it's the same language.
        // tesseractInstance.setLanguage(language);
        
        // Optimize Tesseract parameters based on the content
        tesseractInstance.setPageSegMode(1); // Automatic page segmentation with OSD
        
        // Set DPI to improve recognition (can be adjusted based on the image quality)
        tesseractInstance.setVariable("user_defined_dpi", "400");
        
        // Additional parameters to improve accuracy
        tesseractInstance.setVariable("tessedit_char_whitelist", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,;:!?()-+*/_'\"@#$%&=[]{}|<>àèìòùÀÈÌÒÙáéíóúÁÉÍÓÚ");
    }
}