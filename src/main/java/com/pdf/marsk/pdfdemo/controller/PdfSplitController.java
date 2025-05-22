package com.pdf.marsk.pdfdemo.controller;

import com.pdf.marsk.pdfdemo.service.PdfSplitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition; // Added import
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Controller
@RequestMapping("/split")
public class PdfSplitController {

    private static final Logger logger = LoggerFactory.getLogger(PdfSplitController.class);
    private final PdfSplitService pdfSplitService;

    @Autowired
    public PdfSplitController(PdfSplitService pdfSplitService) {
        this.pdfSplitService = pdfSplitService;
    }

    @GetMapping
    public String splitPage(Model model) {
        return "split";
    }

    @PostMapping
    public Object handlePdfSplit(@RequestParam("pdfFile") MultipartFile pdfFile,
                                 @RequestParam("splitOption") String splitOption,
                                 // @RequestParam(name = "pageRanges", required = false) String pageRanges, // For future use
                                 RedirectAttributes redirectAttributes) {

        if (pdfFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a PDF file to split.");
            return "redirect:/split";
        }

        long fileSize = pdfFile.getSize();
        long maxFileSize = 200 * 1024 * 1024; // 200 MB
        if (fileSize > maxFileSize) {
            redirectAttributes.addFlashAttribute("errorMessage",
                String.format("File size (%.2f MB) exceeds the maximum limit of 200 MB.", fileSize / (1024.0 * 1024.0)));
            return "redirect:/split";
        }

        String originalFileName = pdfFile.getOriginalFilename();
        String baseName = originalFileName != null ? originalFileName.substring(0, originalFileName.lastIndexOf('.')) : "split_pdf";
        baseName = baseName.replaceAll("[^a-zA-Z0-9.\\-_]", "_");


        try {
            byte[] resultBytes;
            String downloadFilename;

            if ("every_page".equals(splitOption)) {
                resultBytes = pdfSplitService.splitPdfEveryPage(pdfFile, baseName);
                downloadFilename = baseName + "_pages.zip";
                logger.info("PDF {} split into individual pages, packaged as {}", originalFileName, downloadFilename);
            } 
            // else if ("custom_range".equals(splitOption)) {
            //     // Implement custom range splitting here if needed
            //     redirectAttributes.addFlashAttribute("errorMessage", "Custom range splitting is not yet implemented.");
            //     return "redirect:/split";
            // } 
            else {
                redirectAttributes.addFlashAttribute("errorMessage", "Invalid split option selected.");
                return "redirect:/split";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM); // For ZIP file
            headers.setContentDisposition(ContentDisposition.attachment().filename(downloadFilename).build());
            headers.setContentLength(resultBytes.length);

            return new ResponseEntity<>(resultBytes, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid input for PDF split: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/split";
        } catch (IOException e) {
            logger.error("Error splitting PDF file {}: {}", originalFileName, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An error occurred while splitting the PDF. Ensure it's a valid, non-encrypted PDF.");
            return "redirect:/split";
        } catch (Exception e) {
            logger.error("Unexpected error during PDF split for {}: {}", originalFileName, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred. Please try again.");
            return "redirect:/split";
        }
    }
}