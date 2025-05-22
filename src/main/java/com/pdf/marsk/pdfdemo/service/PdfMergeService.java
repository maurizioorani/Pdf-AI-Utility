package com.pdf.marsk.pdfdemo.service;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for merging PDF files.
 * Uses Apache PDFBox to combine multiple PDF files into a single document.
 */
@Service
public class PdfMergeService {

    private static final Logger logger = LoggerFactory.getLogger(PdfMergeService.class);
    private static final int MAX_MEMORY_THRESHOLD = 50 * 1024 * 1024; // 50MB threshold for memory optimization

    /**
     * Merges multiple PDF files into a single PDF document.
     * 
     * @param pdfFiles List of MultipartFile objects representing PDF files to merge
     * @return Byte array containing the merged PDF data
     * @throws IOException If an I/O error occurs during merging
     * @throws IllegalArgumentException If input validation fails
     */
    public byte[] mergePdfs(List<MultipartFile> pdfFiles) throws IOException {
        // Validate input
        if (pdfFiles == null || pdfFiles.size() < 2) {
            throw new IllegalArgumentException("At least two PDF files are required for merging.");
        }

        // Initialize merger and output stream
        PDFMergerUtility pdfMerger = new PDFMergerUtility();
        ByteArrayOutputStream mergedPdfOutputStream = new ByteArrayOutputStream();
        pdfMerger.setDestinationStream(mergedPdfOutputStream);
        
        // Prepare documents for merging
        List<PDDocument> validDocuments = new ArrayList<>();
        int totalSize = 0;

        try {
            // First pass: validate all files
            for (MultipartFile pdfFile : pdfFiles) {
                // Skip empty files
                if (pdfFile.isEmpty()) {
                    logger.warn("Skipping empty file during merge: {}", pdfFile.getOriginalFilename());
                    continue;
                }
                
                // Validate content type
                if (!"application/pdf".equalsIgnoreCase(pdfFile.getContentType())) {
                    logger.warn("Skipping non-PDF file during merge: {} (type: {})", 
                            pdfFile.getOriginalFilename(), pdfFile.getContentType());
                    throw new IllegalArgumentException("Invalid file type provided: " + 
                            pdfFile.getOriginalFilename() + ". Only PDF files are allowed.");
                }
                
                totalSize += pdfFile.getSize();
                
                // Load and validate the PDF document
                try {
                    PDDocument doc = PDDocument.load(pdfFile.getInputStream());
                    
                    // Check if document is encrypted
                    if (doc.isEncrypted()) {
                        doc.close();
                        throw new IllegalArgumentException("Encrypted or password-protected PDF detected: " + 
                                pdfFile.getOriginalFilename() + ". Please remove password protection first.");
                    }
                    
                    // Check if document has pages
                    if (doc.getNumberOfPages() == 0) {
                        logger.warn("Skipping PDF with zero pages: {}", pdfFile.getOriginalFilename());
                        doc.close();
                        continue;
                    }
                    
                    // Add to valid documents list
                    validDocuments.add(doc);
                    logger.debug("Added valid PDF document: {} ({} pages)", 
                            pdfFile.getOriginalFilename(), doc.getNumberOfPages());
                    
                } catch (IOException e) {
                    logger.error("Invalid PDF file encountered: {}", pdfFile.getOriginalFilename(), e);
                    throw new IOException("Invalid PDF file: " + pdfFile.getOriginalFilename(), e);
                }
            }            // Check if we have enough valid documents to merge
            if (validDocuments.size() < 2) {
                throw new IOException("No valid PDF files were provided for merging after validation.");
            }// Decide on memory optimization strategy
            boolean useMemoryOptimization = totalSize > MAX_MEMORY_THRESHOLD;
            if (useMemoryOptimization) {
                logger.info("Using memory optimization for large PDF merge (total size: {} bytes)", totalSize);
                pdfMerger.mergeDocuments(null); // Use null for memory optimization
            } else {
                logger.info("Standard PDF merge for {} documents", validDocuments.size());
                  // Create a fresh document for merging into
                PDDocument mergedDoc = new PDDocument();
                
                try {
                    // Add all documents to merger
                    for (PDDocument doc : validDocuments) {
                        pdfMerger.appendDocument(mergedDoc, doc);
                    }
                    
                    // Save the merged document
                    mergedDoc.save(mergedPdfOutputStream);
                } finally {
                    mergedDoc.close();
                }
            }

            logger.info("Successfully merged {} PDF files.", validDocuments.size());
            return mergedPdfOutputStream.toByteArray();

        } finally {
            // Clean up resources
            for (PDDocument doc : validDocuments) {
                try {
                    doc.close();
                } catch (IOException e) {
                    logger.warn("Failed to close PDF document during cleanup", e);
                }
            }
            
            try {
                mergedPdfOutputStream.close();
            } catch (IOException e) {
                logger.warn("Failed to close output stream during PDF merge cleanup", e);
            }
        }
    }
}