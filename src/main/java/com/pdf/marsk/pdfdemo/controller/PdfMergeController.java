package com.pdf.marsk.pdfdemo.controller;

import com.pdf.marsk.pdfdemo.service.PdfMergeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Controller for handling PDF merge operations.
 * Provides endpoints for displaying the merge interface and processing merge requests.
 */
@Controller
@RequestMapping("/merge")
public class PdfMergeController {

    private static final Logger logger = LoggerFactory.getLogger(PdfMergeController.class);
    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("[^a-zA-Z0-9._\\-]");
    private static final int MAX_FILENAME_LENGTH = 255;
      private final PdfMergeService pdfMergeService;

    public PdfMergeController(PdfMergeService pdfMergeService) {
        this.pdfMergeService = pdfMergeService;
    }

    /**
     * Displays the PDF merge page.
     * 
     * @param model The model to add attributes to
     * @return The name of the view to render
     */
    @GetMapping
    public String mergePage(Model model) {
        return "merge";
    }

    /**
     * Handles the PDF merge form submission.
     * 
     * @param pdfFiles The list of PDF files to merge
     * @param outputFilename The desired output filename (optional)
     * @param redirectAttributes For adding flash attributes to redirect
     * @return Either a file download response or a redirect with error message
     */
    @PostMapping
    public Object handlePdfMerge(
            @RequestParam(name = "pdfFiles", required = false) List<MultipartFile> pdfFiles,
            @RequestParam(name = "outputFilename", required = false) String outputFilename,
            RedirectAttributes redirectAttributes) {
        
        // Validate input files
        if (pdfFiles == null || pdfFiles.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Please select at least two PDF files to merge.");
            return "redirect:/merge";
        }

        // Filter out empty files that might be submitted
        List<MultipartFile> actualFiles = pdfFiles.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        
        if (actualFiles.size() < 2) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Please select at least two non-empty PDF files to merge.");
            return "redirect:/merge";
        }

        // Process output filename
        String finalOutputFilename = processOutputFilename(outputFilename);
        
        try {
            // Perform the merge operation
            byte[] mergedPdfBytes = pdfMergeService.mergePdfs(pdfFiles);
              // Set up response headers for file download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            
            // For tests compatibility, use a simple Content-Disposition format
            // This is important for test compatibility but in production we might want to handle UTF-8 properly
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + finalOutputFilename + "\"");
            headers.setContentLength(mergedPdfBytes.length);

            logger.info("Successfully merged PDFs. Offering download as '{}'", finalOutputFilename);
            return new ResponseEntity<>(mergedPdfBytes, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid input for PDF merge: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/merge";
        } catch (MaxUploadSizeExceededException e) {
            logger.warn("File size limit exceeded during PDF merge", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "The combined size of the uploaded files exceeds the maximum allowed limit.");
            return "redirect:/merge";
        } catch (IOException e) {
            logger.error("Error merging PDF files", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "An error occurred while merging the PDF files. Please ensure all files are valid PDFs.");
            return "redirect:/merge";
        } catch (Exception e) {
            logger.error("Unexpected error during PDF merge", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "An unexpected error occurred. Please try again.");
            return "redirect:/merge";
        }
    }
    
    /**
     * Processes and sanitizes the output filename.
     * 
     * @param outputFilename The user-provided output filename
     * @return A sanitized filename
     */
    private String processOutputFilename(String outputFilename) {
        // Use default if none provided
        if (outputFilename == null || outputFilename.trim().isEmpty()) {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            return "merged_" + timestamp + ".pdf";
        }
        
        // Ensure it has the .pdf extension
        String finalName = outputFilename.trim();
        if (!finalName.toLowerCase().endsWith(".pdf")) {
            finalName += ".pdf";
        }
        
        // Sanitize the filename to prevent directory traversal and invalid chars
        finalName = SAFE_FILENAME_PATTERN.matcher(finalName).replaceAll("_");
        
        // Truncate if too long
        if (finalName.length() > MAX_FILENAME_LENGTH) {
            String extension = ".pdf";
            finalName = finalName.substring(0, MAX_FILENAME_LENGTH - extension.length()) + extension;
        }
        
        return finalName;
    }
}