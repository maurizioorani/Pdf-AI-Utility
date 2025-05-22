package com.pdf.marsk.pdfdemo.controller;

import java.io.IOException;
import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is; // Added for jsonPath assertions
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // Added for merged tests
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content; // Added for merged tests
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath; // Added for merged tests
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.pdf.marsk.pdfdemo.model.OcrTextDocument; // Ensure this is present
import com.pdf.marsk.pdfdemo.repository.OcrTextDocumentRepository;
import com.pdf.marsk.pdfdemo.service.OcrService;
import com.pdf.marsk.pdfdemo.service.ProgressTrackingService;
import java.util.Optional; // Ensure this is present
import com.pdf.marsk.pdfdemo.service.OllamaService; // Added import

import net.sourceforge.tess4j.TesseractException; // Added import

@WebMvcTest(OcrController.class) // Changed from @SpringBootTest
@AutoConfigureMockMvc // This might be redundant with @WebMvcTest but often kept
public class OcrControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OcrService ocrService;

    @MockBean
    private OcrTextDocumentRepository ocrTextDocumentRepository;

    @MockBean // Added mock for ProgressTrackingService
    private ProgressTrackingService progressTrackingService;
    
    @MockBean // Added mock for OllamaService
    private OllamaService ollamaService;

    @Test
    public void testOcrPageLoads() throws Exception {
        // Mock the repository call that happens in the ocrPage GET mapping
        when(ocrTextDocumentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Collections.emptyList());
        // Mock getProgress for the case where completedTaskId is null (or not provided)
        when(progressTrackingService.getProgress(null)).thenReturn(null);

        mockMvc.perform(get("/ocr"))
                .andExpect(status().isOk())
                .andExpect(view().name("ocr"))
                .andExpect(model().attribute("savedOcrDocuments", Collections.emptyList()))
                .andExpect(model().attributeExists("ocrInfo"));
    }@Test
    public void testHandleOcrUpload_Success() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "imageFile",
                "test.png",
                MediaType.IMAGE_PNG_VALUE,
                "fake-image-bytes".getBytes()
        );
        String expectedOcrText = "This is the extracted text.";

        when(ocrService.performOcr(any(MockMultipartFile.class), eq("eng"))).thenReturn(expectedOcrText);

        mockMvc.perform(multipart("/ocr/process").file(imageFile))
                .andExpect(status().isFound()) // 302 Redirect
                .andExpect(redirectedUrl("/ocr"))
                .andExpect(flash().attribute("ocrResult", expectedOcrText))
                .andExpect(flash().attribute("originalFilename", "test.png"))
                .andExpect(flash().attribute("language", "English"));
    }
    
    @Test
    public void testHandleOcrUpload_WithLanguage() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "imageFile",
                "test.png",
                MediaType.IMAGE_PNG_VALUE,
                "fake-image-bytes".getBytes()
        );
        String expectedOcrText = "This is the extracted text in Italian.";

        when(ocrService.performOcr(any(MockMultipartFile.class), any(String.class))).thenReturn(expectedOcrText);

        mockMvc.perform(multipart("/ocr/process")
                .file(imageFile)
                .param("language", "ita"))
                .andExpect(status().isFound()) // 302 Redirect
                .andExpect(redirectedUrl("/ocr"))
                .andExpect(flash().attribute("ocrResult", expectedOcrText))
                .andExpect(flash().attribute("originalFilename", "test.png"))
                .andExpect(flash().attribute("language", "Italian"));
    }
    
    @Test
    public void testHandleOcrUpload_EmptyFile() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "imageFile",
                "empty.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[0]
        );

        mockMvc.perform(multipart("/ocr/process").file(emptyFile))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/ocr"))
                .andExpect(flash().attribute("ocrError", "Please select an image file to upload."));
    }    @Test
    public void testHandleOcrUpload_UnsupportedFileType() throws Exception {
        MockMultipartFile textFile = new MockMultipartFile(
                "imageFile",
                "test.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "this is not an image".getBytes()
        );

        mockMvc.perform(multipart("/ocr/process").file(textFile))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/ocr"))
                .andExpect(flash().attribute("ocrError", "Unsupported file type. Please upload a PNG, JPG, JPEG, TIFF, or PDF file."));
    }    @Test
    public void testHandleOcrUpload_OcrServiceIOException() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "imageFile",
                "test_io_error.png",
                MediaType.IMAGE_PNG_VALUE,
                "fake-image-bytes".getBytes()
        );

        when(ocrService.performOcr(any(MockMultipartFile.class), eq("eng"))).thenThrow(new IOException("Simulated Disk Read Error"));

        mockMvc.perform(multipart("/ocr/process").file(imageFile))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/ocr"))
                .andExpect(flash().attribute("ocrError", "File processing error: Simulated Disk Read Error"));
    }    @Test
    public void testHandleOcrUpload_OcrServiceTesseractException() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "imageFile",
                "test_tesseract_error.png",
                MediaType.IMAGE_PNG_VALUE,
                "fake-image-bytes".getBytes()
        );

        when(ocrService.performOcr(any(MockMultipartFile.class), eq("eng"))).thenThrow(new TesseractException("Simulated Tesseract Engine Error"));

        mockMvc.perform(multipart("/ocr/process").file(imageFile))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/ocr"))
                .andExpect(flash().attribute("ocrError", containsString("OCR processing failed. Ensure Tesseract is installed correctly and the image is clear. Error: Simulated Tesseract Engine Error")));
    }
      @Test
    public void testHandleOcrUpload_TesseractLibraryNotFoundException() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "imageFile",
                "test_lib_not_found.png",
                MediaType.IMAGE_PNG_VALUE,
                "fake-image-bytes".getBytes()
        );

        when(ocrService.performOcr(any(MockMultipartFile.class), eq("eng"))).thenThrow(new TesseractException("Unable to load library 'tesseract'. This is a critical error."));

        mockMvc.perform(multipart("/ocr/process").file(imageFile))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/ocr"))
                .andExpect(flash().attribute("ocrError", "OCR processing failed: Tesseract library not found. Please ensure Tesseract is installed and configured correctly on the server."));
    }    @Test
    public void testHandleOcrUpload_TessdataNotFoundException() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "imageFile",
                "test_tessdata_not_found.png",
                MediaType.IMAGE_PNG_VALUE,
                "fake-image-bytes".getBytes()
        );

        when(ocrService.performOcr(any(MockMultipartFile.class), eq("eng"))).thenThrow(new TesseractException("Data path does not exist! This is a critical error."));        
        mockMvc.perform(multipart("/ocr/process").file(imageFile))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/ocr"))
                .andExpect(flash().attribute("ocrError", "OCR processing failed: Tesseract 'tessdata' (language files) not found. Please check if 'eng.traineddata' is present in the tessdata directory."));
    }    @Test
    public void testHandleOcrUpload_PdfFileSuccess() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "imageFile",
                "test.pdf",
                "application/pdf",
                "fake-pdf-bytes".getBytes()
        );
        String mockTaskId = "pdf-task-123";

        // Mock the call to progressTrackingService.createOcrTask, which is used by startAsyncOcrProcess
        when(progressTrackingService.createOcrTask(eq("test.pdf"), eq(0), eq("eng")))
                .thenReturn(mockTaskId);
        
        // We don't need to mock ocrService.performOcr itself for this controller test,
        // as the controller's PDF branch primarily sets up the async task.
        // The actual performOcr is part of the async flow handled by CompletableFuture.

        mockMvc.perform(multipart("/ocr/process").file(pdfFile).param("language", "eng"))
                .andExpect(status().isFound()) // 302 Redirect
                .andExpect(redirectedUrl("/ocr"))
                .andExpect(flash().attribute("ocrTaskId", mockTaskId)) // Check that the correct task ID is set
                .andExpect(flash().attribute("originalFilename", "test.pdf"))
                .andExpect(flash().attribute("language", "English"));
                // We do not expect "ocrResult" here because PDF processing is asynchronous.
    }

    // --- Tests for merged OcrDocumentController functionality ---

    @Test
    public void testSaveOcrDocument_Enhanced_Success() throws Exception {
        when(ocrTextDocumentRepository.save(any(OcrTextDocument.class))).thenAnswer(invocation -> {
            OcrTextDocument doc = invocation.getArgument(0);
            doc.setId(1L); // Simulate save
            return doc;
        });

        mockMvc.perform(post("/ocr/documents/save")
                        .param("originalFilename", "doc.png")
                        .param("extractedText", "Original text")
                        .param("enhancedText", "Enhanced text")
                        .param("language", "English")
                        .param("enhancementModel", "llama3")
                        .param("documentType", "generic")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.documentId", is(1)))
                .andExpect(jsonPath("$.message", is("Document saved successfully")));
    }

    @Test
    public void testSaveOcrDocument_Original_Success() throws Exception {
        when(ocrTextDocumentRepository.save(any(OcrTextDocument.class))).thenAnswer(invocation -> {
            OcrTextDocument doc = invocation.getArgument(0);
            doc.setId(2L); // Simulate save
            return doc;
        });

        mockMvc.perform(post("/ocr/documents/save-original")
                        .param("originalFilename", "doc_orig.png")
                        .param("extractedText", "Original text only")
                        .param("language", "Italian")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.documentId", is(2)))
                .andExpect(jsonPath("$.message", is("Original document saved successfully")));
    }

    @Test
    public void testViewDocument_Found_Enhanced() throws Exception {
        OcrTextDocument doc = new OcrTextDocument("view.png", "View original", "View enhanced", "eng", "llama3", "technical");
        doc.setId(3L);
        doc.setIsEnhanced(true); // Ensure this is set for the test case
        when(ocrTextDocumentRepository.findById(3L)).thenReturn(Optional.of(doc));
        when(ollamaService.getAvailableModels()).thenReturn(Collections.singletonList("llama3"));


        mockMvc.perform(get("/ocr/documents/3"))
                .andExpect(status().isOk())
                .andExpect(view().name("ocr"))
                .andExpect(model().attribute("ocrResult", "View enhanced"))
                .andExpect(model().attribute("originalFilename", "view.png"))
                .andExpect(model().attribute("language", "English"))
                .andExpect(model().attribute("currentDocumentId", 3L))
                .andExpect(model().attribute("isEnhanced", true))
                .andExpect(model().attribute("enhancementModel", "llama3"))
                .andExpect(model().attribute("documentType", "technical"))
                .andExpect(model().attribute("originalOcrText", "View original"))
                .andExpect(model().attribute("showComparison", true));
    }

    @Test
    public void testViewDocument_Found_OriginalOnly() throws Exception {
        OcrTextDocument doc = new OcrTextDocument("view_orig.png", "View original only", "ita");
        doc.setId(4L);
        doc.setIsEnhanced(false);
        when(ocrTextDocumentRepository.findById(4L)).thenReturn(Optional.of(doc));
        when(ollamaService.getAvailableModels()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/ocr/documents/4"))
                .andExpect(status().isOk())
                .andExpect(view().name("ocr"))
                .andExpect(model().attribute("ocrResult", "View original only"))
                .andExpect(model().attribute("originalFilename", "view_orig.png"))
                .andExpect(model().attribute("language", "Italian"))
                .andExpect(model().attribute("currentDocumentId", 4L))
                .andExpect(model().attribute("isEnhanced", false))
                .andExpect(model().attribute("showComparison", false));
    }
    
    @Test
    public void testViewDocument_Found_ShowOriginalParam() throws Exception {
        OcrTextDocument doc = new OcrTextDocument("view_param.png", "Original for param test", "Enhanced for param test", "eng", "gemma", "business");
        doc.setId(5L);
        doc.setIsEnhanced(true);
        when(ocrTextDocumentRepository.findById(5L)).thenReturn(Optional.of(doc));
        when(ollamaService.getAvailableModels()).thenReturn(Collections.singletonList("gemma"));

        mockMvc.perform(get("/ocr/documents/5").param("showOriginal", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("ocr"))
                .andExpect(model().attribute("ocrResult", "Original for param test")) // Should show original
                .andExpect(model().attribute("isEnhanced", false)) // isEnhanced should be false
                .andExpect(model().attribute("showComparison", false)); // No comparison when showing original
    }


    @Test
    public void testViewDocument_NotFound() throws Exception {
        when(ocrTextDocumentRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/ocr/documents/99"))
                .andExpect(status().isFound()) // Redirects
                .andExpect(redirectedUrl("/ocr"))
                .andExpect(flash().attribute("ocrError", "Document not found with ID: 99"));
    }

    @Test
    public void testDeleteDocument_Success() throws Exception {
        OcrTextDocument doc = new OcrTextDocument("delete_me.png", "text", "eng");
        doc.setId(6L);
        when(ocrTextDocumentRepository.findById(6L)).thenReturn(Optional.of(doc));

        mockMvc.perform(post("/ocr/documents/6/delete"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Document deleted successfully")));
    }

    @Test
    public void testDeleteDocument_NotFound() throws Exception {
        when(ocrTextDocumentRepository.findById(100L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/ocr/documents/100/delete"))
                .andExpect(status().isBadRequest()) // As per OcrController logic
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Document not found with ID: 100")));
    }
}