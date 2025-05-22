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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

@Service
public class PdfCompressionService {

    private static final Logger logger = LoggerFactory.getLogger(PdfCompressionService.class);

    // Define a quality setting for JPEG compression (0.0f to 1.0f)
    private static final float JPEG_COMPRESSION_QUALITY = 0.75f; // Example: 75% quality

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
            if (page.getResources() == null) continue;
            Iterator<org.apache.pdfbox.cos.COSName> xObjectNames = page.getResources().getXObjectNames().iterator();
            while (xObjectNames.hasNext()) {
                org.apache.pdfbox.cos.COSName cosName = xObjectNames.next();
                if (page.getResources().isImageXObject(cosName)) {
                    PDImageXObject imageXObject = (PDImageXObject) page.getResources().getXObject(cosName);
                    
                    // Skip if already JPEG or if it's a small image (heuristic)
                    if ("jpg".equalsIgnoreCase(imageXObject.getSuffix()) || "jpeg".equalsIgnoreCase(imageXObject.getSuffix()) ||
                        (imageXObject.getWidth() * imageXObject.getHeight() < 100*100) ) { // Skip small images
                        // Potentially re-compress existing JPEGs if desired, but that's more complex.
                        // For now, we only convert non-JPEGs or large images.
                        // logger.debug("Skipping image {} as it's already JPEG or too small.", cosName.getName());
                        continue;
                    }

                    BufferedImage bufferedImage = imageXObject.getImage();
                    if (bufferedImage == null) {
                        logger.warn("Could not get BufferedImage for image {} on page.", cosName.getName());
                        continue;
                    }

                    // Create a new BufferedImage with RGB type for JPEG compatibility
                    BufferedImage rgbImage = new BufferedImage(bufferedImage.getWidth(), bufferedImage.getHeight(), BufferedImage.TYPE_INT_RGB);
                    rgbImage.createGraphics().drawImage(bufferedImage, 0, 0, java.awt.Color.WHITE, null);


                    ByteArrayOutputStream compressedImageStream = new ByteArrayOutputStream();
                    boolean written = ImageIO.write(rgbImage, "jpeg", compressedImageStream);
                    
                    if (!written) {
                        logger.warn("Could not write image {} as JPEG.", cosName.getName());
                        continue;
                    }

                    byte[] compressedImageData = compressedImageStream.toByteArray();
                    
                    // Only replace if the new image is smaller
                    if (compressedImageData.length < imageXObject.getStream().getLength()) {
                        PDImageXObject newImageXObject = PDImageXObject.createFromByteArray(document, compressedImageData, cosName.getName());
                        page.getResources().put(cosName, newImageXObject);
                        logger.info("Compressed and replaced image {} on page. Original size: {}, New size: {}",
                                    cosName.getName(), imageXObject.getStream().getLength(), compressedImageData.length);
                    } else {
                        logger.info("Skipping replacement for image {} as compressed version is not smaller. Original: {}, Compressed: {}",
                                    cosName.getName(), imageXObject.getStream().getLength(), compressedImageData.length);
                    }
                }
            }
        }
    }
}