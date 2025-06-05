package com.pdf.marsk.pdfdemo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for improving content using AI
 */
@Service
public class ContentImprovementService {
    
    private static final Logger logger = LoggerFactory.getLogger(ContentImprovementService.class);
    
    @Autowired
    private OllamaService ollamaService;
    
    /**
     * Improves the given HTML content using AI
     * @param htmlContent The original HTML content to improve
     * @param modelName The Ollama model to use
     * @return The improved HTML content
     */
    public String improveContent(String htmlContent, String modelName) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            logger.warn("Empty content provided for improvement");
            return htmlContent;
        }
        
        try {
            String prompt = createImprovementPrompt(htmlContent);
            String improvedContent = ollamaService.generateResponse(prompt, modelName);
            
            // Clean up the response to ensure it's valid HTML
            String cleanedContent = cleanupImprovedContent(improvedContent);
            
            logger.info("Successfully improved content using model: {}", modelName);
            return cleanedContent;
            
        } catch (Exception e) {
            logger.error("Error improving content with model {}: {}", modelName, e.getMessage(), e);
            // Return original content if improvement fails
            return htmlContent;
        }
    }
    
    /**
     * Creates a focused prompt for content improvement
     */
    private String createImprovementPrompt(String htmlContent) {
        return """
            You are an expert content editor and writing assistant. Your task is to improve the given HTML content while preserving its structure and formatting.
            
            INSTRUCTIONS:
            1. Improve the text content for clarity, readability, and engagement
            2. Fix any grammar, spelling, or punctuation errors
            3. Enhance the overall flow and coherence of the content
            4. Make the writing more professional and polished
            5. PRESERVE all HTML tags and structure exactly as they are
            6. ONLY modify the text content within the HTML tags, not the tags themselves
            7. Do not add new HTML elements or remove existing ones
            8. Return ONLY the improved HTML content, no explanations or preambles
            
            IMPORTANT: Your response should be valid HTML that can be directly used in an editor.
            
            HTML Content to improve:
            ```html
            %s
            ```
            
            Improved HTML content:
            """.formatted(htmlContent);
    }
    
    /**
     * Cleans up the AI response to ensure it's valid HTML
     */
    private String cleanupImprovedContent(String content) {
        if (content == null) {
            return "";
        }
        
        // Remove any markdown code block markers if present
        content = content.replaceAll("^```html\\s*", "").replaceAll("^```\\s*", "");
        content = content.replaceAll("\\s*```$", "");
        
        // Remove any explanatory text that might have been added before the HTML
        if (content.contains("<")) {
            int firstTagIndex = content.indexOf("<");
            content = content.substring(firstTagIndex);
        }
        
        // Remove any explanatory text that might have been added after the HTML
        int lastTagIndex = content.lastIndexOf(">");
        if (lastTagIndex != -1 && lastTagIndex < content.length() - 1) {
            String afterLastTag = content.substring(lastTagIndex + 1).trim();
            // If there's significant text after the last HTML tag, remove it
            if (afterLastTag.length() > 50 || afterLastTag.toLowerCase().contains("improved") || 
                afterLastTag.toLowerCase().contains("enhanced") || afterLastTag.toLowerCase().contains("here")) {
                content = content.substring(0, lastTagIndex + 1);
            }
        }
        
        return content.trim();
    }
}
