package com.pdf.marsk.pdfdemo.controller;

import com.pdf.marsk.pdfdemo.service.PdfMergeService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException; // Added import
import java.util.List;
import org.springframework.http.HttpHeaders; // Added import

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WebMvcTest(PdfMergeController.class)
class PdfMergeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PdfMergeService pdfMergeService;

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
    void mergePage_shouldReturnMergeView() throws Exception {
        mockMvc.perform(get("/merge"))
                .andExpect(status().isOk())
                .andExpect(view().name("merge"));
    }

    @Test
    void handlePdfMerge_success() throws Exception {
        MockMultipartFile file1 = createDummyPdfPart("pdfFiles", "file1.pdf", 1);
        MockMultipartFile file2 = createDummyPdfPart("pdfFiles", "file2.pdf", 1);

        byte[] mergedPdfBytes = createDummyPdfPart("merged", "merged.pdf", 2).getBytes();
        when(pdfMergeService.mergePdfs(anyList())).thenReturn(mergedPdfBytes);

        MvcResult result = mockMvc.perform(multipart("/merge")
                        .file(file1)
                        .file(file2)
                        .param("outputFilename", "merged_output.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"merged_output.pdf\""))
                .andReturn();
        
        assertEquals(mergedPdfBytes.length, result.getResponse().getContentAsByteArray().length);
    }

    @Test
    void handlePdfMerge_notEnoughFiles_shouldRedirectWithError() throws Exception {
        MockMultipartFile file1 = createDummyPdfPart("pdfFiles", "file1.pdf", 1);

        mockMvc.perform(multipart("/merge").file(file1))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/merge"))
                .andExpect(flash().attribute("errorMessage", "Please select at least two non-empty PDF files to merge."));
    }
    
    @Test
    void handlePdfMerge_noFiles_shouldRedirectWithError() throws Exception {
        mockMvc.perform(multipart("/merge"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/merge"))
                .andExpect(flash().attribute("errorMessage", "Please select at least two PDF files to merge."));
    }


    @Test
    void handlePdfMerge_serviceThrowsIllegalArgumentException_shouldRedirectWithError() throws Exception {
        MockMultipartFile file1 = createDummyPdfPart("pdfFiles", "file1.pdf", 1);
        MockMultipartFile file2 = createDummyPdfPart("pdfFiles", "file2.pdf", 1);

        when(pdfMergeService.mergePdfs(anyList())).thenThrow(new IllegalArgumentException("Test error"));

        mockMvc.perform(multipart("/merge")
                        .file(file1)
                        .file(file2))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/merge"))
                .andExpect(flash().attribute("errorMessage", "Test error"));
    }

    @Test
    void handlePdfMerge_serviceThrowsIOException_shouldRedirectWithError() throws Exception {
        MockMultipartFile file1 = createDummyPdfPart("pdfFiles", "file1.pdf", 1);
        MockMultipartFile file2 = createDummyPdfPart("pdfFiles", "file2.pdf", 1);

        when(pdfMergeService.mergePdfs(anyList())).thenThrow(new IOException("Merge failed"));

        mockMvc.perform(multipart("/merge")
                        .file(file1)
                        .file(file2))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/merge"))
                .andExpect(flash().attribute("errorMessage", "An error occurred while merging the PDF files. Please ensure all files are valid PDFs."));
    }
    
    @Test
    void handlePdfMerge_defaultOutputFilename() throws Exception {
        MockMultipartFile file1 = createDummyPdfPart("pdfFiles", "file1.pdf", 1);
        MockMultipartFile file2 = createDummyPdfPart("pdfFiles", "file2.pdf", 1);

        byte[] mergedPdfBytes = createDummyPdfPart("merged", "merged.pdf", 2).getBytes();
        when(pdfMergeService.mergePdfs(anyList())).thenReturn(mergedPdfBytes);

        mockMvc.perform(multipart("/merge")
                        .file(file1)
                        .file(file2))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("attachment; filename=\"merged_")));
    }
}