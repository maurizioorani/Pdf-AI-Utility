package com.pdf.marsk.pdfdemo.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PdfMergeServiceTest {

    @InjectMocks
    private PdfMergeService pdfMergeService;

    private MultipartFile createDummyPdf(String name, int pages) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return new MockMultipartFile(name, name + ".pdf", "application/pdf", baos.toByteArray());
        }
    }

    @Test
    void mergePdfs_success() throws IOException {
        MultipartFile pdf1 = createDummyPdf("file1", 1);
        MultipartFile pdf2 = createDummyPdf("file2", 2);
        List<MultipartFile> files = List.of(pdf1, pdf2);

        byte[] mergedBytes = pdfMergeService.mergePdfs(files);
        assertNotNull(mergedBytes);
        assertTrue(mergedBytes.length > 0);

        try (PDDocument mergedDoc = PDDocument.load(mergedBytes)) {
            assertEquals(3, mergedDoc.getNumberOfPages(), "Merged PDF should have 3 pages.");
        }
    }

    @Test
    void mergePdfs_notEnoughFiles_throwsIllegalArgumentException() throws IOException {
        MultipartFile pdf1 = createDummyPdf("file1", 1);
        List<MultipartFile> files = List.of(pdf1);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pdfMergeService.mergePdfs(files);
        });
        assertEquals("At least two PDF files are required for merging.", exception.getMessage());
    }

    @Test
    void mergePdfs_nullFileList_throwsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pdfMergeService.mergePdfs(null);
        });
        assertEquals("At least two PDF files are required for merging.", exception.getMessage());
    }
    
    @Test
    void mergePdfs_emptyFileList_throwsIllegalArgumentException() {
        List<MultipartFile> files = new ArrayList<>();
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pdfMergeService.mergePdfs(files);
        });
        assertEquals("At least two PDF files are required for merging.", exception.getMessage());
    }

    @Test
    void mergePdfs_invalidFileType_throwsIllegalArgumentException() throws IOException {
        MultipartFile pdf1 = createDummyPdf("file1", 1);
        MultipartFile notAPdf = new MockMultipartFile("file2", "file2.txt", "text/plain", "some text".getBytes());
        List<MultipartFile> files = List.of(pdf1, notAPdf);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pdfMergeService.mergePdfs(files);
        });
        assertTrue(exception.getMessage().contains("Invalid file type provided"));
    }
    
    @Test
    void mergePdfs_oneFileIsEmpty_skipsAndMergesOthers() throws IOException {
        MultipartFile pdf1 = createDummyPdf("file1", 1);
        MultipartFile emptyFile = new MockMultipartFile("empty", "empty.pdf", "application/pdf", new byte[0]);
        MultipartFile pdf3 = createDummyPdf("file3", 1);
        List<MultipartFile> files = List.of(pdf1, emptyFile, pdf3);

        byte[] mergedBytes = pdfMergeService.mergePdfs(files);
        assertNotNull(mergedBytes);
        try (PDDocument mergedDoc = PDDocument.load(mergedBytes)) {
            assertEquals(2, mergedDoc.getNumberOfPages(), "Merged PDF should have 2 pages, skipping the empty one.");
        }
    }

    @Test
    void mergePdfs_oneFileIsZeroPagePdf_skipsAndMergesOthers() throws IOException {
        MultipartFile pdf1 = createDummyPdf("file1", 1);
        MultipartFile zeroPagePdf = createDummyPdf("zeropage", 0); // Creates a valid PDF structure but no pages
        MultipartFile pdf3 = createDummyPdf("file3", 1);
        List<MultipartFile> files = List.of(pdf1, zeroPagePdf, pdf3);
    
        byte[] mergedBytes = pdfMergeService.mergePdfs(files);
        assertNotNull(mergedBytes);
        try (PDDocument mergedDoc = PDDocument.load(mergedBytes)) {
            assertEquals(2, mergedDoc.getNumberOfPages(), "Merged PDF should have 2 pages, skipping the zero-page one.");
        }
    }

    @Test
    void mergePdfs_allValidFilesButOneIsCorrupt_throwsIOException() throws IOException {
        MultipartFile pdf1 = createDummyPdf("file1", 1);
        // Corrupt PDF (not a real PDF structure)
        MultipartFile corruptPdf = new MockMultipartFile("corrupt", "corrupt.pdf", "application/pdf", "this is not a pdf".getBytes());
        MultipartFile pdf3 = createDummyPdf("file3", 1);
        List<MultipartFile> files = List.of(pdf1, corruptPdf, pdf3);

        IOException exception = assertThrows(IOException.class, () -> {
            pdfMergeService.mergePdfs(files);
        });
        assertTrue(exception.getMessage().contains("Invalid PDF file: corrupt.pdf"));
    }
    
    @Test
    void mergePdfs_onlyEmptyAndZeroPageFiles_throwsIOException() throws IOException {
        MultipartFile emptyFile = new MockMultipartFile("empty", "empty.pdf", "application/pdf", new byte[0]);
        MultipartFile zeroPagePdf = createDummyPdf("zeropage", 0);
        List<MultipartFile> files = List.of(emptyFile, zeroPagePdf);

        IOException exception = assertThrows(IOException.class, () -> {
            pdfMergeService.mergePdfs(files);
        });
        assertEquals("No valid PDF files were provided for merging after validation.", exception.getMessage());
    }
}