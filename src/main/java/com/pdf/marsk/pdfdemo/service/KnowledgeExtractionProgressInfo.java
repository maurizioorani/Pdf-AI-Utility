package com.pdf.marsk.pdfdemo.service;

public class KnowledgeExtractionProgressInfo extends TaskProgressInfo {

    private String query;
    private String modelName;
    // Potentially add fields like:
    // private int totalChunks;
    // private int chunksProcessed;

    public KnowledgeExtractionProgressInfo(String taskId, String filename, String query, String modelName) {
        super(taskId, TaskType.KNOWLEDGE_EXTRACTION, filename, "Starting");
        this.query = query;
        this.modelName = modelName;
        this.setMessage("Initializing knowledge extraction...");
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    // Add getters and setters for any new fields like totalChunks, chunksProcessed
}