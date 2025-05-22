package com.pdf.marsk.pdfdemo.controller;

import com.pdf.marsk.pdfdemo.service.PdfSplitService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@WebMvcTest(PdfSplitController.class)
class PdfSplitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PdfSplitService pdfSplitService;

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
    void splitPage_shouldReturnSplitView() throws Exception {
        mockMvc.perform(get("/split"))
                .andExpect(status().isOk())
                .andExpect(view().name("split"));
    }

    @Test
    void handlePdfSplit_everyPage_success() throws Exception {
        MockMultipartFile file = createDummyPdfPart("pdfFile", "test.pdf", 3);
        byte[] zipBytes = "zip_archive_data".getBytes(); // Dummy zip data
        when(pdfSplitService.splitPdfEveryPage(any(MockMultipartFile.class), anyString())).thenReturn(zipBytes);

        MvcResult result = mockMvc.perform(multipart("/split")
                        .file(file)
                        .param("splitOption", "every_page"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"test_pages.zip\""))
                .andReturn();
        assertEquals(zipBytes.length, result.getResponse().getContentAsByteArray().length);
    }

    @Test
    void handlePdfSplit_emptyFile_shouldRedirectWithError() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("pdfFile", "", "application/pdf", new byte[0]);
        mockMvc.perform(multipart("/split").file(emptyFile).param("splitOption", "every_page"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/split"))
                .andExpect(flash().attribute("errorMessage", "Please select a PDF file to split."));
    }

    @Test
    void handlePdfSplit_fileTooLarge_shouldRedirectWithError() throws Exception {
        byte[] largeContent = new byte[201 * 1024 * 1024]; // 201 MB
        MockMultipartFile largeFile = new MockMultipartFile("pdfFile", "large.pdf", "application/pdf", largeContent);

        mockMvc.perform(multipart("/split").file(largeFile).param("splitOption", "every_page"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/split"))
                .andExpect(flash().attributeExists("errorMessage"));
    }
    
    @Test
    void handlePdfSplit_invalidSplitOption_shouldRedirectWithError() throws Exception {
        MockMultipartFile file = createDummyPdfPart("pdfFile", "test.pdf", 1);
        mockMvc.perform(multipart("/split").file(file).param("splitOption", "invalid_option"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/split"))
                .andExpect(flash().attribute("errorMessage", "Invalid split option selected."));
    }

    @Test
    void handlePdfSplit_serviceThrowsIllegalArgument_shouldRedirectWithError() throws Exception {
        MockMultipartFile file = createDummyPdfPart("pdfFile", "test.pdf", 1);
        when(pdfSplitService.splitPdfEveryPage(any(MockMultipartFile.class), anyString())).thenThrow(new IllegalArgumentException("Bad PDF for split"));

        mockMvc.perform(multipart("/split").file(file).param("splitOption", "every_page"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/split"))
                .andExpect(flash().attribute("errorMessage", "Bad PDF for split"));
    }

    @Test
    void handlePdfSplit_serviceThrowsIOException_shouldRedirectWithError() throws Exception {
        MockMultipartFile file = createDummyPdfPart("pdfFile", "test.pdf", 1);
        when(pdfSplitService.splitPdfEveryPage(any(MockMultipartFile.class), anyString())).thenThrow(new IOException("Split failed"));

        mockMvc.perform(multipart("/split").file(file).param("splitOption", "every_page"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/split"))
                .andExpect(flash().attribute("errorMessage", "An error occurred while splitting the PDF. Ensure it's a valid, non-encrypted PDF."));
    }
}