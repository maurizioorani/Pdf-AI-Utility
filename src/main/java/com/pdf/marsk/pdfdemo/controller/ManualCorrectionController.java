package com.pdf.marsk.pdfdemo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Controller for handling manual OCR text corrections
 */
@Controller
@RequestMapping("/ocr/manual")
public class ManualCorrectionController {
    
    private static final Logger logger = LoggerFactory.getLogger(ManualCorrectionController.class);

    // Define a static inner class for the API response
    private static class ManualCorrectionApiResponse {
        private final String original;
        private final String corrected;
        private final boolean success;
        private final String message;

        public ManualCorrectionApiResponse(String original, String corrected, boolean success, String message) {
            this.original = original;
            this.corrected = corrected;
            this.success = success;
            this.message = message;
        }

        // Getters are needed for Jackson serialization
        public String getOriginal() {
            return original;
        }

        public String getCorrected() {
            return corrected;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
    
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
                // Consider creating a standard error response object as well
                return ResponseEntity.badRequest().body("Corrected text cannot be empty");
            }
            
            // Use the new response object
            ManualCorrectionApiResponse apiResponse = new ManualCorrectionApiResponse(
                originalText,
                correctedText,
                true,
                "Manual correction applied successfully"
            );
            
            logger.info("Manual correction completed successfully");
            
            return ResponseEntity.ok(apiResponse);
        } catch (Exception e) {
            logger.error("Error in manual correction: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}
