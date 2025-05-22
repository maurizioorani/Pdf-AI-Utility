package com.pdf.marsk.pdfdemo.repository;

import com.pdf.marsk.pdfdemo.model.KnowledgeSnippet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeSnippetRepository extends JpaRepository<KnowledgeSnippet, Long> {

    // Find all snippets, ordered by creation date descending
    List<KnowledgeSnippet> findAllByOrderByCreatedAtDesc();

    // Optional: find by original PDF filename if needed for filtering later
    List<KnowledgeSnippet> findByOriginalPdfFilenameContainingIgnoreCaseOrderByCreatedAtDesc(String filename);

    // Optional: find by user query if needed
    List<KnowledgeSnippet> findByUserQueryContainingIgnoreCaseOrderByCreatedAtDesc(String query);
}