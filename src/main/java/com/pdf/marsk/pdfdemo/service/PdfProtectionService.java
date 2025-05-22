package com.pdf.marsk.pdfdemo.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
public class PdfProtectionService {

    private static final Logger logger = LoggerFactory.getLogger(PdfProtectionService.class);

    public byte[] protectPdf(MultipartFile pdfFile, String userPassword, String ownerPassword) throws IOException {
        if (pdfFile == null || pdfFile.isEmpty()) {
            throw new IllegalArgumentException("A PDF file is required for protection.");
        }
        if (!"application/pdf".equalsIgnoreCase(pdfFile.getContentType())) {
            logger.warn("Invalid file type for protection: {} (type: {})", pdfFile.getOriginalFilename(), pdfFile.getContentType());
            throw new IllegalArgumentException("Invalid file type provided: " + pdfFile.getOriginalFilename() + ". Only PDF files are allowed.");
        }

        boolean hasUserPass = StringUtils.hasText(userPassword);
        boolean hasOwnerPass = StringUtils.hasText(ownerPassword);

        if (!hasUserPass && !hasOwnerPass) {
            throw new IllegalArgumentException("At least an owner password or a user password must be provided.");
        }
        
        // PDFBox requires an owner password if any protection is applied.
        // If only user password is provided by user, we can use it as owner pass, or generate a random one.
        // For simplicity, if owner pass is blank but user pass is not, we'll use user pass as owner pass.
        // Or, more robustly, require owner password if user wants any protection.
        // The current HTML implies owner is optional if user pass is set, but PDFBox needs it.
        // Let's enforce that if userPassword is set, ownerPassword must also be set (can be same or different).
        // If only ownerPassword is set, that's fine for restricting permissions without an open password.

        String effectiveOwnerPassword = ownerPassword;
        if (!hasOwnerPass && hasUserPass) {
             // This case should ideally be validated in controller or HTML to ensure owner pass is provided if user pass is.
             // For now, let's make owner password same as user password if owner is blank but user is not.
             // This is a common behavior in some tools.
            effectiveOwnerPassword = userPassword;
            logger.warn("Owner password was not provided but user password was. Using user password as owner password for PDF: {}", pdfFile.getOriginalFilename());
        }
         if (!StringUtils.hasText(effectiveOwnerPassword)) { // Final check
            throw new IllegalArgumentException("An effective owner password is required to apply any protection.");
        }


        try (InputStream inputStream = pdfFile.getInputStream();
             PDDocument document = PDDocument.load(inputStream);
             ByteArrayOutputStream protectedPdfOutputStream = new ByteArrayOutputStream()) {

            if (document.isEncrypted()) {
                logger.warn("PDF {} is already encrypted. Re-protection might not work as expected or might require original owner password.", pdfFile.getOriginalFilename());
                // Optionally, could throw an error or try to remove existing encryption if owner pass is known (complex).
                // For now, we'll proceed, but it might fail or behave unexpectedly.
            }

            AccessPermission ap = new AccessPermission();
            // By default, all permissions are granted. Let's restrict some for testing.
            ap.setCanPrint(false);
            ap.setCanModify(false);
            // Other permissions like CanAssembleDocument, CanExtractContent, etc., remain true by default.
            // Owner password will bypass these. User password will be subject to them.

            StandardProtectionPolicy spp = new StandardProtectionPolicy(effectiveOwnerPassword, userPassword, ap);
            spp.setEncryptionKeyLength(128); // Or 256 for stronger encryption

            document.protect(spp);
            document.save(protectedPdfOutputStream);

            logger.info("Successfully protected PDF: {}", pdfFile.getOriginalFilename());
            return protectedPdfOutputStream.toByteArray();

        } catch (IOException e) {
            logger.error("Error protecting PDF file {}: {}", pdfFile.getOriginalFilename(), e.getMessage(), e);
            throw new IOException("Error protecting PDF file: " + pdfFile.getOriginalFilename(), e);
        }
    }
}