package com.pdf.marsk.pdfdemo.standalone;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Standalone test for PDF compression functionality
 */
public class CompressionTest {
    
    private static final int MAX_WIDTH = 800;
    private static final int MAX_HEIGHT = 800;
    private static final float JPEG_COMPRESSION_QUALITY = 0.7f;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java CompressionTest <pdf-file-path>");
            System.out.println("Example: java CompressionTest test_files/english_test_new.pdf");
            return;
        }
        
        String inputFile = args[0];
        String outputFile = inputFile.replace(".pdf", "_compressed.pdf");
        
        try {
            System.out.println("Testing PDF compression...");
            System.out.println("Input file: " + inputFile);
            
            // Load the PDF
            PDDocument document = PDDocument.load(new FileInputStream(inputFile));
            
            // Get original file size
            long originalSize = new java.io.File(inputFile).length();
            System.out.println("Original size: " + originalSize + " bytes (" + (originalSize / 1024.0) + " KB)");
            
            // Apply compression
            System.out.println("Applying compression...");
            compressImagesInDocument(document);
            optimizeDocumentStructure(document);
            
            // Save compressed PDF
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            document.close();
            
            byte[] compressedData = baos.toByteArray();
            long compressedSize = compressedData.length;
            
            // Write to file
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(compressedData);
            }
            
            // Calculate compression ratio
            double reductionPercentage = ((double) (originalSize - compressedSize) / originalSize) * 100;
            
            System.out.println("Compressed size: " + compressedSize + " bytes (" + (compressedSize / 1024.0) + " KB)");
            System.out.println("Size reduction: " + String.format("%.2f", reductionPercentage) + "%");
            System.out.println("Output file: " + outputFile);
            
            if (compressedSize < originalSize) {
                System.out.println("✓ Compression successful!");
            } else if (compressedSize == originalSize) {
                System.out.println("⚠ No size reduction achieved - file may already be optimized");
            } else {
                System.out.println("⚠ File size increased - this can happen with already optimized PDFs");
            }
            
        } catch (Exception e) {
            System.err.println("Error during compression: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void compressImagesInDocument(PDDocument document) throws IOException {
        System.out.println("Compressing images...");
        int imageCount = 0;
        int compressedCount = 0;
        
        for (PDPage page : document.getPages()) {
            PDResources resources = page.getResources();
            if (resources == null) continue;

            for (COSName cosName : resources.getXObjectNames()) {
                PDXObject xobject = resources.getXObject(cosName);

                if (xobject instanceof PDImageXObject) {
                    imageCount++;
                    PDImageXObject imageXObject = (PDImageXObject) xobject;
                    if (processImage(document, resources, cosName, imageXObject)) {
                        compressedCount++;
                    }
                }
            }
        }
        
        System.out.println("Found " + imageCount + " images, compressed " + compressedCount);
    }
    
    private static boolean processImage(PDDocument document, PDResources resources, COSName cosName, PDImageXObject imageXObject) throws IOException {
        try {
            BufferedImage originalImage = imageXObject.getImage();
            if (originalImage == null) {
                System.out.println("Could not get BufferedImage for image " + cosName.getName());
                return false;
            }

            BufferedImage imageToProcess = originalImage;
            boolean wasResized = false;

            // Resize if too large
            if (originalImage.getWidth() > MAX_WIDTH || originalImage.getHeight() > MAX_HEIGHT) {
                imageToProcess = resizeImage(originalImage);
                wasResized = true;
                System.out.println("Resized image " + cosName.getName() + " from " + 
                                   originalImage.getWidth() + "x" + originalImage.getHeight() + " to " + 
                                   imageToProcess.getWidth() + "x" + imageToProcess.getHeight());
            }

            // Compress to JPEG
            byte[] compressedImageData = compressToJpeg(imageToProcess);
            
            if (compressedImageData == null) {
                System.out.println("Could not compress image " + cosName.getName() + " to JPEG");
                return false;
            }

            long originalSize = imageXObject.getStream().getLength();
            boolean shouldReplace = wasResized || 
                                  compressedImageData.length < originalSize || 
                                  compressedImageData.length < (originalSize * 0.9);

            if (shouldReplace) {
                PDImageXObject newImageXObject = PDImageXObject.createFromByteArray(document, compressedImageData, cosName.getName());
                resources.put(cosName, newImageXObject);
                
                double reduction = ((double)(originalSize - compressedImageData.length) / originalSize) * 100;
                System.out.println("Replaced image " + cosName.getName() + 
                                   ". Original: " + originalSize + " bytes, New: " + compressedImageData.length + 
                                   " bytes (" + String.format("%.1f", reduction) + "% reduction)");
                return true;
            } else {
                System.out.println("Skipping replacement for image " + cosName.getName() + 
                                   " - no significant improvement");
                return false;
            }
            
        } catch (Exception e) {
            System.out.println("Failed to process image " + cosName.getName() + ": " + e.getMessage());
            return false;
        }
    }
    
    private static BufferedImage resizeImage(BufferedImage originalImage) {
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
        
        newWidth = Math.max(1, newWidth);
        newHeight = Math.max(1, newHeight);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g.dispose();
        
        return resized;
    }
    
    private static byte[] compressToJpeg(BufferedImage image) throws IOException {
        BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D gRgb = rgbImage.createGraphics();
        gRgb.drawImage(image, 0, 0, null);
        gRgb.dispose();

        ByteArrayOutputStream compressedImageStream = new ByteArrayOutputStream();
        
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
    
    private static void optimizeDocumentStructure(PDDocument document) {
        try {
            System.out.println("Optimizing document structure...");
            
            // Remove metadata
            if (document.getDocumentInformation() != null) {
                document.getDocumentInformation().setAuthor(null);
                document.getDocumentInformation().setCreator(null);
                document.getDocumentInformation().setProducer(null);
                document.getDocumentInformation().setSubject(null);
                document.getDocumentInformation().setTitle(null);
                document.getDocumentInformation().setKeywords(null);
                System.out.println("Removed document metadata");
            }
            
            // Remove structure tree
            if (document.getDocumentCatalog().getStructureTreeRoot() != null) {
                document.getDocumentCatalog().setStructureTreeRoot(null);
                System.out.println("Removed structure tree");
            }
            
        } catch (Exception e) {
            System.out.println("Failed to optimize document structure: " + e.getMessage());
        }
    }
}
