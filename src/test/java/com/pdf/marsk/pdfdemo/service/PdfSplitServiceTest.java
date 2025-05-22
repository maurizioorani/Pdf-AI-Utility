package com.pdf.marsk.pdfdemo.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PdfSplitServiceTest {

    @InjectMocks
    private PdfSplitService pdfSplitService;

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
    void splitPdfEveryPage_success() throws IOException {
        MockMultipartFile pdf = createDummyPdf("testSplit", 3);
        String baseName = "testSplit_output";

        byte[] zipBytes = pdfSplitService.splitPdfEveryPage(pdf, baseName);
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);

        // Verify ZIP content
        int entryCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                assertTrue(entry.getName().startsWith(baseName + "_page_"));
                assertTrue(entry.getName().endsWith(".pdf"));
                
                // Optionally, load each PDF entry to verify it's a valid single-page PDF
                ByteArrayOutputStream entryBaos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    entryBaos.write(buffer, 0, len);
                }
                try (PDDocument singlePageDoc = PDDocument.load(entryBaos.toByteArray())) {
                    assertEquals(1, singlePageDoc.getNumberOfPages());
                }
                zis.closeEntry();
            }
        }
        assertEquals(3, entryCount, "ZIP archive should contain 3 PDF files.");
    }

    @Test
    void splitPdfEveryPage_singlePagePdf_success() throws IOException {
        MockMultipartFile pdf = createDummyPdf("singlePage", 1);
        String baseName = "singlePage_output";

        byte[] zipBytes = pdfSplitService.splitPdfEveryPage(pdf, baseName);
        assertNotNull(zipBytes);
        
        int entryCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                assertEquals(baseName + "_page_1.pdf", entry.getName());
                 zis.closeEntry();
            }
        }
        assertEquals(1, entryCount);
    }


    @Test
    void splitPdfEveryPage_nullFile_throwsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pdfSplitService.splitPdfEveryPage(null, "basename");
        });
        assertEquals("A PDF file is required for splitting.", exception.getMessage());
    }

    @Test
    void splitPdfEveryPage_emptyFile_throwsIllegalArgumentException() throws IOException {
        MockMultipartFile emptyPdf = new MockMultipartFile("empty", "empty.pdf", "application/pdf", new byte[0]);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pdfSplitService.splitPdfEveryPage(emptyPdf, "basename");
        });
         assertEquals("A PDF file is required for splitting.", exception.getMessage());
    }

    @Test
    void splitPdfEveryPage_invalidFileType_throwsIllegalArgumentException() {
        MockMultipartFile notAPdf = new MockMultipartFile("file", "file.txt", "text/plain", "some text".getBytes());
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pdfSplitService.splitPdfEveryPage(notAPdf, "basename");
        });
        assertTrue(exception.getMessage().contains("Invalid file type provided"));
    }
    
    @Test
    void splitPdfEveryPage_encryptedPdf_throwsIOException() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy spp =
                new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("owner", "user", new org.apache.pdfbox.pdmodel.encryption.AccessPermission());
            doc.protect(spp);
            doc.save(baos);
        }
        MockMultipartFile encryptedPdf = new MockMultipartFile("encrypted", "encrypted.pdf", "application/pdf", baos.toByteArray());

        IOException exception = assertThrows(IOException.class, () -> {
            pdfSplitService.splitPdfEveryPage(encryptedPdf, "basename");
        });
        // PDDocument.load will throw InvalidPasswordException if it's password protected for opening
        assertTrue(exception.getCause() instanceof org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException ||
                   exception.getMessage().toLowerCase().contains("password") ||
                   exception.getMessage().toLowerCase().contains("decrypt"),
                   "Exception message should indicate a password or decryption issue. Actual: " + exception.getMessage());
    }
    
    @Test
    void splitPdfEveryPage_corruptPdf_throwsIOExceptionDuringLoad() throws IOException {
        MockMultipartFile corruptPdf = new MockMultipartFile("corrupt", "corrupt.pdf", "application/pdf", "this is not a pdf".getBytes());
        
        // Exception will be thrown by PDDocument.load()
        assertThrows(IOException.class, () -> {
            pdfSplitService.splitPdfEveryPage(corruptPdf, "basename");
        });
    }
}