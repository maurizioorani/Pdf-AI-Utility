package com.pdf.marsk.pdfdemo.controller; // Updated package

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.springframework.test.context.ActiveProfiles; // Added

@ActiveProfiles("test") // Added to explicitly activate the test profile
@SpringBootTest // Removed properties = "spring.profiles.active=test"
@AutoConfigureMockMvc
public class PdfSimpleGenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testShowSimpleGeneratePage() throws Exception {
        mockMvc.perform(get("/simple-generate"))
                .andExpect(status().isOk())
                .andExpect(view().name("simple_generate")) // Assuming this is the view name
                .andExpect(model().attributeExists("title"));
    }

    @Test
    public void testGenerateSimplePdf_Success() throws Exception {
        String htmlContent = "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Simple PDF</title></head><body><h1>Simple Content</h1></body></html>";

        MvcResult result = mockMvc.perform(post("/simple-generate/create")
                        .param("htmlContent", htmlContent)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"generated_document.pdf\""))
                .andReturn();

        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertTrue(pdfBytes.length > 0, "PDF content should not be empty");
        assertTrue(new String(pdfBytes, 0, Math.min(pdfBytes.length, 8)).startsWith("%PDF-"), "Output should be a PDF file");
    }

    @Test
    public void testGenerateSimplePdf_EmptyHtmlContent() throws Exception {
        mockMvc.perform(post("/simple-generate/create")
                        .param("htmlContent", "")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("HTML content cannot be empty.")));
    }

    @Test
    public void testGenerateSimplePdf_MissingHtmlContentParameter() throws Exception {
        // Test without sending the htmlContent parameter
        mockMvc.perform(post("/simple-generate/create")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest()); // Spring typically returns 400 if a required parameter is missing
    }
}