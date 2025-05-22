package com.pdf.marsk.pdfdemo.controller;

import com.pdf.marsk.pdfdemo.service.PdfProtectionService;
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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
@RequestMapping("/protect")
public class PdfProtectController {

    private static final Logger logger = LoggerFactory.getLogger(PdfProtectController.class);
    private final PdfProtectionService pdfProtectionService;

    @Autowired
    public PdfProtectController(PdfProtectionService pdfProtectionService) {
        this.pdfProtectionService = pdfProtectionService;
    }

    @GetMapping
    public String protectPage(Model model) {
        return "protect";
    }

    @PostMapping
    public Object handlePdfProtection(@RequestParam("pdfFile") MultipartFile pdfFile,
                                      @RequestParam(name = "userPassword", required = false) String userPassword,
                                      @RequestParam(name = "ownerPassword", required = false) String ownerPassword,
                                      RedirectAttributes redirectAttributes) {

        if (pdfFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a PDF file to protect.");
            return "redirect:/protect";
        }

        if (!StringUtils.hasText(userPassword) && !StringUtils.hasText(ownerPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please provide at least a user password or an owner password.");
            return "redirect:/protect";
        }
        
        // PDFBox requires an owner password for any protection.
        // If user provides a userPassword but no ownerPassword, we can use userPassword as ownerPassword.
        String effectiveOwnerPassword = ownerPassword;
        if (!StringUtils.hasText(ownerPassword) && StringUtils.hasText(userPassword)) {
            effectiveOwnerPassword = userPassword;
        }
         if (!StringUtils.hasText(effectiveOwnerPassword)) { // Should not happen if above logic is correct and one pass is given
            redirectAttributes.addFlashAttribute("errorMessage", "An owner password is required to apply protection.");
            return "redirect:/protect";
        }


        long fileSize = pdfFile.getSize();
        long maxFileSize = 200 * 1024 * 1024; // 200 MB
        if (fileSize > maxFileSize) {
            redirectAttributes.addFlashAttribute("errorMessage",
                String.format("File size (%.2f MB) exceeds the maximum limit of 200 MB.", fileSize / (1024.0 * 1024.0)));
            return "redirect:/protect";
        }

        String originalFileName = pdfFile.getOriginalFilename();
        String protectedFileName = "protected_" + (originalFileName != null ? originalFileName : "document.pdf");
        protectedFileName = protectedFileName.replaceAll("[^a-zA-Z0-9.\\-_]", "_");

        try {
            byte[] protectedPdfBytes = pdfProtectionService.protectPdf(pdfFile, userPassword, effectiveOwnerPassword);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment().filename(protectedFileName).build());
            headers.setContentLength(protectedPdfBytes.length);

            logger.info("PDF {} protected successfully. Offering download as '{}'", originalFileName, protectedFileName);
            redirectAttributes.addFlashAttribute("successMessage", "PDF protected successfully! Download has started.");
            // For direct download, success message via flash won't show on same page.
            // If we want to show it, we'd redirect to /protect and have a download link.
            // For now, direct download is simpler.

            return new ResponseEntity<>(protectedPdfBytes, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid input for PDF protection: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/protect";
        } catch (IOException e) {
            logger.error("Error protecting PDF file {}: {}", originalFileName, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An error occurred while protecting the PDF. Ensure it's a valid PDF.");
            return "redirect:/protect";
        } catch (Exception e) {
            logger.error("Unexpected error during PDF protection for {}: {}", originalFileName, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred. Please try again.");
            return "redirect:/protect";
        }
    }
}