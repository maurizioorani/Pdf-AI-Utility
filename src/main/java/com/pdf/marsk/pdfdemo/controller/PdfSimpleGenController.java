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
        }        // Clean and prepare HTML content for XHTML compliance
        String cleanedHtml = cleanHtmlForXhtml(htmlContent);
        
        // Ensure the HTML content is wrapped in a proper XHTML structure with entity declarations
        String wellFormedHtml = createWellFormedXhtml(cleanedHtml);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            // It's crucial that the htmlContent is a well-formed XHTML document
            // For simple cases, this might work. For complex HTML, it might need wrapping.
            builder.withHtmlContent(wellFormedHtml, null); // Use the well-formed HTML
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
    
    /**
     * Clean HTML content for XHTML compliance by replacing HTML entities with numeric equivalents
     * @param htmlContent The original HTML content
     * @return HTML content with entities replaced by numeric equivalents
     */
    private String cleanHtmlForXhtml(String htmlContent) {
        if (htmlContent == null) {
            return "";
        }
        
        // Replace common HTML entities with their numeric equivalents
        // This ensures XHTML compliance without requiring entity declarations
        return htmlContent
            .replace("&nbsp;", "&#160;")      // Non-breaking space
            .replace("&amp;", "&#38;")        // Ampersand
            .replace("&lt;", "&#60;")         // Less than
            .replace("&gt;", "&#62;")         // Greater than
            .replace("&quot;", "&#34;")       // Double quote
            .replace("&apos;", "&#39;")       // Apostrophe
            .replace("&copy;", "&#169;")      // Copyright symbol
            .replace("&reg;", "&#174;")       // Registered trademark
            .replace("&trade;", "&#8482;")    // Trademark
            .replace("&euro;", "&#8364;")     // Euro symbol
            .replace("&pound;", "&#163;")     // Pound sterling
            .replace("&yen;", "&#165;")       // Yen symbol
            .replace("&cent;", "&#162;")      // Cent symbol
            .replace("&sect;", "&#167;")      // Section symbol
            .replace("&para;", "&#182;")      // Paragraph symbol
            .replace("&middot;", "&#183;")    // Middle dot
            .replace("&laquo;", "&#171;")     // Left angle quotes
            .replace("&raquo;", "&#187;")     // Right angle quotes
            .replace("&ndash;", "&#8211;")    // En dash
            .replace("&mdash;", "&#8212;")    // Em dash
            .replace("&hellip;", "&#8230;");  // Horizontal ellipsis
    }
    
    /**
     * Create a well-formed XHTML document structure from HTML content
     * @param htmlContent The cleaned HTML content
     * @return A complete, well-formed XHTML document
     */
    private String createWellFormedXhtml(String htmlContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            htmlContent = "<p>No content provided</p>";
        }
        
        // Check if the content already has a complete document structure
        String trimmedContent = htmlContent.trim();
        boolean hasDoctype = trimmedContent.toLowerCase().startsWith("<!doctype");
        boolean hasHtmlTag = trimmedContent.toLowerCase().contains("<html");
        
        if (hasDoctype && hasHtmlTag) {
            // Content already appears to be a complete document
            return htmlContent;
        }
        
        // Check if content has body/head structure
        boolean hasBodyTag = trimmedContent.toLowerCase().contains("<body");
        boolean hasHeadTag = trimmedContent.toLowerCase().contains("<head");
        
        StringBuilder xhtmlBuilder = new StringBuilder();
        
        // Add XHTML DOCTYPE and document structure
        xhtmlBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xhtmlBuilder.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" ");
        xhtmlBuilder.append("\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n");
        xhtmlBuilder.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n");
        
        if (!hasHeadTag) {
            xhtmlBuilder.append("<head>\n");
            xhtmlBuilder.append("<title>Generated PDF Document</title>\n");
            xhtmlBuilder.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
            xhtmlBuilder.append("</head>\n");
        }
        
        if (!hasBodyTag) {
            xhtmlBuilder.append("<body>\n");
            xhtmlBuilder.append(htmlContent);
            xhtmlBuilder.append("\n</body>\n");
        } else {
            xhtmlBuilder.append(htmlContent);
        }
        
        xhtmlBuilder.append("</html>");
        
        return xhtmlBuilder.toString();
    }
}