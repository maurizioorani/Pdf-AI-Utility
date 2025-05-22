package com.pdf.marsk.pdfdemo.controller; // Updated package

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains; // Added for PdfControllerTest merge
import static org.hamcrest.Matchers.hasProperty; // Added for PdfControllerTest merge
import static org.hamcrest.Matchers.hasSize; // Added for PdfControllerTest merge
import static org.hamcrest.Matchers.is; // Added for PdfControllerTest merge
import static org.junit.jupiter.api.Assertions.assertEquals; // Added for PdfControllerTest merge
import static org.junit.jupiter.api.Assertions.assertTrue; // Added for PdfControllerTest merge
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor; // Added for PdfControllerTest merge
import static org.mockito.ArgumentMatchers.any; // Added for PdfControllerTest merge
import static org.mockito.ArgumentMatchers.anyLong; // Added for PdfControllerTest merge
import static org.mockito.Mockito.doNothing; // Added for PdfControllerTest merge
import static org.mockito.Mockito.never; // Added for PdfControllerTest merge
import static org.mockito.Mockito.times; // Added for PdfControllerTest merge
import static org.mockito.Mockito.verify; // Added for PdfControllerTest merge
import static org.mockito.Mockito.when; // Added for PdfControllerTest merge
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean; // Added for PdfControllerTest merge
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult; // Added for PdfControllerTest merge
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // Added for PdfControllerTest merge
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content; // Added for PdfControllerTest merge
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header; // Added for PdfControllerTest merge

import com.pdf.marsk.pdfdemo.model.HtmlTemplate; // Added for PdfControllerTest merge
import com.pdf.marsk.pdfdemo.repository.HtmlTemplateRepository; // Added for PdfControllerTest merge

import java.util.Arrays; // Added for PdfControllerTest merge
import java.util.Optional; // Added for PdfControllerTest merge

import org.springframework.test.context.ActiveProfiles; // Added

