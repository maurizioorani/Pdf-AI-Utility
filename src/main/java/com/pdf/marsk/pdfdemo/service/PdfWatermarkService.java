package com.pdf.marsk.pdfdemo.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class PdfWatermarkService {

    public byte[] addWatermark(MultipartFile pdfFile, String watermarkText, float opacity, String position) throws IOException {
        try (PDDocument document = PDDocument.load(pdfFile.getInputStream())) {
            PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
            graphicsState.setNonStrokingAlphaConstant(opacity);
            graphicsState.setAlphaSourceFlag(true);

            for (PDPage page : document.getPages()) {
                try (PDPageContentStream contentStream = new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    
                    contentStream.setGraphicsStateParameters(graphicsState);
                    
                    // Calculate position based on parameter
                    float x, y;
                    switch (position.toLowerCase()) {
                        case "top-left":
                            x = 50;
                            y = page.getMediaBox().getHeight() - 50;
                            break;
                        case "top-right":
                            x = page.getMediaBox().getWidth() - 150;
                            y = page.getMediaBox().getHeight() - 50;
                            break;
                        case "bottom-left":
                            x = 50;
                            y = 50;
                            break;
                        case "bottom-right":
                            x = page.getMediaBox().getWidth() - 150;
                            y = 50;
                            break;
                        default: // center
                            x = page.getMediaBox().getWidth() / 2 - 50;
                            y = page.getMediaBox().getHeight() / 2;
                    }

                    contentStream.beginText();
                    contentStream.setFont(PDType1Font.HELVETICA_BOLD, 48);
                    contentStream.newLineAtOffset(x, y);
                    contentStream.showText(watermarkText);
                    contentStream.endText();
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}