package com.pdf.marsk.pdfdemo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.pdf.marsk.pdfdemo.model.OcrTextDocument;

@Repository
public interface OcrTextDocumentRepository extends JpaRepository<OcrTextDocument, Long> {
    List<OcrTextDocument> findByOriginalFilenameContainingIgnoreCase(String filename);
    List<OcrTextDocument> findAllByOrderByCreatedAtDesc();
}