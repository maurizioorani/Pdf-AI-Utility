package com.pdf.marsk.pdfdemo.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test; // Added import
import org.junit.jupiter.api.io.TempDir; // Added import
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.pdf.marsk.pdfdemo.model.OcrTextDocument;
import com.pdf.marsk.pdfdemo.repository.OcrTextDocumentRepository;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.TesseractException;

class OcrServiceTest {

    @Mock
    private ITesseract tesseractMock;

    @Mock // Added mock for the repository
    private OcrTextDocumentRepository ocrTextDocumentRepositoryMock;

    @Mock // Added mock for ProgressTrackingService as it's used by OcrService
    private ProgressTrackingService progressTrackingServiceMock;
    
    @InjectMocks
    private OcrService ocrService;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Replace the tesseractInstance with our mock
        try {
            var field = OcrService.class.getDeclaredField("tesseractInstance");
            field.setAccessible(true);
            field.set(ocrService, tesseractMock);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Failed to set up mock tesseract instance");
        }
        // Ensure the mocked repository is used by the service instance
        // This is typically handled by @InjectMocks if the field in OcrService is not final
        // or if OcrService has a constructor that accepts OcrTextDocumentRepository.
        // If OcrService uses @Autowired for ocrTextDocumentRepository, this manual injection might be tricky
        // without a setter or constructor injection. For now, we assume @InjectMocks handles it.
        // If not, we might need to use ReflectionTestUtils.setField or refactor OcrService for testability.

        // Mock default behavior for progressTrackingService to avoid NPEs if it's called
        when(progressTrackingServiceMock.createOcrTask(anyString(), anyInt(), anyString())).thenReturn("mockTaskId");
        when(ocrTextDocumentRepositoryMock.save(any(OcrTextDocument.class))).thenReturn(new OcrTextDocument());


    }
    
    @Test
    void testPerformOcrWithImageFile() throws IOException, TesseractException {
        // Arrange
        String expectedText = "This is sample OCR text";
        when(tesseractMock.doOCR(any(File.class))).thenReturn(expectedText);
        
        byte[] imageContent = "fake image content".getBytes();
        MultipartFile imageFile = new MockMultipartFile(
                "test.png", "test.png", "image/png", imageContent);
        
        // Act
        String result = ocrService.performOcr(imageFile);
        
        // Assert
        assertEquals(expectedText, result);
        verify(tesseractMock, times(1)).doOCR(any(File.class));
    }
    
    @Test
    void testPerformOcrWithImageFileAndLanguage() throws IOException, TesseractException {
        // Arrange
        String expectedText = "This is sample OCR text in Italian";
        when(tesseractMock.doOCR(any(File.class))).thenReturn(expectedText);
        
        byte[] imageContent = "fake image content".getBytes();
        MultipartFile imageFile = new MockMultipartFile(
                "test.png", "test.png", "image/png", imageContent);
        
        // Act
        String result = ocrService.performOcr(imageFile, "ita");
        
        // Assert
        assertEquals(expectedText, result);
        verify(tesseractMock, times(1)).setLanguage("ita");
        verify(tesseractMock, times(1)).doOCR(any(File.class));
    }
    
    @Test
    void testPerformOcrWithPdfFile() throws IOException, TesseractException {
        // Arrange
        String expectedPageText = "This is OCR text from PDF";
        when(tesseractMock.doOCR(any(File.class))).thenReturn(expectedPageText);
        
        // Create a simple PDF file for testing
        Path pdfPath = createSamplePdf();
        byte[] pdfContent = Files.readAllBytes(pdfPath);
        
        MultipartFile pdfFile = new MockMultipartFile(
                "test.pdf", "test.pdf", "application/pdf", pdfContent);
        
        // Act
        String result = ocrService.performOcr(pdfFile);
        
        // Assert
        assertTrue(result.contains(expectedPageText));
        // Should be called at least once (depends on the number of pages)
        verify(tesseractMock, atLeastOnce()).doOCR(any(File.class));
    }
    
    @Test
    void testPerformOcrWithPdfFileAndLanguage() throws IOException, TesseractException {
        // Arrange
        String expectedPageText = "This is OCR text from PDF in Italian";
        when(tesseractMock.doOCR(any(File.class))).thenReturn(expectedPageText);
        
        // Create a simple PDF file for testing
        Path pdfPath = createSamplePdf();
        byte[] pdfContent = Files.readAllBytes(pdfPath);
        
        MultipartFile pdfFile = new MockMultipartFile(
                "test.pdf", "test.pdf", "application/pdf", pdfContent);
        
        // Act
        String result = ocrService.performOcr(pdfFile, "ita");
        
        // Assert
        assertTrue(result.contains(expectedPageText));
        verify(tesseractMock, times(1)).setLanguage("ita");
        verify(tesseractMock, atLeastOnce()).doOCR(any(File.class));
    }
    
    @Test
    void testPerformOcrWithNullFilename() throws IOException, TesseractException {
        // Arrange
        String expectedText = "This is sample OCR text";
        when(tesseractMock.doOCR(any(File.class))).thenReturn(expectedText);
        
        byte[] imageContent = "fake image content".getBytes();
        MultipartFile imageFile = new MockMultipartFile(
                "image", null, "image/png", imageContent);
        
        // Act
        String result = ocrService.performOcr(imageFile);
        
        // Assert
        assertEquals(expectedText, result);
        verify(tesseractMock, times(1)).doOCR(any(File.class));
    }
    
    @Test
    void testPerformOcrWithTesseractException() throws TesseractException, IOException {
        // Arrange
        when(tesseractMock.doOCR(any(File.class))).thenThrow(new TesseractException("Tesseract error"));
        
        byte[] imageContent = "fake image content".getBytes();
        MultipartFile imageFile = new MockMultipartFile(
                "test.png", "test.png", "image/png", imageContent);
        
        // Act & Assert
        assertThrows(TesseractException.class, () -> ocrService.performOcr(imageFile));
    }
    
    private Path createSamplePdf() throws IOException {
        Path pdfFile = tempDir.resolve("sample.pdf");
        
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText("Sample PDF text");
                contentStream.endText();
            }
            
            document.save(pdfFile.toFile());
        }
        
        return pdfFile;
    }
}
