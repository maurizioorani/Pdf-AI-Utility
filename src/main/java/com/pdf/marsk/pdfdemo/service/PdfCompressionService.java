package com.pdf.marsk.pdfdemo.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D; // Added for resizing
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import org.apache.pdfbox.cos.COSName; // Added from example for consistency
import org.apache.pdfbox.pdmodel.PDResources; // Added from example
import org.apache.pdfbox.pdmodel.graphics.PDXObject; // Added from example
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject; // Added from example, though we won't use its specific compression

@Service
public class PdfCompressionService {
 
    private static final Logger logger = LoggerFactory.getLogger(PdfCompressionService.class);
 
    // Constants for maximum image dimensions from the example
    private static final int MAX_WIDTH = 1000;
    private static final int MAX_HEIGHT = 1000;
    // Your JPEG quality can be kept or adjusted if needed. The example didn't specify ImageWriteParam for quality.
    // private static final float JPEG_COMPRESSION_QUALITY = 0.75f; // Example: 75% quality. Let's rely on default ImageIO JPEG quality for now.
 
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
            
            // Attempt to enable object stream compression if not already used.
            // This can reduce size for some documents.
            // Note: PDFBox 2.x enables this by default when saving if possible.
            // For more control, one might need to delve into COSWriter settings.

            // A simple re-save can sometimes optimize and compress.
            // For more aggressive compression, especially of images, more complex logic is needed.
            // This basic approach mainly relies on PDFBox's default save optimizations.

            if (attemptImageCompression) {
                logger.info("Attempting image compression for PDF: {}", pdfFile.getOriginalFilename());
                compressImagesInDocument(document);
            }
            
            document.save(compressedPdfOutputStream);
            logger.info("PDF processed for potential compression: {}. Image compression attempted: {}", pdfFile.getOriginalFilename(), attemptImageCompression);
            return compressedPdfOutputStream.toByteArray();

        } catch (IOException e) {
            logger.error("Error during PDF processing for {}: {}", pdfFile.getOriginalFilename(), e.getMessage(), e);
            throw new IOException("Error processing PDF file: " + pdfFile.getOriginalFilename(), e);
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
                    
                    // Your existing skip logic is good, let's refine slightly
                    // if ("jpg".equalsIgnoreCase(imageXObject.getSuffix()) || "jpeg".equalsIgnoreCase(imageXObject.getSuffix())) {
                    //     logger.debug("Skipping image {} as it's already JPEG.", cosName.getName());
                    //     continue;
                    // }

                    BufferedImage originalImage = imageXObject.getImage();
                    if (originalImage == null) {
                        logger.warn("Could not get BufferedImage for image {} on page.", cosName.getName());
                        continue;
                    }

                    BufferedImage imageToProcess = originalImage;

                    // Resize logic from example
                    if (originalImage.getWidth() > MAX_WIDTH || originalImage.getHeight() > MAX_HEIGHT) {
                        int newWidth = originalImage.getWidth();
                        int newHeight = originalImage.getHeight();

                        if (originalImage.getWidth() > originalImage.getHeight()) {
                            newWidth = MAX_WIDTH;
                            newHeight = (int) ((double) MAX_WIDTH * originalImage.getHeight() / originalImage.getWidth());
                        } else {
                            newHeight = MAX_HEIGHT;
                            newWidth = (int) ((double) MAX_HEIGHT * originalImage.getWidth() / originalImage.getHeight());
                        }
                        
                        // Ensure new dimensions are at least 1x1
                        newWidth = Math.max(1, newWidth);
                        newHeight = Math.max(1, newHeight);

                        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = resized.createGraphics();
                        g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
                        g.dispose();
                        imageToProcess = resized;
                        logger.info("Resized image {} from {}x{} to {}x{}", cosName.getName(),
                                    originalImage.getWidth(), originalImage.getHeight(), newWidth, newHeight);
                    }

                    // Convert to RGB for JPEG compression (if not already or if resized)
                    BufferedImage rgbImage = new BufferedImage(imageToProcess.getWidth(), imageToProcess.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D gRgb = rgbImage.createGraphics();
                    gRgb.drawImage(imageToProcess, 0, 0, null); // Using null for ImageObserver
                    gRgb.dispose();

                    ByteArrayOutputStream compressedImageStream = new ByteArrayOutputStream();
                    // Using default JPEG quality. For specific quality:
                    // ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
                    // ImageWriteParam param = writer.getDefaultWriteParam();
                    // param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    // param.setCompressionQuality(JPEG_COMPRESSION_QUALITY); // Your 0.75f
                    // writer.setOutput(ImageIO.createImageOutputStream(compressedImageStream));
                    // writer.write(null, new IIOImage(rgbImage, null, null), param);
                    // writer.dispose();
                    boolean written = ImageIO.write(rgbImage, "jpeg", compressedImageStream);
                    
                    if (!written) {
                        logger.warn("Could not write image {} as JPEG.", cosName.getName());
                        continue;
                    }

                    byte[] compressedImageData = compressedImageStream.toByteArray();
                    
                    // Only replace if the new image is smaller (or if it wasn't a JPEG before and now is)
                    boolean wasNotJpeg = !("jpg".equalsIgnoreCase(imageXObject.getSuffix()) || "jpeg".equalsIgnoreCase(imageXObject.getSuffix()));
                    if (wasNotJpeg || compressedImageData.length < imageXObject.getStream().getLength()) {
                        PDImageXObject newImageXObject = PDImageXObject.createFromByteArray(document, compressedImageData, cosName.getName());
                        resources.put(cosName, newImageXObject);
                        logger.info("Processed and replaced image {} on page. Original size: {}, New size: {}. Was not JPEG: {}",
                                    cosName.getName(), imageXObject.getStream().getLength(), compressedImageData.length, wasNotJpeg);
                    } else {
                        logger.info("Skipping replacement for image {} as compressed JPEG version is not smaller. Original: {}, Compressed: {}",
                                    cosName.getName(), imageXObject.getStream().getLength(), compressedImageData.length);
                    }
                }
                // We are not processing PDFormXObject with custom text compression from the example.
            }
        }
    }
}