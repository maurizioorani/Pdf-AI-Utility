package com.pdf.marsk.pdfdemo.controller;

import com.pdf.marsk.pdfdemo.service.PdfCompressionService;
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
@RequestMapping("/compress")
public class PdfCompressController {

    private static final Logger logger = LoggerFactory.getLogger(PdfCompressController.class);
    private final PdfCompressionService pdfCompressionService;

    @Autowired
    public PdfCompressController(PdfCompressionService pdfCompressionService) {
        this.pdfCompressionService = pdfCompressionService;
    }

    @GetMapping
    public String compressPage(Model model) {
        return "compress";
    }

    @PostMapping
    public Object handlePdfCompression(@RequestParam("pdfFile") MultipartFile pdfFile,
                                       @RequestParam(name = "compressImages", required = false) boolean compressImages,
                                       @RequestParam(name = "outputFilename", required = false) String outputFilename,
                                       RedirectAttributes redirectAttributes) {
        if (pdfFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a PDF file to compress.");
            return "redirect:/compress";
        }

        long fileSize = pdfFile.getSize();
        long maxFileSize = 200 * 1024 * 1024; // 200 MB in bytes

        if (fileSize > maxFileSize) {
            redirectAttributes.addFlashAttribute("errorMessage",
                String.format("File size (%.2f MB) exceeds the maximum limit of 200 MB.", fileSize / (1024.0 * 1024.0)));
            return "redirect:/compress";
        }

        String originalFileName = pdfFile.getOriginalFilename();
        String finalOutputFilename = outputFilename;

        if (finalOutputFilename == null || finalOutputFilename.trim().isEmpty()) {
            if (originalFileName != null && !originalFileName.isEmpty()) {
                finalOutputFilename = "compressed_" + originalFileName;
            } else {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                finalOutputFilename = "compressed_" + timestamp + ".pdf";
            }
        }
        
        if (!finalOutputFilename.toLowerCase().endsWith(".pdf")) {
            finalOutputFilename += ".pdf";
        }
        // Sanitize filename
        finalOutputFilename = finalOutputFilename.replaceAll("[^a-zA-Z0-9.\\-_]", "_");

        try {
            long originalSize = pdfFile.getSize();
            logger.info("Attempting to compress PDF: {}, compressImages: {}", originalFileName, compressImages);
            byte[] compressedPdfBytes = pdfCompressionService.compressPdf(pdfFile, compressImages);
            long compressedSize = compressedPdfBytes.length;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment().filename(finalOutputFilename).build());
            headers.setContentLength(compressedPdfBytes.length);

            double reductionPercentage = 0;
            if (originalSize > 0) {
                reductionPercentage = ((double) (originalSize - compressedSize) / originalSize) * 100;
            }
            
            String successMessage = String.format(
                "Successfully processed PDF. Original size: %.2f KB, Compressed size: %.2f KB. Reduction: %.2f%%. Downloading as '%s'",
                originalSize / 1024.0, compressedSize / 1024.0, reductionPercentage, finalOutputFilename
            );
            logger.info(successMessage);
            // It's better to show this message on the page after download, but that requires a different flow.
            // For direct download, the message is mostly for logging.
            // If we wanted to show it on the page, we'd redirect with flash attributes and not serve the file directly.

            return new ResponseEntity<>(compressedPdfBytes, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid input for PDF compression: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/compress";
        } catch (IOException e) {
            logger.error("Error compressing PDF file: {}", originalFileName, e);
            redirectAttributes.addFlashAttribute("errorMessage", "An error occurred while compressing the PDF file. Ensure it is a valid, non-encrypted PDF.");
            return "redirect:/compress";
        } catch (Exception e) {
            logger.error("Unexpected error during PDF compression: {}", originalFileName, e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred. Please try again.");
            return "redirect:/compress";
        }
    }
}