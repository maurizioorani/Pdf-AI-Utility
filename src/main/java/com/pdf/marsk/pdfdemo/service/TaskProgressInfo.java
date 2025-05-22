package com.pdf.marsk.pdfdemo.service;

import java.time.LocalDateTime;

public abstract class TaskProgressInfo {
    protected final String taskId;
    protected final String filename; // Original filename if applicable
    protected String currentStage; // e.g., "OCR", "LLM Processing", "Parsing"
    protected int progressPercent;
    protected String message;
    protected boolean completed;
    protected boolean success;
    protected final LocalDateTime createdAt;
    protected LocalDateTime updatedAt;

    public enum TaskType {
        OCR,
        KNOWLEDGE_EXTRACTION
    }
    protected final TaskType taskType;


    public TaskProgressInfo(String taskId, TaskType taskType, String filename, String initialStage) {
        this.taskId = taskId;
        this.taskType = taskType;
        this.filename = filename;
        this.currentStage = initialStage;
        this.progressPercent = 0;
        this.message = "Initializing...";
        this.completed = false;
        this.success = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getTaskId() {
        return taskId;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public String getFilename() {
        return filename;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String currentStage) {
        this.currentStage = currentStage;
        this.updatedAt = LocalDateTime.now();
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(int progressPercent) {
        this.progressPercent = Math.min(100, Math.max(0, progressPercent));
        this.updatedAt = LocalDateTime.now();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}