package com.pdf.marsk.pdfdemo.controller;

import com.pdf.marsk.pdfdemo.service.PdfWatermarkService;
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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
@RequestMapping("/watermark")
public class PdfWatermarkController {

    private static final Logger logger = LoggerFactory.getLogger(PdfWatermarkController.class);
    private final PdfWatermarkService pdfWatermarkService;

    @Autowired
    public PdfWatermarkController(PdfWatermarkService pdfWatermarkService) {
        this.pdfWatermarkService = pdfWatermarkService;
    }

    @GetMapping
    public String watermarkPage(Model model) {
        return "watermark";
    }

    @PostMapping
    public Object handlePdfWatermarking(@RequestParam("pdfFile") MultipartFile pdfFile,
                                      @RequestParam("watermarkText") String watermarkText,
                                      @RequestParam(name = "opacity", defaultValue = "0.5") float opacity,
                                      @RequestParam(name = "position", defaultValue = "center") String position,
                                      RedirectAttributes redirectAttributes) throws IOException {

        if (pdfFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a PDF file to watermark.");
            return "redirect:/watermark";
        }

        if (!StringUtils.hasText(watermarkText)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please provide watermark text.");
            return "redirect:/watermark";
        }

        long fileSize = pdfFile.getSize();
        long maxFileSize = 200 * 1024 * 1024; // 200 MB
        if (fileSize > maxFileSize) {
            redirectAttributes.addFlashAttribute("errorMessage",
                String.format("File size (%.2f MB) exceeds the maximum limit of 200 MB.", fileSize / (1024.0 * 1024.0)));
            return "redirect:/watermark";
        }

        String originalFileName = pdfFile.getOriginalFilename();
        String watermarkedFileName = "watermarked_" + (originalFileName != null ? originalFileName : "document.pdf");
        watermarkedFileName = watermarkedFileName.replaceAll("[^a-zA-Z0-9.\\-_]", "_");

        try {
            byte[] watermarkedPdfBytes = pdfWatermarkService.addWatermark(pdfFile, watermarkText, opacity, position);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment().filename(watermarkedFileName).build());
            headers.setContentLength(watermarkedPdfBytes.length);

            logger.info("PDF {} watermarked successfully. Offering download as '{}'", originalFileName, watermarkedFileName);
            redirectAttributes.addFlashAttribute("successMessage", "PDF watermarked successfully! Download has started.");

            return new ResponseEntity<>(watermarkedPdfBytes, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid input for PDF watermarking: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/watermark";
        } catch (Exception e) {
            logger.error("Unexpected error during PDF watermarking for {}: {}", originalFileName, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred. Please try again.");
            return "redirect:/watermark";
        }
    }
}