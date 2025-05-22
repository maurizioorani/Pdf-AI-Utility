package com.pdf.marsk.pdfdemo.controller; // Updated package

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;


@Controller
@RequestMapping("/simple-generate")
public class PdfSimpleGenController {

    private static final Logger logger = LoggerFactory.getLogger(PdfSimpleGenController.class);

    // Removed HtmlTemplateRepository as this controller is now for direct HTML input only

    @GetMapping
    public String showSimpleGeneratePage(Model model) {
        model.addAttribute("title", "Simple PDF Generator");
        // The view "simple_generate.html" should have a textarea with name="htmlContent"
        return "simple_generate";
    }
    
    @PostMapping("/create")
    public ResponseEntity<byte[]> generateSimplePdf(@RequestParam(name = "htmlContent", required = false) String htmlContent) {
        logger.info("Received request to convert direct HTML content to PDF");

        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            logger.warn("Attempted to generate PDF with empty or missing HTML content.");
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("HTML content cannot be empty.".getBytes());
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            // It's crucial that the htmlContent is a well-formed XHTML document
            // For simple cases, this might work. For complex HTML, it might need wrapping.
            builder.withHtmlContent(htmlContent, null);
            builder.toStream(baos);
            builder.run();
            byte[] pdfBytes = baos.toByteArray();

            if (pdfBytes == null || pdfBytes.length == 0) {
                logger.error("PDF generation from direct HTML content resulted in empty output.");
                return ResponseEntity.status(500).contentType(MediaType.TEXT_PLAIN).body("Error: Generated PDF is empty.".getBytes());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            // Using ContentDisposition builder for cleaner header
            headers.setContentDisposition(ContentDisposition.attachment().filename("generated_document.pdf").build());
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            
            logger.info("Successfully generated PDF from direct HTML content. PDF size: {} bytes.", pdfBytes.length);
            return ResponseEntity.ok().headers(headers).body(pdfBytes);

        } catch (IOException e) {
            logger.error("IOException during PDF generation from direct HTML content: {}", e.getMessage(), e);
            return ResponseEntity.status(500).contentType(MediaType.TEXT_PLAIN).body(("IO Error during PDF generation: " + e.getMessage()).getBytes());
        } catch (Exception e) {
            // Catching generic Exception to handle potential issues from PdfRendererBuilder if HTML is not well-formed
            logger.error("Unexpected exception during PDF generation from direct HTML content (possibly malformed HTML): {}", e.getMessage(), e);
            return ResponseEntity.status(500).contentType(MediaType.TEXT_PLAIN).body(("Error during PDF generation (possibly malformed HTML): " + e.getMessage()).getBytes());
        }
    }
}