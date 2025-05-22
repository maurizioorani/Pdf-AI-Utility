package com.pdf.marsk.pdfdemo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pdf.marsk.pdfdemo.service.ProgressTrackingService;
import com.pdf.marsk.pdfdemo.service.TaskProgressInfo; // Import base class
import com.pdf.marsk.pdfdemo.service.ProgressTrackingService.OcrProgressInfo; // Import specific OCR progress info

/**
 * REST controller for OCR progress tracking.
 */
@RestController
@RequestMapping("/api/ocr/progress")
public class OcrProgressController {

    @Autowired
    private ProgressTrackingService progressTrackingService;

    /**
     * Gets the current progress of an OCR task.
     *
     * @param taskId The task ID
     * @return The progress information
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<?> getProgress(@PathVariable String taskId) {
        TaskProgressInfo generalProgress = progressTrackingService.getProgress(taskId);
        
        if (generalProgress == null) {
            return ResponseEntity.notFound().build();
        }

        // Check if it's an OCR task and cast
        if (generalProgress instanceof OcrProgressInfo ocrProgress) {
            return ResponseEntity.ok(ocrProgress);
        } else {
            // If it's a different type of task or an unknown state,
            // you might return a generic progress or an error/specific DTO.
            // For now, let's return the general progress if it's not specifically OCR,
            // or consider it a "not found" for this specific OCR progress endpoint.
            // Returning generalProgress might be okay if the client can handle it,
            // but this endpoint is specifically for OCR progress.
            // So, if it's not OcrProgressInfo, it's effectively not found for this controller's purpose.
            // Alternatively, return a generic DTO or an error.
            // For simplicity, if it's not OcrProgressInfo, treat as not found for this specific endpoint.
             return ResponseEntity.status(404).body("Task ID found, but it's not an OCR task or type is mismatched.");
        }
    }
}
