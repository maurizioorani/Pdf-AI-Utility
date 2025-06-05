package com.pdf.marsk.pdfdemo.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

@Service
public class PdfCompressionService {
 
    private static final Logger logger = LoggerFactory.getLogger(PdfCompressionService.class);
 
    // Enhanced compression constants
    private static final int MAX_WIDTH = 800;  // Reduced from 1000 for better compression
    private static final int MAX_HEIGHT = 800;
    private static final float JPEG_COMPRESSION_QUALITY = 0.7f; // 70% quality for better compression
    
    public byte[] compressPdf(MultipartFile pdfFile, boolean attemptImageCompression) throws IOException {
        if (pdfFile == null || pdfFile.isEmpty()) {
            throw new IllegalArgumentException("A PDF file is required for compression.");
        }
        if (!"application/pdf".equalsIgnoreCase(pdfFile.getContentType())) {
            logger.warn("Invalid file type for compression: {} (type: {})", pdfFile.getOriginalFilename(), pdfFile.getContentType());
            throw new IllegalArgumentException("Invalid file type provided: " + pdfFile.getOriginalFilename() + ". Only PDF files are allowed.");
        }

        try (InputStream inputStream = pdfFile.getInputStream();
             PDDocument document = PDDocument.load(inputStream);
             ByteArrayOutputStream compressedPdfOutputStream = new ByteArrayOutputStream()) {

            if (document.isEncrypted()) {
                logger.warn("Cannot compress an encrypted PDF: {}", pdfFile.getOriginalFilename());
                throw new IOException("Encrypted PDFs cannot be compressed with this method.");
            }
            
            logger.info("Starting comprehensive compression for PDF: {}", pdfFile.getOriginalFilename());
            
            // Apply various compression techniques
            if (attemptImageCompression) {
                logger.info("Applying aggressive image compression");
                compressImagesInDocument(document);
            }
            
            // Remove metadata and structure information to reduce size
            optimizeDocumentStructure(document);
            
            // Save with compression options
            document.save(compressedPdfOutputStream);
            
            byte[] result = compressedPdfOutputStream.toByteArray();
            logger.info("PDF compression completed for: {}. Image compression: {}. Final size: {} bytes", 
                       pdfFile.getOriginalFilename(), attemptImageCompression, result.length);
            return result;

        } catch (IOException e) {
            logger.error("Error during PDF processing for {}: {}", pdfFile.getOriginalFilename(), e.getMessage(), e);
            throw new IOException("Error processing PDF file: " + pdfFile.getOriginalFilename(), e);
        }
    }
    
    private void optimizeDocumentStructure(PDDocument document) {
        try {
            // Remove metadata to reduce file size
            if (document.getDocumentInformation() != null) {
                document.getDocumentInformation().setAuthor(null);
                document.getDocumentInformation().setCreator(null);
                document.getDocumentInformation().setProducer(null);
                document.getDocumentInformation().setSubject(null);
                document.getDocumentInformation().setTitle(null);
                document.getDocumentInformation().setKeywords(null);
                logger.debug("Removed document metadata for size optimization");
            }
            
            // Remove structure tree if present (accessibility info that can be large)
            if (document.getDocumentCatalog().getStructureTreeRoot() != null) {
                document.getDocumentCatalog().setStructureTreeRoot(null);
                logger.debug("Removed structure tree for size optimization");
            }
            
        } catch (Exception e) {
            logger.warn("Failed to optimize document structure: {}", e.getMessage());
        }
    }
    
    private void compressImagesInDocument(PDDocument document) throws IOException {
        for (PDPage page : document.getPages()) {
            PDResources resources = page.getResources();
            if (resources == null) continue;

            for (COSName cosName : resources.getXObjectNames()) {
                PDXObject xobject = resources.getXObject(cosName);

                if (xobject instanceof PDImageXObject) {
                    PDImageXObject imageXObject = (PDImageXObject) xobject;
                    processImage(document, resources, cosName, imageXObject);
                }
            }
        }
    }
    
    private void processImage(PDDocument document, PDResources resources, COSName cosName, PDImageXObject imageXObject) throws IOException {
        try {
            BufferedImage originalImage = imageXObject.getImage();
            if (originalImage == null) {
                logger.warn("Could not get BufferedImage for image {} on page.", cosName.getName());
                return;
            }

            BufferedImage imageToProcess = originalImage;
            boolean wasResized = false;

            // More aggressive resizing - resize images that are larger than our limits
            if (originalImage.getWidth() > MAX_WIDTH || originalImage.getHeight() > MAX_HEIGHT) {
                imageToProcess = resizeImage(originalImage);
                wasResized = true;
                logger.info("Resized image {} from {}x{} to {}x{}", cosName.getName(),
                           originalImage.getWidth(), originalImage.getHeight(), 
                           imageToProcess.getWidth(), imageToProcess.getHeight());
            }

            // Always try to compress to JPEG with quality settings for better compression
            byte[] compressedImageData = compressToJpeg(imageToProcess);
            
            if (compressedImageData == null) {
                logger.warn("Could not compress image {} to JPEG.", cosName.getName());
                return;
            }

            // More aggressive replacement - replace if compressed version is significantly smaller
            // or if we resized the image
            long originalSize = imageXObject.getStream().getLength();
            boolean shouldReplace = wasResized || 
                                  compressedImageData.length < originalSize || 
                                  compressedImageData.length < (originalSize * 0.9); // Replace if at least 10% smaller

            if (shouldReplace) {
                PDImageXObject newImageXObject = PDImageXObject.createFromByteArray(document, compressedImageData, cosName.getName());
                resources.put(cosName, newImageXObject);
                logger.info("Replaced image {}. Original size: {}, New size: {}, Reduction: {:.1f}%",
                           cosName.getName(), originalSize, compressedImageData.length,
                           ((double)(originalSize - compressedImageData.length) / originalSize) * 100);
            } else {
                logger.debug("Skipping replacement for image {} - no significant improvement. Original: {}, Compressed: {}",
                           cosName.getName(), originalSize, compressedImageData.length);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to process image {}: {}", cosName.getName(), e.getMessage());
        }
    }
    
    private BufferedImage resizeImage(BufferedImage originalImage) {
        int newWidth = originalImage.getWidth();
        int newHeight = originalImage.getHeight();

        // Calculate new dimensions maintaining aspect ratio
        double aspectRatio = (double) originalImage.getWidth() / originalImage.getHeight();
        
        if (originalImage.getWidth() > originalImage.getHeight()) {
            newWidth = MAX_WIDTH;
            newHeight = (int) (MAX_WIDTH / aspectRatio);
        } else {
            newHeight = MAX_HEIGHT;
            newWidth = (int) (MAX_HEIGHT * aspectRatio);
        }
        
        // Ensure new dimensions are at least 1x1
        newWidth = Math.max(1, newWidth);
        newHeight = Math.max(1, newHeight);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        
        // Use high quality rendering hints for better image quality
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g.dispose();
        
        return resized;
    }
    
    private byte[] compressToJpeg(BufferedImage image) throws IOException {
        // Convert to RGB for JPEG compression
        BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D gRgb = rgbImage.createGraphics();
        gRgb.drawImage(image, 0, 0, null);
        gRgb.dispose();

        ByteArrayOutputStream compressedImageStream = new ByteArrayOutputStream();
        
        // Use specific JPEG compression quality for better file size reduction
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writers available");
        }
        
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_COMPRESSION_QUALITY);
        }
        
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(compressedImageStream)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(rgbImage, null, null), param);
        } finally {
            writer.dispose();
        }
        
        return compressedImageStream.toByteArray();
    }
}