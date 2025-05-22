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

    // Placeholder for more advanced splitting options (e.g., by range)
    // public List<byte[]> splitPdfByRanges(MultipartFile pdfFile, String ranges) throws IOException {
    //     // ... implementation would go here ...
    //     return new ArrayList<>();
    // }
}