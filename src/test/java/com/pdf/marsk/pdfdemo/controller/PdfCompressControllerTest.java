package com.pdf.marsk.pdfdemo.controller;

import com.pdf.marsk.pdfdemo.service.PdfCompressionService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@WebMvcTest(PdfCompressController.class)
class PdfCompressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PdfCompressionService pdfCompressionService;

    private MockMultipartFile createDummyPdfPart(String name, String originalFilename, int pages) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return new MockMultipartFile(name, originalFilename, "application/pdf", baos.toByteArray());
        }
    }

    @Test
    void compressPage_shouldReturnCompressView() throws Exception {
        mockMvc.perform(get("/compress"))
                .andExpect(status().isOk())
                .andExpect(view().name("compress"));
    }

    @Test
    void handlePdfCompression_success_noImageCompression() throws Exception {
        MockMultipartFile file = createDummyPdfPart("pdfFile", "test.pdf", 1);
        byte[] compressedBytes = "compressed_pdf_data".getBytes();
        when(pdfCompressionService.compressPdf(any(MockMultipartFile.class), anyBoolean())).thenReturn(compressedBytes);

        MvcResult result = mockMvc.perform(multipart("/compress")
                        .file(file)
                        .param("compressImages", "false")
                        .param("outputFilename", "optimized_test.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"optimized_test.pdf\""))
                .andReturn();
        assertEquals(compressedBytes.length, result.getResponse().getContentAsByteArray().length);
    }

    @Test
    void handlePdfCompression_success_withImageCompression() throws Exception {
        MockMultipartFile file = createDummyPdfPart("pdfFile", "image_doc.pdf", 1);
        byte[] compressedBytes = "compressed_image_pdf_data".getBytes();
        when(pdfCompressionService.compressPdf(any(MockMultipartFile.class), anyBoolean())).thenReturn(compressedBytes);

        MvcResult result = mockMvc.perform(multipart("/compress")
                        .file(file)
                        .param("compressImages", "true")
                        .param("outputFilename", "optimized_image_doc.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"optimized_image_doc.pdf\""))
                .andReturn();
        assertEquals(compressedBytes.length, result.getResponse().getContentAsByteArray().length);
    }
    
    @Test
    void handlePdfCompression_defaultOutputFilename() throws Exception {
        MockMultipartFile file = createDummyPdfPart("pdfFile", "original.pdf", 1);
        byte[] compressedBytes = "data".getBytes();
        when(pdfCompressionService.compressPdf(any(MockMultipartFile.class), anyBoolean())).thenReturn(compressedBytes);

        mockMvc.perform(multipart("/compress").file(file))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"compressed_original.pdf\""));
    }


    @Test
    void handlePdfCompression_emptyFile_shouldRedirectWithError() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("pdfFile", "", "application/pdf", new byte[0]);
        mockMvc.perform(multipart("/compress").file(emptyFile))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/compress"))
                .andExpect(flash().attribute("errorMessage", "Please select a PDF file to compress."));
    }

    @Test
    void handlePdfCompression_fileTooLarge_shouldRedirectWithError() throws Exception {
        byte[] largeContent = new byte[201 * 1024 * 1024]; // 201 MB
        MockMultipartFile largeFile = new MockMultipartFile("pdfFile", "large.pdf", "application/pdf", largeContent);

        // Note: Spring's MaxUploadSizeExceededException might be thrown before this controller logic
        // if spring.servlet.multipart.max-file-size is smaller than 201MB.
        // This test assumes Spring's limit is higher and controller's custom check is hit.
        // If Spring's limit is hit first, this test would need adjustment or a different setup.
        // For now, we test the controller's specific logic.
        mockMvc.perform(multipart("/compress").file(largeFile))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/compress"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void handlePdfCompression_serviceThrowsIllegalArgument_shouldRedirectWithError() throws Exception {
        MockMultipartFile file = createDummyPdfPart("pdfFile", "test.pdf", 1);
        when(pdfCompressionService.compressPdf(any(MockMultipartFile.class), anyBoolean())).thenThrow(new IllegalArgumentException("Bad PDF"));

        mockMvc.perform(multipart("/compress").file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/compress"))
                .andExpect(flash().attribute("errorMessage", "Bad PDF"));
    }

    @Test
    void handlePdfCompression_serviceThrowsIOException_shouldRedirectWithError() throws Exception {
        MockMultipartFile file = createDummyPdfPart("pdfFile", "test.pdf", 1);
        when(pdfCompressionService.compressPdf(any(MockMultipartFile.class), anyBoolean())).thenThrow(new IOException("Compression failed"));

        mockMvc.perform(multipart("/compress").file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/compress"))
                .andExpect(flash().attribute("errorMessage", "An error occurred while compressing the PDF file. Ensure it is a valid, non-encrypted PDF."));
    }
}