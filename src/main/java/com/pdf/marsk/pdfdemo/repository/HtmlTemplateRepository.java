package com.pdf.marsk.pdfdemo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.pdf.marsk.pdfdemo.model.HtmlTemplate;

@Repository
public interface HtmlTemplateRepository extends JpaRepository<HtmlTemplate, Long> {
    List<HtmlTemplate> findAllByOrderByCreatedAtDesc();
}