@ActiveProfiles("test") // Added to explicitly activate the test profile
@SpringBootTest // Removed properties = "spring.profiles.active=test" as @ActiveProfiles is preferred
@AutoConfigureMockMvc
public class DocumentConversionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean // Added for PdfControllerTest merge
    private HtmlTemplateRepository htmlTemplateRepository; // Added for PdfControllerTest merge

    @Test
    public void testUploadConvertPageLoads() throws Exception { // Renamed test and updated URL
        mockMvc.perform(get("/upload-convert")) // Updated URL
                .andExpect(status().isOk())
                .andExpect(view().name("convert")) // View name remains 'convert' as per DocumentConversionController
                .andExpect(model().attributeExists("savedFiles"))
                .andExpect(model().attributeExists("message"));
    }

    @Test
    public void testSaveUploadedFile_Html_Success() throws Exception {
        String htmlContent = "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Test HTML</title></head><body><h1>Hello HTML</h1></body></html>";
        MockMultipartFile htmlFile = new MockMultipartFile(
                "file",
                "test.html",
                MediaType.TEXT_HTML_VALUE,
                htmlContent.getBytes()
        );

        mockMvc.perform(multipart("/upload-convert/save").file(htmlFile)) // Updated URL
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/upload-convert")) // Updated redirect URL
                .andExpect(flash().attribute("message", containsString("File 'test.html' saved successfully.")));
    }

    @Test
    public void testSaveUploadedFile_Markdown_Success() throws Exception {
        String mdContent = "# Test Markdown\n\nThis is a test paragraph.";
        MockMultipartFile mdFile = new MockMultipartFile(
                "file",
                "test.md",
                "text/markdown",
                mdContent.getBytes()
        );

        mockMvc.perform(multipart("/upload-convert/save").file(mdFile)) // Updated URL
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/upload-convert")) // Updated redirect URL
                .andExpect(flash().attribute("message", containsString("File 'test.md' saved successfully.")));
    }

    @Test
    public void testSaveUploadedFile_EmptyFile() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.txt",
                MediaType.TEXT_PLAIN_VALUE,
                new byte[0]
        );

        mockMvc.perform(multipart("/upload-convert/save").file(emptyFile)) // Updated URL
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/upload-convert")) // Updated redirect URL
                .andExpect(flash().attribute("error", "Please select a file to upload."));
    }

    @Test
    public void testSaveUploadedFile_UnsupportedFileType() throws Exception {
        MockMultipartFile unsupportedFile = new MockMultipartFile(
                "file",
                "test.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "This is a plain text file.".getBytes()
        );

        mockMvc.perform(multipart("/upload-convert/save").file(unsupportedFile)) // Updated URL
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/upload-convert")) // Updated redirect URL
                .andExpect(flash().attribute("error", "Invalid file type or name. Please upload an HTML or Markdown file."));
    }
    
    @Test
    public void testSaveUploadedFile_MissingFilename() throws Exception {
        MockMultipartFile fileWithNoName = new MockMultipartFile(
            "file",
            null,   // original filename is null
            MediaType.TEXT_HTML_VALUE,
            "some content".getBytes()
        );

        mockMvc.perform(multipart("/upload-convert/save").file(fileWithNoName)) // Updated URL
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/upload-convert")) // Updated redirect URL
            .andExpect(flash().attribute("error", "Invalid file type or name. Please upload an HTML or Markdown file."));
    }

    // --- Tests merged from PdfControllerTest ---

    @Test
    public void testShowTemplateManagementPage() throws Exception { // Renamed from testShowGeneratePage
        HtmlTemplate template1 = new HtmlTemplate();
        template1.setId(1L);
        template1.setName("Test Template 1");
        template1.setHtmlContent("<h1>Template 1</h1>");

        when(htmlTemplateRepository.findAll()).thenReturn(Arrays.asList(template1));

        mockMvc.perform(get("/templates")) // Updated URL from /generate
                .andExpect(status().isOk())
                .andExpect(view().name("generate")) // View name remains 'generate' as per DocumentConversionController
                .andExpect(model().attributeExists("htmlTemplate"))
                .andExpect(model().attribute("htmlTemplates", hasSize(1)))
                .andExpect(model().attribute("htmlTemplates", contains(
                        hasProperty("name", is("Test Template 1"))
                )));
    }

    @Test
    public void testSaveHtmlTemplate_Success() throws Exception {
        String templateName = "My New Template";
        String templateContent = "<div>Content of new template</div>";

        when(htmlTemplateRepository.save(any(HtmlTemplate.class))).thenAnswer(invocation -> {
            HtmlTemplate savedTemplate = invocation.getArgument(0);
            savedTemplate.setId(1L); // Simulate saving by assigning an ID
            return savedTemplate;
        });

        mockMvc.perform(post("/templates/save") // Updated URL from /generate/save
                        .param("name", templateName)
                        .param("htmlContent", templateContent)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/templates")) // Updated redirect URL
                .andExpect(flash().attribute("message", containsString("Template saved successfully!")));

        ArgumentCaptor<HtmlTemplate> templateCaptor = ArgumentCaptor.forClass(HtmlTemplate.class);
        verify(htmlTemplateRepository, times(1)).save(templateCaptor.capture());
        assertEquals(templateName, templateCaptor.getValue().getName());
        assertEquals(templateContent, templateCaptor.getValue().getHtmlContent());
    }
    
    @Test
    public void testSaveHtmlTemplate_EmptyName() throws Exception {
        mockMvc.perform(post("/templates/save") // Updated URL from /generate/save
                        .param("name", "")
                        .param("htmlContent", "Some content") // Corrected from "content" to "htmlContent"
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(view().name("generate")) // View name remains 'generate'
                .andExpect(model().attributeHasFieldErrors("htmlTemplate", "name"));
    }

    @Test
    public void testPreviewRawHtmlTemplate_Found() throws Exception { // Renamed from testPreviewHtmlTemplateById_Found
        Long templateId = 1L;
        String htmlContent = "<h1>Preview Content</h1>";
        HtmlTemplate template = new HtmlTemplate();
        template.setId(templateId);
        template.setName("Preview Template");
        template.setHtmlContent(htmlContent);

        when(htmlTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));

        mockMvc.perform(get("/templates/preview-raw").param("templateId", templateId.toString())) // Updated URL
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(htmlContent));
    }

    @Test
    public void testPreviewRawHtmlTemplate_NotFound() throws Exception { // Renamed from testPreviewHtmlTemplateById_NotFound
        Long templateId = 99L;
        when(htmlTemplateRepository.findById(templateId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/templates/preview-raw").param("templateId", templateId.toString())) // Updated URL
                .andExpect(status().isNotFound())
                .andExpect(content().string("Template not found"));
    }

    @Test
    public void testConvertTemplateToPdf_Found() throws Exception { // Renamed from testGeneratePdfFromTemplateId_Found
        Long templateId = 1L;
        String htmlContent = "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Test PDF</title></head><body><h1>PDF from Template</h1></body></html>";
        HtmlTemplate template = new HtmlTemplate();
        template.setId(templateId);
        template.setName("PDF_Template.html"); // Changed name to include .html extension
        template.setHtmlContent(htmlContent);

        when(htmlTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));

        MvcResult result = mockMvc.perform(get("/templates/convert-to-pdf/" + templateId.toString())) // Updated URL and use path variable
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"PDF_Template.pdf\"")) // Output filename should still be .pdf
                .andReturn();
        
        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertTrue(pdfBytes.length > 0, "PDF content should not be empty");
        assertTrue(new String(pdfBytes, 0, Math.min(pdfBytes.length, 8)).startsWith("%PDF-"), "Output should be a PDF file");
    }

    @Test
    public void testConvertTemplateToPdf_NotFound() throws Exception { // Renamed from testGeneratePdfFromTemplateId_NotFound
        Long templateId = 99L;
        when(htmlTemplateRepository.findById(templateId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/templates/convert-to-pdf/" + templateId.toString())) // Updated URL and use path variable
                .andExpect(status().isNotFound());
    }
    
    @Test
    public void testDeleteTemplate_Success() throws Exception {
        Long templateId = 1L;
        HtmlTemplate template = new HtmlTemplate();
        template.setId(templateId);
        template.setName("To Be Deleted");
        
        when(htmlTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));
        doNothing().when(htmlTemplateRepository).deleteById(templateId);

        mockMvc.perform(get("/templates/delete/" + templateId)) // Updated URL from /generate/delete/
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/templates")) // Updated redirect URL
                .andExpect(flash().attribute("message", containsString("Template '" + template.getName() + "' deleted successfully!"))); // Updated message to match controller
        
        verify(htmlTemplateRepository, times(1)).deleteById(templateId);
    }

    @Test
    public void testDeleteTemplate_NotFound() throws Exception {
        Long templateId = 99L;
        when(htmlTemplateRepository.findById(templateId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/templates/delete/" + templateId)) // Updated URL from /generate/delete/
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/templates")) // Updated redirect URL
                .andExpect(flash().attribute("error", containsString("Could not find template to delete.")));
        
        verify(htmlTemplateRepository, never()).deleteById(anyLong());
    }
}