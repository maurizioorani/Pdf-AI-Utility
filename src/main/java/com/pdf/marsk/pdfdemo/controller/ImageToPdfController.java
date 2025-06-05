package com.pdf.marsk.pdfdemo.controller;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;

@Controller
public class ImageToPdfController {

    private static final Logger logger = LoggerFactory.getLogger(ImageToPdfController.class);
    
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
        "image/jpeg", "image/jpg", "image/png", "image/tiff", "image/bmp", "image/gif"
    );
    
    // Maximum dimensions for images to prevent memory issues and white PDFs
    private static final int MAX_IMAGE_WIDTH = 2000;
    private static final int MAX_IMAGE_HEIGHT = 2000;
    
    // Maximum file size in bytes (50MB)
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    
    // JPEG compression quality for optimization
    private static final float JPEG_QUALITY = 0.85f;

    @GetMapping("/images-to-pdf")
    public String showImageUploadPage(Model model) {
        model.addAttribute("title", "Generate PDF from Images");
        return "images-to-pdf";
    }

    @PostMapping("/images-to-pdf/convert")
    public void convertImagesToPdf(
            @RequestParam("images") MultipartFile[] images,
            @RequestParam(value = "pageSize", defaultValue = "A4") String pageSize,
            @RequestParam(value = "orientation", defaultValue = "portrait") String orientation,
            @RequestParam(value = "fitToPage", defaultValue = "true") boolean fitToPage,
            HttpServletResponse response) throws IOException {

        if (images == null || images.length == 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No images provided");
            return;
        }

        // Validate file types and sizes
        for (MultipartFile image : images) {
            if (!ALLOWED_IMAGE_TYPES.contains(image.getContentType())) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, 
                    "Invalid file type: " + image.getContentType() + ". Allowed types: JPG, PNG, TIFF, BMP, GIF");
                return;
            }
            
            // Check file size
            if (image.getSize() > MAX_FILE_SIZE) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, 
                    "File " + image.getOriginalFilename() + " is too large. Maximum size is " + 
                    (MAX_FILE_SIZE / (1024 * 1024)) + "MB");
                return;
            }
        }

        try (PDDocument document = new PDDocument()) {
            PDRectangle pageRectangle = getPageRectangle(pageSize, orientation);

            for (MultipartFile imageFile : images) {
                if (!imageFile.isEmpty()) {
                    logger.info("Processing image: {} (size: {} bytes)", 
                               imageFile.getOriginalFilename(), imageFile.getSize());
                    addImageToDocument(document, imageFile, pageRectangle, fitToPage);
                }
            }

            // Set response headers
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"images-to-pdf.pdf\"");

            // Write PDF to response
            document.save(response.getOutputStream());
            logger.info("Successfully generated PDF from {} images", images.length);
        } catch (Exception e) {
            logger.error("Error generating PDF from images: {}", e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                "Error generating PDF: " + e.getMessage());
        }
    }

    private PDRectangle getPageRectangle(String pageSize, String orientation) {
        PDRectangle rectangle;
        
        switch (pageSize.toUpperCase()) {
            case "A3":
                rectangle = PDRectangle.A3;
                break;
            case "A5":
                rectangle = PDRectangle.A5;
                break;
            case "LETTER":
                rectangle = PDRectangle.LETTER;
                break;
            case "LEGAL":
                rectangle = PDRectangle.LEGAL;
                break;
            default:
                rectangle = PDRectangle.A4;
                break;
        }

        // Rotate for landscape orientation
        if ("landscape".equalsIgnoreCase(orientation)) {
            rectangle = new PDRectangle(rectangle.getHeight(), rectangle.getWidth());
        }

        return rectangle;
    }

    private void addImageToDocument(PDDocument document, MultipartFile imageFile, 
                                  PDRectangle pageRectangle, boolean fitToPage) throws IOException {
        
        // Read image
        BufferedImage originalImage = ImageIO.read(imageFile.getInputStream());
        if (originalImage == null) {
            throw new IOException("Could not read image: " + imageFile.getOriginalFilename());
        }

        logger.info("Original image dimensions: {}x{} for {}", 
                   originalImage.getWidth(), originalImage.getHeight(), imageFile.getOriginalFilename());

        // Optimize image if it's too large
        BufferedImage processedImage = optimizeImageForPdf(originalImage, imageFile.getOriginalFilename());
        
        // Convert optimized image to byte array for PDFBox
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String formatName = "jpg"; // Always use JPEG for optimized output
        
        // Use high-quality JPEG compression
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(formatName);
        if (writers.hasNext()) {
            ImageWriter writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new javax.imageio.IIOImage(processedImage, null, null), param);
            } finally {
                writer.dispose();
            }
        } else {
            // Fallback to default ImageIO
            ImageIO.write(processedImage, formatName, baos);
        }
        
        logger.info("Processed image size: {} bytes for {}", baos.size(), imageFile.getOriginalFilename());
        
        PDImageXObject pdImage = PDImageXObject.createFromByteArray(
            document, baos.toByteArray(), imageFile.getOriginalFilename());

        // Create new page
        PDPage page = new PDPage(pageRectangle);
        document.addPage(page);

        // Calculate image dimensions
        float pageWidth = pageRectangle.getWidth();
        float pageHeight = pageRectangle.getHeight();
        float imageWidth = pdImage.getWidth();
        float imageHeight = pdImage.getHeight();

        float x = 0;
        float y = 0;
        float scaledWidth = imageWidth;
        float scaledHeight = imageHeight;

        if (fitToPage) {
            // Calculate scaling factor to fit image within page while maintaining aspect ratio
            float scaleX = pageWidth / imageWidth;
            float scaleY = pageHeight / imageHeight;
            float scale = Math.min(scaleX, scaleY);

            scaledWidth = imageWidth * scale;
            scaledHeight = imageHeight * scale;

            // Center the image on the page
            x = (pageWidth - scaledWidth) / 2;
            y = (pageHeight - scaledHeight) / 2;
        }

        // Draw image on page
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.drawImage(pdImage, x, y, scaledWidth, scaledHeight);
        }
        
        logger.info("Successfully added image {} to PDF at position ({}, {}) with size {}x{}", 
                   imageFile.getOriginalFilename(), x, y, scaledWidth, scaledHeight);
    }

    /**
     * Optimizes large images by resizing them to prevent memory issues and white PDFs
     */
    private BufferedImage optimizeImageForPdf(BufferedImage originalImage, String filename) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        
        // Check if image needs resizing
        if (originalWidth <= MAX_IMAGE_WIDTH && originalHeight <= MAX_IMAGE_HEIGHT) {
            logger.info("Image {} is within size limits, no resizing needed", filename);
            return originalImage;
        }
        
        // Calculate new dimensions while maintaining aspect ratio
        double aspectRatio = (double) originalWidth / originalHeight;
        int newWidth, newHeight;
        
        if (originalWidth > originalHeight) {
            // Landscape or square image
            newWidth = Math.min(originalWidth, MAX_IMAGE_WIDTH);
            newHeight = (int) (newWidth / aspectRatio);
            
            if (newHeight > MAX_IMAGE_HEIGHT) {
                newHeight = MAX_IMAGE_HEIGHT;
                newWidth = (int) (newHeight * aspectRatio);
            }
        } else {
            // Portrait image
            newHeight = Math.min(originalHeight, MAX_IMAGE_HEIGHT);
            newWidth = (int) (newHeight * aspectRatio);
            
            if (newWidth > MAX_IMAGE_WIDTH) {
                newWidth = MAX_IMAGE_WIDTH;
                newHeight = (int) (newWidth / aspectRatio);
            }
        }
        
        // Ensure dimensions are at least 1x1
        newWidth = Math.max(1, newWidth);
        newHeight = Math.max(1, newHeight);
        
        logger.info("Resizing image {} from {}x{} to {}x{}", 
                   filename, originalWidth, originalHeight, newWidth, newHeight);
        
        // Create resized image with high quality
        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();
        
        // Enable high-quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Fill with white background (important for transparent images)
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, newWidth, newHeight);
        
        // Draw the resized image
        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();
        
        return resizedImage;
    }

}
