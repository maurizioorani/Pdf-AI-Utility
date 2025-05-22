package com.pdf.marsk.pdfdemo.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Service for tracking the progress of OCR operations.
 * Uses a task ID-based approach to track progress of different OCR operations.
 */
@Service
public class ProgressTrackingService {

    // Map to store progress information for each task, now using the base class
    private final Map<String, TaskProgressInfo> progressMap = new ConcurrentHashMap<>();

    /**
     * Creates a new OCR task and returns its ID for tracking.
     *
     * @param filename The name of the file being processed
     * @param totalPages The total number of pages to process
     * @param language The language used for OCR
     * @return The task ID for tracking progress
     */
    public String createOcrTask(String filename, int totalPages, String language) {
        String taskId = generateTaskId("ocr");
        OcrProgressInfo ocrInfo = new OcrProgressInfo(taskId, filename, totalPages, language);
        progressMap.put(taskId, ocrInfo);
        return taskId;
    }

    /**
     * Creates a new Knowledge Extraction task and returns its ID.
     * @param filename Name of the PDF file.
     * @param query User's query for extraction.
     * @param modelName LLM model to be used.
     * @return The task ID.
     */
    public String createKnowledgeExtractionTask(String filename, String query, String modelName) {
        String taskId = generateTaskId("ke"); // "ke" for Knowledge Extraction
        KnowledgeExtractionProgressInfo keInfo = new KnowledgeExtractionProgressInfo(taskId, filename, query, modelName);
        progressMap.put(taskId, keInfo);
        return taskId;
    }


    /**
     * Updates the progress of an OCR task.
     *
     * @param taskId The task ID
     * @param currentPage The current page being processed
     * @param message Additional status message (optional)
     */
    public void updateOcrTaskProgress(String taskId, int currentPage, String message) {
        TaskProgressInfo taskInfo = progressMap.get(taskId);
        if (taskInfo instanceof OcrProgressInfo info) {
            info.setCurrentPage(currentPage);
            info.setMessage(message);
            info.setProgressPercent(calculateOcrProgress(currentPage, info.getTotalPages()));
        }
    }
    
    /**
     * Updates the stage and general progress of any task.
     * @param taskId The task ID.
     * @param stage The current processing stage (e.g., "OCR", "LLM Processing").
     * @param percent The overall progress percentage (0-100).
     * @param message A status message.
     */
    public void updateTaskProgress(String taskId, String stage, int percent, String message) {
        TaskProgressInfo info = progressMap.get(taskId);
        if (info != null) {
            info.setCurrentStage(stage);
            info.setProgressPercent(percent);
            info.setMessage(message);
        }
    }


    /**
     * Updates the total pages for an OCR task.
     *
     * @param taskId The task ID
     * @param totalPages The actual total number of pages
     */
    public void updateOcrTaskTotalPages(String taskId, int totalPages) {
        TaskProgressInfo taskInfo = progressMap.get(taskId);
        if (taskInfo instanceof OcrProgressInfo info) {
            info.setTotalPages(totalPages);
            info.setProgressPercent(calculateOcrProgress(info.getCurrentPage(), totalPages));
        }
    }

    /**
     * Gets the current progress information for a task.
     *
     * @param taskId The task ID
     * @return The progress information or null if the task doesn't exist
     */
    public TaskProgressInfo getProgress(String taskId) { // Return type changed to base class
        return progressMap.get(taskId);
    }

    /**
     * Marks a task as completed.
     *
     * @param taskId The task ID
     * @param success Whether the task completed successfully
     * @param result The result message or error information (can be actual result or error string)
     */
    public void completeTask(String taskId, boolean success, String result) {
        TaskProgressInfo info = progressMap.get(taskId); // Use base class
        if (info != null) {
            info.setCompleted(true);
            info.setSuccess(success);
            info.setMessage(result); // This message could be the final result or an error.
            info.setProgressPercent(100);
        }
    }

    /**
     * Removes a task from tracking.
     *
     * @param taskId The task ID to remove
     */
    public void removeTask(String taskId) {
        progressMap.remove(taskId);
    }

    /**
     * Calculate progress percentage for OCR tasks.
     */
    private int calculateOcrProgress(int currentPage, int totalPages) {
        if (totalPages <= 0) return 0;
        return Math.min(100, (int) (((float) currentPage / totalPages) * 100));
    }

    /**
     * Generates a unique task ID with a given prefix.
     */
    private String generateTaskId(String prefix) {
        return prefix + "-" + System.currentTimeMillis() + "-" + Math.abs(java.util.UUID.randomUUID().hashCode());
    }

    /**
     * Class to hold progress information for an OCR task.
     * Extends the generic TaskProgressInfo.
     */
    public static class OcrProgressInfo extends TaskProgressInfo {
        private int totalPages;
        private int currentPage;
        private String language;

        public OcrProgressInfo(String taskId, String filename, int totalPages, String language) {
            super(taskId, TaskType.OCR, filename, "OCR Initializing");
            this.totalPages = totalPages;
            this.language = language;
            this.currentPage = 0;
            // Initial message and progress are set by super constructor
            super.setMessage("Initializing OCR for " + filename + "...");
        }

        public int getTotalPages() {
            return totalPages;
        }

        public void setTotalPages(int totalPages) {
            this.totalPages = totalPages;
            super.updatedAt = java.time.LocalDateTime.now(); // Ensure parent's updatedAt is also touched
        }

        public int getCurrentPage() {
            return currentPage;
        }

        public void setCurrentPage(int currentPage) {
            this.currentPage = currentPage;
            super.updatedAt = java.time.LocalDateTime.now();
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
            super.updatedAt = java.time.LocalDateTime.now();
        }
        
        // Override setters from TaskProgressInfo if they need specific OCR logic,
        // or rely on superclass methods. For example, setProgressPercent might be calculated
        // based on currentPage and totalPages specifically for OCR.
    }
}
