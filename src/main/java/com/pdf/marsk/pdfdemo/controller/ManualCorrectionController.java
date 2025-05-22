package com.pdf.marsk.pdfdemo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for handling manual OCR text corrections
 */
@Controller
@RequestMapping("/ocr/manual")
public class ManualCorrectionController {
    
    private static final Logger logger = LoggerFactory.getLogger(ManualCorrectionController.class);
    
    /**
     * Handle manual text correction
     */
    @PostMapping("/correction")
    @ResponseBody
    public ResponseEntity<?> handleManualCorrection(@RequestParam("originalText") String originalText,
                                                  @RequestParam("correctedText") String correctedText) {
        try {
            logger.info("Received manual correction request");
            
            // Simple validation
            if (correctedText == null || correctedText.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Corrected text cannot be empty");
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("original", originalText);
            response.put("corrected", correctedText);
            response.put("success", true);
            response.put("message", "Manual correction applied successfully");
            
            logger.info("Manual correction completed successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error in manual correction: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}
