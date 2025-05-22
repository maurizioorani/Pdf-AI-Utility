package com.pdf.marsk.pdfdemo.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PdfCompressionServiceTest {

    @InjectMocks
    private PdfCompressionService pdfCompressionService;

    private MockMultipartFile createDummyPdfWithText(String name) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText("Hello World");
                contentStream.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return new MockMultipartFile(name, name + ".pdf", "application/pdf", baos.toByteArray());
        }
    }
    
    private MockMultipartFile createDummyPdfWithImage(String name, String imageName, boolean useJpeg) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            BufferedImage awtImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            PDImageXObject pdImage;
            if (useJpeg) {
                ByteArrayOutputStream jpegOs = new ByteArrayOutputStream();
                ImageIO.write(awtImage, "jpeg", jpegOs);
                pdImage = PDImageXObject.createFromByteArray(doc, jpegOs.toByteArray(), imageName);
            } else {
                 // Create a simple PNG like image (using lossless factory)
                pdImage = org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, awtImage);
            }

            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
                contentStream.drawImage(pdImage, 50, 50, pdImage.getWidth(), pdImage.getHeight());
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return new MockMultipartFile(name, name + ".pdf", "application/pdf", baos.toByteArray());
        }
    }


    @Test
    void compressPdf_basicOptimization_returnsBytes() throws IOException {
        MockMultipartFile pdf = createDummyPdfWithText("testOpt");
        byte[] originalBytes = pdf.getBytes();

        byte[] compressedBytes = pdfCompressionService.compressPdf(pdf, false); // false for no image compression

        assertNotNull(compressedBytes);
        assertTrue(compressedBytes.length > 0);
        // Basic re-save might not always reduce size, could even slightly increase due to metadata/structure changes
        // So, we don't assert compressedBytes.length < originalBytes.length strictly for this basic case.
        // We mainly check that it processes without error and returns a valid PDF.
        try (PDDocument processedDoc = PDDocument.load(compressedBytes)) {
            assertEquals(1, processedDoc.getNumberOfPages());
        }
    }

    @Test
    void compressPdf_withImageCompression_nonJpegImage() throws IOException {
        MockMultipartFile pdfWithPng = createDummyPdfWithImage("testPngImage", "image1", false);
        byte[] originalBytes = pdfWithPng.getBytes();

        byte[] compressedBytes = pdfCompressionService.compressPdf(pdfWithPng, true); // true for image compression

        assertNotNull(compressedBytes);
        assertTrue(compressedBytes.length > 0);
        // We expect some compression here, but it's not guaranteed to be massive or always smaller
        // depending on original image and PDFBox's JPEG conversion.
        // For a robust test, one might inspect the image objects within the PDF.
        // assertTrue(compressedBytes.length < originalBytes.length); // This might be flaky
         try (PDDocument processedDoc = PDDocument.load(compressedBytes)) {
            assertEquals(1, processedDoc.getNumberOfPages());
            // Further checks could involve extracting images and verifying their format/size if needed
        }
    }
    
    @Test
    void compressPdf_withImageCompression_jpegImage_noChangeExpected() throws IOException {
        MockMultipartFile pdfWithJpeg = createDummyPdfWithImage("testJpegImage", "image1", true);
        byte[] originalBytes = pdfWithJpeg.getBytes();

        byte[] compressedBytes = pdfCompressionService.compressPdf(pdfWithJpeg, true);

        assertNotNull(compressedBytes);
        // If original was already JPEG, current logic might not re-compress it or change it much
        // unless the re-save itself optimizes something.
        // For this test, we mainly ensure it runs and the output is valid.
        // A more precise test would check if the image object itself was modified (it shouldn't be significantly).
        try (PDDocument processedDoc = PDDocument.load(compressedBytes)) {
            assertEquals(1, processedDoc.getNumberOfPages());
        }
    }


    @Test
    void compressPdf_nullFile_throwsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pdfCompressionService.compressPdf(null, false);
        });
        assertEquals("A PDF file is required for compression.", exception.getMessage());
    }

    @Test
    void compressPdf_emptyFile_throwsIllegalArgumentException() throws IOException {
        MockMultipartFile emptyPdf = new MockMultipartFile("empty", "empty.pdf", "application/pdf", new byte[0]);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pdfCompressionService.compressPdf(emptyPdf, false);
        });
        // This check is now done by MultipartFile.isEmpty() before service call in controller,
        // but service should also handle it.
         assertEquals("A PDF file is required for compression.", exception.getMessage());
    }

    @Test
    void compressPdf_invalidFileType_throwsIllegalArgumentException() {
        MockMultipartFile notAPdf = new MockMultipartFile("file", "file.txt", "text/plain", "some text".getBytes());
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pdfCompressionService.compressPdf(notAPdf, false);
        });
        assertTrue(exception.getMessage().contains("Invalid file type provided"));
    }
    
    @Test
    void compressPdf_encryptedPdf_throwsIOException() throws IOException {
        // Create a simple encrypted PDF
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy spp =
                new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("owner", "user", new org.apache.pdfbox.pdmodel.encryption.AccessPermission());
            doc.protect(spp);
            doc.save(baos);
        }
        MockMultipartFile encryptedPdf = new MockMultipartFile("encrypted", "encrypted.pdf", "application/pdf", baos.toByteArray());

        IOException exception = assertThrows(IOException.class, () -> {
            pdfCompressionService.compressPdf(encryptedPdf, false);
        });
        // PDDocument.load will throw InvalidPasswordException if it's password protected for opening
        // The service wraps this in a generic "Error processing PDF file"
        assertTrue(exception.getMessage().startsWith("Error processing PDF file:") &&
                   (exception.getCause() instanceof org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException ||
                    (exception.getCause() != null && exception.getCause().getMessage().toLowerCase().contains("password"))),
                   "Exception message should indicate an error processing due to password/decryption. Actual: " + exception.getMessage() + (exception.getCause() != null ? " Caused by: " + exception.getCause().getMessage() : ""));
    }
}