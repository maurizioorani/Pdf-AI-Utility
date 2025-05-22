package com.pdf.marsk.pdfdemo.controller;

import com.pdf.marsk.pdfdemo.model.HtmlTemplate;
import com.pdf.marsk.pdfdemo.repository.HtmlTemplateRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/")
public class DocumentConversionController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentConversionController.class);

    @Autowired
    private HtmlTemplateRepository htmlTemplateRepository;

    // --- Home Page (from PdfController) ---
    @GetMapping
    public String home(Model model) {
        List<HtmlTemplate> htmlTemplates = htmlTemplateRepository.findAll();
        model.addAttribute("htmlTemplates", htmlTemplates);
        model.addAttribute("title", "PDF Utility Application - Home"); // Updated title
        logger.info("Serving home page with {} templates.", htmlTemplates.size());
        return "home";
    }

    // --- HTML/Markdown Upload and Save (from ConvertController) ---
    @GetMapping("/upload-convert") // Renamed from "/convert" for clarity
    public String showUploadAndConvertPage(Model model) {
        List<HtmlTemplate> savedFiles = htmlTemplateRepository.findAll();
        model.addAttribute("savedFiles", savedFiles);
        logger.info("Fetched {} saved files for the upload/convert page.", savedFiles.size());

        if (!model.containsAttribute("message") && !model.containsAttribute("error")) {
            if (savedFiles.isEmpty()) {
                model.addAttribute("message", "Upload an HTML or Markdown file to save and convert to PDF. No files saved yet.");
            } else {
                model.addAttribute("message", "Upload a new HTML or Markdown file, or convert one from the list below.");
            }
        }
        model.addAttribute("title", "Upload & Convert Document");
        return "convert"; // Assumes 'convert.html' view exists and is appropriate
    }

    @PostMapping("/upload-convert/save") // Renamed from "/convert/save"
    public String saveUploadedFile(@RequestParam("file") MultipartFile file,
                                   RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please select a file to upload.");
            logger.warn("File save attempt with empty file.");
            return "redirect:/upload-convert";
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty() ||
            !(originalFilename.toLowerCase().endsWith(".html") ||
              originalFilename.toLowerCase().endsWith(".htm") ||
              originalFilename.toLowerCase().endsWith(".md"))) {
            redirectAttributes.addFlashAttribute("error", "Invalid file type or name. Please upload an HTML or Markdown file.");
            logger.warn("File save attempt with invalid filename or type: {}", originalFilename);
            return "redirect:/upload-convert";
        }

        try {
            byte[] fileContentBytes = file.getBytes();
            String fileContentString = new String(fileContentBytes, StandardCharsets.UTF_8);

            HtmlTemplate newTemplate = new HtmlTemplate();
            newTemplate.setName(originalFilename);
            newTemplate.setHtmlContent(fileContentString); // Content is stored as is, type inferred from name at conversion

            htmlTemplateRepository.save(newTemplate);
            logger.info("Successfully saved uploaded file: {}", originalFilename);
            redirectAttributes.addFlashAttribute("message", "File '" + originalFilename + "' saved successfully.");

        } catch (IOException e) {
            logger.error("IOException during file saving for: " + originalFilename, e);
            redirectAttributes.addFlashAttribute("error", "Failed to save file due to an IO error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected exception during file saving for: " + originalFilename, e);
            redirectAttributes.addFlashAttribute("error", "An unexpected error occurred during file saving: " + e.getMessage());
        }
        return "redirect:/upload-convert";
    }

    // --- Template Management (from PdfController) ---
    @GetMapping("/templates") // Was "/generate" in PdfController
    public String showTemplateManagementPage(Model model) {
        model.addAttribute("htmlTemplate", new HtmlTemplate()); // For new template form
        model.addAttribute("htmlTemplates", htmlTemplateRepository.findAll());
        model.addAttribute("title", "Manage PDF Templates");
        logger.info("Serving template management page.");
        return "generate"; // Assumes 'generate.html' view is for template management
    }

    @PostMapping("/templates/save")
    public String saveHtmlTemplate(@Valid @ModelAttribute("htmlTemplate") HtmlTemplate htmlTemplate,
                                   BindingResult bindingResult,
                                   RedirectAttributes redirectAttributes,
                                   Model model) {
        if (bindingResult.hasErrors()) {
            logger.warn("Validation errors while saving template: {}", bindingResult.getAllErrors());
            model.addAttribute("htmlTemplates", htmlTemplateRepository.findAll()); // Reload for display
            model.addAttribute("title", "Manage PDF Templates");
            return "generate"; // Return to form with errors
        }
        try {
            htmlTemplateRepository.save(htmlTemplate);
            redirectAttributes.addFlashAttribute("message", "Template saved successfully!");
            logger.info("Saved template: {}", htmlTemplate.getName());
        } catch (Exception e) {
            logger.error("Error saving template: {}", htmlTemplate.getName(), e);
            redirectAttributes.addFlashAttribute("error", "Error saving template: " + e.getMessage());
        }
        return "redirect:/templates";
    }

    @GetMapping("/templates/preview-page") // Renamed from /generate/preview for clarity
    public String previewTemplatePage(@RequestParam Long templateId, Model model) {
        Optional<HtmlTemplate> templateOpt = htmlTemplateRepository.findById(templateId);
        if (templateOpt.isPresent()) {
            model.addAttribute("htmlContent", templateOpt.get().getHtmlContent());
            model.addAttribute("templateName", templateOpt.get().getName());
            model.addAttribute("templateId", templateId);
            model.addAttribute("title", "Preview Template: " + templateOpt.get().getName());
            logger.info("Serving preview page for template ID: {}", templateId);
            return "preview"; // Assumes 'preview.html' view exists
        } else {
            logger.warn("Preview page requested for non-existent template ID: {}", templateId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found");
        }
    }

    @GetMapping("/templates/preview-raw") // Renamed from /generate/previewById
    public ResponseEntity<String> previewRawHtmlTemplate(@RequestParam Long templateId) {
        Optional<HtmlTemplate> templateOpt = htmlTemplateRepository.findById(templateId);
        if (templateOpt.isPresent()) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_HTML);
            logger.info("Serving raw HTML preview for template ID: {}", templateId);
            return new ResponseEntity<>(templateOpt.get().getHtmlContent(), headers, HttpStatus.OK);
        } else {
            logger.warn("Raw preview requested for non-existent template ID: {}", templateId);
            return new ResponseEntity<>("Template not found", HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/templates/delete/{id}")
    public String deleteTemplate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Optional<HtmlTemplate> templateOpt = htmlTemplateRepository.findById(id);
        if (templateOpt.isPresent()) {
            try {
                htmlTemplateRepository.deleteById(id);
                redirectAttributes.addFlashAttribute("message", "Template '" + templateOpt.get().getName() + "' deleted successfully!");
                logger.info("Deleted template with ID: {}", id);
            } catch (Exception e) {
                logger.error("Error deleting template with ID {}: {}", id, e.getMessage(), e);
                redirectAttributes.addFlashAttribute("error", "Error deleting template: " + e.getMessage());
            }
        } else {
            logger.warn("Attempted to delete non-existent template with ID: {}", id);
            redirectAttributes.addFlashAttribute("error", "Could not find template to delete.");
        }
        return "redirect:/templates";
    }

    // --- PDF Conversion from Saved Template (Combined from ConvertController & PdfController) ---
    // Path /convert/file/{id} from ConvertController and /generate/fromTemplate from PdfController
    // are merged into this one.
    @GetMapping("/templates/convert-to-pdf/{id}")
    public ResponseEntity<?> convertTemplateToPdf(@PathVariable("id") Long id) {
        logger.info("Attempting to convert saved template with ID: {}", id);
        Optional<HtmlTemplate> templateOptional = htmlTemplateRepository.findById(id);

        if (!templateOptional.isPresent()) {
            logger.warn("No template found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN).body("File not found with ID: " + id);
        }

        HtmlTemplate template = templateOptional.get();
        String contentToConvert = template.getHtmlContent();
        String savedFilename = template.getName();

        if (contentToConvert == null || contentToConvert.trim().isEmpty()) {
            logger.error("Content for template ID {} is empty.", id);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.TEXT_PLAIN).body("Cannot convert: The content of the saved file is empty.");
        }
        if (savedFilename == null || savedFilename.trim().isEmpty()) {
            logger.error("Filename for template ID {} is missing.", id);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.TEXT_PLAIN).body("Cannot convert: The filename of the saved file is missing.");
        }

        try {
            byte[] pdfBytes;
            String lowerCaseFilename = savedFilename.toLowerCase();

            if (lowerCaseFilename.endsWith(".html") || lowerCaseFilename.endsWith(".htm")) {
                logger.info("Converting saved HTML file: {}", savedFilename);
                pdfBytes = convertHtmlToPdfUtility(contentToConvert);
            } else if (lowerCaseFilename.endsWith(".md")) {
                logger.info("Converting saved Markdown file: {}", savedFilename);
                String htmlContentFromMarkdown = convertMarkdownToHtmlUtility(contentToConvert);
                pdfBytes = convertHtmlToPdfUtility(htmlContentFromMarkdown);
            } else {
                logger.warn("Unsupported file type for saved file: {}", savedFilename);
                return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("Unsupported file type for saved file: " + savedFilename + ". Cannot convert.");
            }

            if (pdfBytes == null || pdfBytes.length == 0) {
                logger.error("PDF conversion resulted in empty output for saved file: {}", savedFilename);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.TEXT_PLAIN).body("Error during PDF conversion for saved file: The resulting PDF was empty.");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            String outputFilename = savedFilename.substring(0, savedFilename.lastIndexOf('.')) + ".pdf";
            headers.setContentDisposition(ContentDisposition.attachment().filename(outputFilename).build());
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            logger.info("Successfully converted saved file '{}' to '{}'. PDF size: {} bytes.", savedFilename, outputFilename, pdfBytes.length);
            return ResponseEntity.ok().headers(headers).body(pdfBytes);

        } catch (IOException e) {
            logger.error("IOException during conversion of saved file: " + savedFilename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.TEXT_PLAIN).body("Failed to convert saved file due to an IO error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected exception during conversion of saved file: " + savedFilename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.TEXT_PLAIN).body("An unexpected error occurred during conversion of saved file: " + e.getMessage());
        }
    }

    // --- Utility Methods (from ConvertController) ---
    private byte[] convertHtmlToPdfUtility(String htmlContent) throws IOException {
        logger.debug("Starting HTML to PDF conversion utility.");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(htmlContent, null); // Base URI null, assuming self-contained HTML
            builder.toStream(baos);
            builder.run();
            byte[] pdfBytes = baos.toByteArray();
            logger.info("HTML to PDF conversion utility successful. PDF size: {} bytes.", pdfBytes.length);
            return pdfBytes;
        } catch (Exception e) {
            logger.error("Error converting HTML to PDF in utility: {}", e.getMessage(), e);
            throw new IOException("Failed to convert HTML to PDF. Reason: " + e.getMessage(), e);
        }
    }

    private String convertMarkdownToHtmlUtility(String markdownContent) {
        logger.debug("Starting Markdown to HTML conversion utility.");
        Parser parser = Parser.builder()
                .extensions(Collections.singletonList(TablesExtension.create()))
                .build();
        Node document = parser.parse(markdownContent);
        HtmlRenderer renderer = HtmlRenderer.builder()
                .extensions(Collections.singletonList(TablesExtension.create()))
                .build();
        String htmlFragment = renderer.render(document);
        logger.info("Markdown to HTML fragment conversion utility successful.");

        // Wrap in full HTML structure for openhtmltopdf
        StringBuilder fullHtml = new StringBuilder();
        fullHtml.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n");
        fullHtml.append("<head>\n");
        fullHtml.append("  <meta charset=\"UTF-8\" />\n");
        fullHtml.append("  <title>Markdown Document</title>\n");
        fullHtml.append("  <style type=\"text/css\">\n");
        fullHtml.append("    body { font-family: sans-serif; line-height: 1.6; margin: 20px; }\n");
        fullHtml.append("    h1, h2, h3, h4, h5, h6 { margin-top: 1.5em; margin-bottom: 0.5em; color: #333; }\n");
        fullHtml.append("    p { margin-bottom: 1em; }\n");
        fullHtml.append("    ul, ol { margin-bottom: 1em; padding-left: 2em; }\n");
        fullHtml.append("    li { margin-bottom: 0.25em; }\n");
        fullHtml.append("    code { font-family: monospace; background-color: #f0f0f0; padding: 0.2em 0.4em; border-radius: 3px; }\n");
        fullHtml.append("    pre { background-color: #f0f0f0; padding: 1em; border: 1px solid #ddd; border-radius: 3px; overflow-x: auto; }\n");
        fullHtml.append("    pre code { padding: 0; background-color: transparent; border: none; border-radius: 0; }\n");
        fullHtml.append("    table { border-collapse: collapse; margin-bottom: 1em; width: auto; border: 1px solid #ccc; }\n");
        fullHtml.append("    th, td { border: 1px solid #ccc; padding: 0.5em; text-align: left; }\n");
        fullHtml.append("    th { background-color: #f7f7f7; }\n");
        fullHtml.append("    blockquote { border-left: 3px solid #eee; margin-left: 0; padding-left: 1em; color: #555; }\n");
        fullHtml.append("    hr { border: 0; border-top: 1px solid #ccc; margin: 2em 0; }\n");
        fullHtml.append("    a { color: #007bff; text-decoration: none; }\n");
        fullHtml.append("    a:hover { text-decoration: underline; }\n");
        fullHtml.append("    img { max-width: 100%; height: auto; }\n");
        fullHtml.append("  </style>\n");
        fullHtml.append("</head>\n");
        fullHtml.append("<body>\n");
        fullHtml.append(htmlFragment);
        fullHtml.append("\n</body>\n");
        fullHtml.append("</html>");

        logger.info("Wrapped Markdown HTML in full XHTML document structure in utility.");
        return fullHtml.toString();
    }
}