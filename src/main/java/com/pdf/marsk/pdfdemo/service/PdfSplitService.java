package com.pdf.marsk.pdfdemo.service;

import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class PdfSplitService {

    private static final Logger logger = LoggerFactory.getLogger(PdfSplitService.class);

    public byte[] splitPdfEveryPage(MultipartFile pdfFile, String originalFilenameBase) throws IOException {
        if (pdfFile == null || pdfFile.isEmpty()) {
            throw new IllegalArgumentException("A PDF file is required for splitting.");
        }
        if (!"application/pdf".equalsIgnoreCase(pdfFile.getContentType())) {
            logger.warn("Invalid file type for splitting: {} (type: {})", pdfFile.getOriginalFilename(), pdfFile.getContentType());
            throw new IllegalArgumentException("Invalid file type provided: " + pdfFile.getOriginalFilename() + ". Only PDF files are allowed.");
        }

        try (InputStream inputStream = pdfFile.getInputStream();
             PDDocument document = PDDocument.load(inputStream)) {

            if (document.isEncrypted()) {
                logger.warn("Cannot split an encrypted PDF: {}", pdfFile.getOriginalFilename());
                throw new IOException("Encrypted PDFs cannot be split with this method.");
            }

            Splitter splitter = new Splitter();
            List<PDDocument> splitDocuments = splitter.split(document);
            
            if (splitDocuments.isEmpty()) {
                throw new IOException("Splitting the PDF resulted in no documents.");
            }

            ByteArrayOutputStream zipOutputStream = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(zipOutputStream)) {
                for (int i = 0; i < splitDocuments.size(); i++) {
                    PDDocument singlePageDoc = splitDocuments.get(i);
                    try (ByteArrayOutputStream pageOutputStream = new ByteArrayOutputStream()) {
                        singlePageDoc.save(pageOutputStream);
                        
                        ZipEntry zipEntry = new ZipEntry(originalFilenameBase + "_page_" + (i + 1) + ".pdf");
                        zos.putNextEntry(zipEntry);
                        zos.write(pageOutputStream.toByteArray());
                        zos.closeEntry();
                    } finally {
                        singlePageDoc.close();
                    }
                }
            }
            logger.info("Successfully split PDF {} into {} pages.", pdfFile.getOriginalFilename(), splitDocuments.size());
            return zipOutputStream.toByteArray();
        }
    }
 
    public int getPdfPageCount(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream cannot be null.");
        }
        try (PDDocument document = PDDocument.load(inputStream)) {
            if (document.isEncrypted()) {
                // Consider if a specific exception type or handling is better here.
                // For now, IOException to indicate an issue processing the PDF.
                throw new IOException("Encrypted PDFs cannot be processed for page count without a password.");
            }
            return document.getNumberOfPages();
        } catch (IOException e) {
            logger.error("Error reading PDF for page count: {}", e.getMessage());
            throw e; // Re-throw to be handled by controller
        }
    }
 
    public byte[] splitPdfByRanges(MultipartFile pdfFile, String ranges, String originalFilenameBase) throws IOException {
        if (pdfFile == null || pdfFile.isEmpty()) {
            throw new IllegalArgumentException("A PDF file is required for splitting.");
        }
        if (!"application/pdf".equalsIgnoreCase(pdfFile.getContentType())) {
            logger.warn("Invalid file type for splitting by range: {} (type: {})", pdfFile.getOriginalFilename(), pdfFile.getContentType());
            throw new IllegalArgumentException("Invalid file type provided: " + pdfFile.getOriginalFilename() + ". Only PDF files are allowed.");
        }
 
        try (InputStream inputStream = pdfFile.getInputStream();
             PDDocument document = PDDocument.load(inputStream)) {
 
            if (document.isEncrypted()) {
                logger.warn("Cannot split an encrypted PDF by range: {}", pdfFile.getOriginalFilename());
                throw new IOException("Encrypted PDFs cannot be split with this method.");
            }
 
            int totalPages = document.getNumberOfPages();
            if (totalPages == 0) {
                throw new IOException("The provided PDF file has no pages.");
            }
 
            String normalizedRanges = ranges.replaceAll("\\s+", "").replace(';', ',');
            String[] rangeParts = normalizedRanges.split(",");
 
            List<PDDocument> splitDocuments = new ArrayList<>();
            List<String> partNamesForZip = new ArrayList<>(); // For naming files within the ZIP
 
            for (String part : rangeParts) {
                if (part.isEmpty()) {
                    continue; // Skip empty parts that might result from "1,,2" or trailing commas
                }
 
                PDDocument currentSplitDoc = new PDDocument();
                List<Integer> pagesToIncludeInThisDoc = new ArrayList<>();
                String currentPartName;
 
                if (part.contains("-")) { // Range like "1-5"
                    String[] ends = part.split("-", 2); // Split only on the first hyphen
                    if (ends.length != 2 || ends[0].isEmpty() || ends[1].isEmpty()) {
                        closeDocuments(splitDocuments); document.close(); // Clean up before throwing
                        throw new IllegalArgumentException("Invalid range format: \"" + part + "\" in \"" + ranges + "\"");
                    }
                    try {
                        int startPage = Integer.parseInt(ends[0]);
                        int endPage = Integer.parseInt(ends[1]);
 
                        if (startPage <= 0 || endPage > totalPages || startPage > endPage) {
                            closeDocuments(splitDocuments); document.close();
                            throw new IllegalArgumentException("Invalid page numbers in range: \"" + part + "\". PDF has " + totalPages + " pages.");
                        }
                        for (int i = startPage; i <= endPage; i++) {
                            pagesToIncludeInThisDoc.add(i);
                        }
                        currentPartName = "pages_" + startPage + "-" + endPage;
                    } catch (NumberFormatException e) {
                        closeDocuments(splitDocuments); document.close();
                        throw new IllegalArgumentException("Invalid page numbers in range: \"" + part + "\"", e);
                    }
                } else { // Single page like "3"
                    try {
                        int pageNum = Integer.parseInt(part);
                        if (pageNum <= 0 || pageNum > totalPages) {
                            closeDocuments(splitDocuments); document.close();
                            throw new IllegalArgumentException("Invalid page number: " + pageNum + ". PDF has " + totalPages + " pages.");
                        }
                        pagesToIncludeInThisDoc.add(pageNum);
                        currentPartName = "page_" + pageNum;
                    } catch (NumberFormatException e) {
                        closeDocuments(splitDocuments); document.close();
                        throw new IllegalArgumentException("Invalid page number: \"" + part + "\"", e);
                    }
                }
 
                if (pagesToIncludeInThisDoc.isEmpty()) {
                    // This should not happen if logic above is correct and part is not empty
                    currentSplitDoc.close(); // Close the just created empty doc
                    logger.warn("No pages were selected for range part: \"{}\"", part);
                    continue;
                }
 
                for (int pageNum : pagesToIncludeInThisDoc) {
                    currentSplitDoc.addPage(document.getPage(pageNum - 1)); // PDDocument pages are 0-indexed
                }
                splitDocuments.add(currentSplitDoc);
                partNamesForZip.add(currentPartName);
            }
 
            if (splitDocuments.isEmpty()) {
                // document is closed by try-with-resources
                throw new IllegalArgumentException("No valid pages selected for splitting based on ranges: \"" + ranges + "\"");
            }
 
            byte[] resultBytes;
            // The controller determines the final output type (single PDF or ZIP) based on the 'ranges' string.
            // This service method returns a single PDF's bytes if only one PDDocument was created,
            // or a ZIP's bytes if multiple PDDocuments were created.
            if (splitDocuments.size() == 1) {
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    splitDocuments.get(0).save(baos);
                    resultBytes = baos.toByteArray();
                }
                logger.info("Successfully split PDF {} into a single document for range '{}'", pdfFile.getOriginalFilename(), ranges);
            } else { // Multiple documents, so create a ZIP
                try (ByteArrayOutputStream zipBos = new ByteArrayOutputStream();
                     ZipOutputStream zos = new ZipOutputStream(zipBos)) {
                    for (int i = 0; i < splitDocuments.size(); i++) {
                        PDDocument docToZip = splitDocuments.get(i);
                        String entryName = originalFilenameBase + "_" + partNamesForZip.get(i) + ".pdf";
                        try (ByteArrayOutputStream pageBos = new ByteArrayOutputStream()) {
                            docToZip.save(pageBos);
                            ZipEntry zipEntry = new ZipEntry(entryName);
                            zos.putNextEntry(zipEntry);
                            zos.write(pageBos.toByteArray());
                            zos.closeEntry();
                        }
                    }
                    resultBytes = zipBos.toByteArray();
                }
                logger.info("Successfully split PDF {} into {} documents for ranges '{}', packaged as ZIP.", pdfFile.getOriginalFilename(), splitDocuments.size(), ranges);
            }
 
            return resultBytes;
 
        } catch (IOException e) {
            logger.error("IOException during PDF split by ranges for {}: {}", pdfFile.getOriginalFilename(), e.getMessage());
            throw e; // Re-throw to be handled by controller
        } catch (IllegalArgumentException e) {
            logger.warn("IllegalArgumentException during PDF split by ranges for {}: {}", pdfFile.getOriginalFilename(), e.getMessage());
            throw e; // Re-throw
        } finally {
            // Ensure all created PDDocuments are closed, original document is closed by try-with-resources
            // The splitDocuments list is not available here if an exception occurred before its initialization
            // However, individual PDDocuments created in the loop are added to splitDocuments,
            // and if an exception occurs mid-loop, the closeDocuments helper would have been called.
            // If exception occurs after loop, they are closed in the main try block's implicit finally for splitDocuments.
            // This explicit finally block is more for safety if we modify logic later.
            // For now, the primary closing is handled by the helper on error and at the end of successful processing.
        }
    }
 
    private void closeDocuments(List<PDDocument> documents) {
        for (PDDocument doc : documents) {
            if (doc != null) {
                try {
                    doc.close();
                } catch (IOException e) {
                    logger.error("Error closing a split PDDocument: {}", e.getMessage(), e);
                }
            }
        }
    }
}