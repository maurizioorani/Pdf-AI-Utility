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

    public PdfSplitService() {
        // Constructor is empty
    }

    public byte[] splitPdfEveryPage(MultipartFile pdfFile, String originalFilenameBase) throws IOException {
        if (pdfFile == null || pdfFile.isEmpty()) {
            throw new IllegalArgumentException("A PDF file is required for splitting.");
        }
        if (!"application/pdf".equalsIgnoreCase(pdfFile.getContentType())) {
            logger.warn("Invalid file type for splitting: {} (type: {})", pdfFile.getOriginalFilename(), pdfFile.getContentType());
            throw new IllegalArgumentException("Invalid file type provided: " + pdfFile.getOriginalFilename() + ". Only PDF files are allowed.");
        }

        List<PDDocument> splitDocuments = new ArrayList<>(); // Initialize here for access in finally

        try (InputStream inputStream = pdfFile.getInputStream();
             PDDocument document = PDDocument.load(inputStream)) {

            if (document.isEncrypted()) {
                logger.warn("Cannot split an encrypted PDF: {}", pdfFile.getOriginalFilename());
                throw new IOException("Encrypted PDFs cannot be split with this method.");
            }

            Splitter splitter = new Splitter();
            // Populate the list directly
            splitDocuments.addAll(splitter.split(document));

            if (splitDocuments.isEmpty()) {
                throw new IOException("Splitting the PDF resulted in no documents.");
            }

            ByteArrayOutputStream zipBos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(zipBos)) {
                for (int i = 0; i < splitDocuments.size(); i++) {
                    PDDocument singlePageDoc = splitDocuments.get(i);
                    try (ByteArrayOutputStream pageBos = new ByteArrayOutputStream()) {
                        singlePageDoc.save(pageBos);
                        ZipEntry zipEntry = new ZipEntry(originalFilenameBase + "_page_" + (i + 1) + ".pdf");
                        zos.putNextEntry(zipEntry);
                        zos.write(pageBos.toByteArray());
                        zos.closeEntry();
                    } finally {
                        // Document is closed here after being written to the zip entry
                        if (singlePageDoc != null) {
                           try { singlePageDoc.close(); } catch (IOException e) { logger.error("Error closing singlePageDoc in ZIP loop: {}", e.getMessage(), e); }
                        }
                    }
                }
            }
            logger.info("Successfully split PDF {} into {} pages, packaged as ZIP.", pdfFile.getOriginalFilename(), splitDocuments.size());
            return zipBos.toByteArray();

        } catch (IOException e) {
            logger.error("Error during PDF split (every page) for {}: {}", pdfFile.getOriginalFilename(), e.getMessage(), e);
            // The finally block below will handle closing documents
            throw e;
        } finally {
            // Ensure all documents in the list are closed, especially if an error occurred before the loop finished.
            closeDocuments(splitDocuments);
        }
    }

    public int getPdfPageCount(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream cannot be null.");
        }
        try (PDDocument document = PDDocument.load(inputStream)) {
            if (document.isEncrypted()) {
                throw new IOException("Encrypted PDFs cannot be processed for page count without a password.");
            }
            return document.getNumberOfPages();
        } catch (IOException e) {
            logger.error("Error reading PDF for page count: {}", e.getMessage());
            throw e;
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

        List<PDDocument> createdDocuments = new ArrayList<>(); // To keep track of all PDDocuments created

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
            List<String> partNamesForOutput = new ArrayList<>();

            for (String part : rangeParts) {
                if (part.isEmpty()) continue;

                PDDocument currentSplitDoc = new PDDocument();
                // Add to createdDocuments immediately.
                createdDocuments.add(currentSplitDoc);
                List<Integer> pagesToIncludeInThisDoc = new ArrayList<>();
                String currentPartName;

                if (part.contains("-")) {
                    String[] ends = part.split("-", 2);
                    if (ends.length != 2 || ends[0].isEmpty() || ends[1].isEmpty()) {
                        throw new IllegalArgumentException("Invalid range format: \"" + part + "\" in \"" + ranges + "\"");
                    }
                    try {
                        int startPage = Integer.parseInt(ends[0]);
                        int endPage = Integer.parseInt(ends[1]);
                        if (startPage <= 0 || endPage > totalPages || startPage > endPage) {
                            throw new IllegalArgumentException("Invalid page numbers in range: \"" + part + "\". PDF has " + totalPages + " pages.");
                        }
                        for (int i = startPage; i <= endPage; i++) pagesToIncludeInThisDoc.add(i);
                        currentPartName = "pages_" + startPage + "-" + endPage;
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid page numbers in range: \"" + part + "\"", e);
                    }
                } else {
                    try {
                        int pageNum = Integer.parseInt(part);
                        if (pageNum <= 0 || pageNum > totalPages) {
                            throw new IllegalArgumentException("Invalid page number: " + pageNum + ". PDF has " + totalPages + " pages.");
                        }
                        pagesToIncludeInThisDoc.add(pageNum);
                        currentPartName = "page_" + pageNum;
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid page number: \"" + part + "\"", e);
                    }
                }

                if (pagesToIncludeInThisDoc.isEmpty()) {
                    logger.warn("No pages were selected for range part: \"{}\", skipping this part for output.", part);
                    createdDocuments.remove(currentSplitDoc);
                    try { currentSplitDoc.close(); } catch (IOException e) { logger.error("Error closing unused currentSplitDoc for part {}", part, e); }
                    continue;
                }

                for (int pageNum : pagesToIncludeInThisDoc) {
                    currentSplitDoc.addPage(document.getPage(pageNum - 1));
                }
                if (currentSplitDoc.getNumberOfPages() > 0) {
                    partNamesForOutput.add(currentPartName);
                } else { // Should be rare if pagesToIncludeInThisDoc was populated
                    createdDocuments.remove(currentSplitDoc);
                    try { currentSplitDoc.close(); } catch (IOException e) { logger.error("Error closing empty currentSplitDoc for part {}", part, e); }
                }
            }
            
            // Filter out any documents that might have been added to createdDocuments but ended up empty
            List<PDDocument> finalDocsToProcess = new ArrayList<>();
            List<String> finalPartNames = new ArrayList<>();
            for (int i = 0; i < createdDocuments.size(); i++) {
                PDDocument doc = createdDocuments.get(i);
                if (doc.getNumberOfPages() > 0) {
                    finalDocsToProcess.add(doc);
                    // Ensure partNamesForOutput has a corresponding entry if the doc is kept
                    if (i < partNamesForOutput.size()) { // Basic check, assumes alignment
                        finalPartNames.add(partNamesForOutput.get(i));
                    } else {
                         // This case should ideally not be hit if logic is correct,
                         // but add a fallback name if partNamesForOutput is misaligned.
                        finalPartNames.add("part_" + (finalDocsToProcess.size()));
                        logger.warn("Mismatch between createdDocuments and partNamesForOutput, using fallback name for document index {}", i);
                    }
                } else {
                    // If a doc is in createdDocuments but has no pages, ensure it's closed
                    try { if(doc != null) doc.close(); } catch (IOException ioe) { logger.warn("Error closing empty doc during final filtering: {}", ioe.getMessage());}
                }
            }
            // Update createdDocuments to only contain those that will be part of the output
            createdDocuments = finalDocsToProcess;
            partNamesForOutput = finalPartNames;


            if (createdDocuments.isEmpty()) {
                throw new IllegalArgumentException("No valid pages selected for splitting based on ranges: \"" + ranges + "\"");
            }

            if (createdDocuments.size() == 1) { // Single PDF output
                PDDocument singleDoc = createdDocuments.get(0);
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    singleDoc.save(baos);
                    logger.info("Successfully split PDF {} into a single document for range '{}'", pdfFile.getOriginalFilename(), ranges);
                    return baos.toByteArray();
                } finally {
                    if (singleDoc != null) {
                         try { singleDoc.close(); } catch (IOException ioe) { logger.warn("Error closing singleDoc: {}", ioe.getMessage());}
                    }
                }
            } else { // Multiple documents, create a ZIP
                ByteArrayOutputStream zipBos = new ByteArrayOutputStream();
                try (ZipOutputStream zos = new ZipOutputStream(zipBos)) {
                    for (int i = 0; i < createdDocuments.size(); i++) {
                        PDDocument docToZip = createdDocuments.get(i);
                        String entryName = originalFilenameBase + "_" + partNamesForOutput.get(i) + ".pdf";
                        try (ByteArrayOutputStream pageBos = new ByteArrayOutputStream()) {
                            docToZip.save(pageBos);
                            ZipEntry zipEntry = new ZipEntry(entryName);
                            zos.putNextEntry(zipEntry);
                            zos.write(pageBos.toByteArray());
                            zos.closeEntry();
                        } finally {
                           if (docToZip != null) {
                                try { docToZip.close(); } catch (IOException ioe) { logger.warn("Error closing docToZip in ZIP loop: {}", ioe.getMessage());}
                           }
                        }
                    }
                }
                logger.info("Successfully split PDF {} into {} documents for ranges '{}', packaged as ZIP.",
                            pdfFile.getOriginalFilename(), createdDocuments.size(), ranges);
                return zipBos.toByteArray();
            }
        } catch (IOException | IllegalArgumentException e) {
            logger.error("Error during PDF split by ranges for {}: {}", pdfFile.getOriginalFilename(), e.getMessage(), e);
            throw e;
        } finally {
            closeDocuments(createdDocuments);
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