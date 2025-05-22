package com.pdf.marsk.pdfdemo.controller;

import com.pdf.marsk.pdfdemo.service.PdfProtectionService;
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

@WebMvcTest(PdfProtectController.class)
class PdfProtectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PdfProtectionService pdfProtectionService;

    private MockMultipartFile createDummyPdfPart(String name, String originalFilename) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return new MockMultipartFile(name, originalFilename, "application/pdf", baos.toByteArray());
        }
    }

    @Test
    void protectPage_shouldReturnProtectView() throws Exception {
        mockMvc.perform(get("/protect"))
                .andExpect(status().isOk())
                .andExpect(view().name("protect"));
    }

    @Test
    void handlePdfProtection_success() throws Exception {
        MockMultipartFile file = createDummyPdfPart("pdfFile", "test.pdf");
        byte[] protectedBytes = "protected_pdf_data".getBytes();
        when(pdfProtectionService.protectPdf(any(MockMultipartFile.class), anyString(), anyString())).thenReturn(protectedBytes);

        MvcResult result = mockMvc.perform(multipart("/protect")
                        .file(file)
                        .param("userPassword", "user123")
                        .param("ownerPassword", "owner456"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"protected_test.pdf\""))
                .andReturn();
        assertEquals(protectedBytes.length, result.getResponse().getContentAsByteArray().length);
    }
    
    @Test
    void handlePdfProtection_onlyUserPassword() throws Exception {
        MockMultipartFile file = createDummyPdfPart("pdfFile", "test_user_only.pdf");
        byte[] protectedBytes = "protected_data".getBytes();
        // Service will use userPassword as ownerPassword in this case
        when(pdfProtectionService.protectPdf(any(MockMultipartFile.class), anyString(), anyString())).thenReturn(protectedBytes);

        mockMvc.perform(multipart("/protect")
                        .file(file)
                        .param("userPassword", "userpass"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"protected_test_user_only.pdf\""));
    }
    
    @Test
    void handlePdfProtection_onlyOwnerPassword() throws Exception {
        MockMultipartFile file = createDummyPdfPart("pdfFile", "test_owner_only.pdf");
        byte[] protectedBytes = "protected_data".getBytes();
        when(pdfProtectionService.protectPdf(any(MockMultipartFile.class), any(), anyString())).thenReturn(protectedBytes);


        mockMvc.perform(multipart("/protect")
                        .file(file)
                        .param("ownerPassword", "ownerpass"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"protected_test_owner_only.pdf\""));
    }


    @Test
    void handlePdfProtection_emptyFile_shouldRedirectWithError() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("pdfFile", "", "application/pdf", new byte[0]);
        mockMvc.perform(multipart("/protect").file(emptyFile).param("userPassword", "p"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/protect"))
                .andExpect(flash().attribute("errorMessage", "Please select a PDF file to protect."));
    }
    
    @Test
    void handlePdfProtection_noPasswords_shouldRedirectWithError() throws Exception {
        MockMultipartFile file = createDummyPdfPart("pdfFile", "test.pdf");
        mockMvc.perform(multipart("/protect").file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/protect"))
                .andExpect(flash().attribute("errorMessage", "Please provide at least a user password or an owner password."));
    }


    @Test
    void handlePdfProtection_fileTooLarge_shouldRedirectWithError() throws Exception {
        byte[] largeContent = new byte[201 * 1024 * 1024]; // 201 MB
        MockMultipartFile largeFile = new MockMultipartFile("pdfFile", "large.pdf", "application/pdf", largeContent);

        mockMvc.perform(multipart("/protect").file(largeFile).param("userPassword", "p"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/protect"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void handlePdfProtection_serviceThrowsIllegalArgument_shouldRedirectWithError() throws Exception {
        MockMultipartFile file = createDummyPdfPart("pdfFile", "test.pdf");
        when(pdfProtectionService.protectPdf(any(MockMultipartFile.class), anyString(), anyString())).thenThrow(new IllegalArgumentException("Bad PDF for protection"));

        mockMvc.perform(multipart("/protect").file(file).param("userPassword", "p").param("ownerPassword", "o"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/protect"))
                .andExpect(flash().attribute("errorMessage", "Bad PDF for protection"));
    }

    @Test
    void handlePdfProtection_serviceThrowsIOException_shouldRedirectWithError() throws Exception {
        MockMultipartFile file = createDummyPdfPart("pdfFile", "test.pdf");
        when(pdfProtectionService.protectPdf(any(MockMultipartFile.class), anyString(), anyString())).thenThrow(new IOException("Protection failed"));

        mockMvc.perform(multipart("/protect").file(file).param("userPassword", "p").param("ownerPassword", "o"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/protect"))
                .andExpect(flash().attribute("errorMessage", "An error occurred while protecting the PDF. Ensure it's a valid PDF."));
    }
}