package com.pdf.marsk.pdfdemo.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PdfProtectionServiceTest {

    @InjectMocks
    private PdfProtectionService pdfProtectionService;

    private MockMultipartFile createDummyPdf(String name, int pages) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return new MockMultipartFile(name, name + ".pdf", "application/pdf", baos.toByteArray());
        }
    }

    @Test
    void protectPdf_withUserAndPassword_success() throws IOException {
        MockMultipartFile pdf = createDummyPdf("testProtect", 1);
        String userPass = "user123";
        String ownerPass = "owner456";

        byte[] protectedBytes = pdfProtectionService.protectPdf(pdf, userPass, ownerPass);
        assertNotNull(protectedBytes);
        assertTrue(protectedBytes.length > 0);

        try (PDDocument protectedDoc = PDDocument.load(protectedBytes, userPass)) {
            assertTrue(protectedDoc.isEncrypted(), "Document should be encrypted.");
            assertFalse(protectedDoc.getCurrentAccessPermission().isOwnerPermission(), "Loading with user password should not grant owner permission.");
        }
        // Try loading with owner password
        try (PDDocument protectedDocOwner = PDDocument.load(protectedBytes, ownerPass)) {
             assertTrue(protectedDocOwner.isEncrypted());
             assertTrue(protectedDocOwner.getCurrentAccessPermission().isOwnerPermission());
        }
    }
    
    @Test
    void protectPdf_withOnlyOwnerPassword_success() throws IOException {
        MockMultipartFile pdf = createDummyPdf("testOwnerOnly", 1);
        String ownerPass = "ownerOnly789";

        byte[] protectedBytes = pdfProtectionService.protectPdf(pdf, null, ownerPass);
        assertNotNull(protectedBytes);

        // Should be able to open without password
        try (PDDocument protectedDoc = PDDocument.load(protectedBytes)) {
            assertTrue(protectedDoc.isEncrypted()); // It is encrypted, but openable
        }
        // But owner operations require owner password
        try (PDDocument protectedDocOwner = PDDocument.load(protectedBytes, ownerPass)) {
            assertTrue(protectedDocOwner.getCurrentAccessPermission().isOwnerPermission());
        }
    }
    
    @Test
    void protectPdf_withOnlyUserPassword_usesUserAsOwner_success() throws IOException {
        MockMultipartFile pdf = createDummyPdf("testUserOnly", 1);
        String userPass = "userOnlyPass";

        byte[] protectedBytes = pdfProtectionService.protectPdf(pdf, userPass, ""); // Owner pass empty
        assertNotNull(protectedBytes);

        try (PDDocument protectedDoc = PDDocument.load(protectedBytes, userPass)) {
             assertTrue(protectedDoc.isEncrypted());
             // Since userPass was used as ownerPass, this should grant owner-like permissions if userPass is also ownerPass
             // However, PDFBox's isOwnerPermission might be false if only userPass is supplied to load()
             // A better check is to try loading with userPass as ownerPass
        }
         try (PDDocument protectedDocOwner = PDDocument.load(protectedBytes, userPass)) { // userPass is effectiveOwnerPassword
             assertTrue(protectedDocOwner.isEncrypted());
             assertTrue(protectedDocOwner.getCurrentAccessPermission().isOwnerPermission());
        }
    }


    @Test
    void protectPdf_noPasswords_throwsIllegalArgumentException() throws IOException {
        MockMultipartFile pdf = createDummyPdf("testNoPass", 1);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pdfProtectionService.protectPdf(pdf, null, null);
        });
        assertEquals("At least an owner password or a user password must be provided.", exception.getMessage());
    }
    
    @Test
    void protectPdf_emptyPasswords_throwsIllegalArgumentException() throws IOException {
        MockMultipartFile pdf = createDummyPdf("testEmptyPass", 1);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pdfProtectionService.protectPdf(pdf, "", "");
        });
        assertEquals("At least an owner password or a user password must be provided.", exception.getMessage());
    }


    @Test
    void protectPdf_nullFile_throwsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pdfProtectionService.protectPdf(null, "user", "owner");
        });
        assertEquals("A PDF file is required for protection.", exception.getMessage());
    }

    @Test
    void protectPdf_invalidFileType_throwsIllegalArgumentException() {
        MockMultipartFile notAPdf = new MockMultipartFile("file", "file.txt", "text/plain", "text".getBytes());
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pdfProtectionService.protectPdf(notAPdf, "user", "owner");
        });
        assertTrue(exception.getMessage().contains("Invalid file type provided"));
    }
    
    @Test
    void protectPdf_alreadyEncrypted_throwsIOException() throws IOException {
        // Create an already encrypted PDF
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy sppOld =
                new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("oldOwner", "oldUser", new org.apache.pdfbox.pdmodel.encryption.AccessPermission());
            doc.protect(sppOld); // Encrypt with old passwords
            doc.save(baos);
        }
        MockMultipartFile encryptedPdf = new MockMultipartFile("encryptedOld", "encryptedOld.pdf", "application/pdf", baos.toByteArray());

        String newUserPass = "newUser";
        String newOwnerPass = "newOwner";

        // The service will attempt PDDocument.load(inputStream) which will fail for an encrypted PDF without a password.
        IOException exception = assertThrows(IOException.class, () -> {
            pdfProtectionService.protectPdf(encryptedPdf, newUserPass, newOwnerPass);
        });
        // Check that the exception message indicates an issue with loading/decrypting the original PDF
        assertTrue(exception.getCause() instanceof InvalidPasswordException || exception.getMessage().contains("password") || exception.getMessage().contains("decrypt"),
                   "Exception should indicate a password or decryption issue for the source PDF.");
    }
}