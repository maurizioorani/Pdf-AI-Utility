package com.pdf.marsk.pdfdemo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for chunking large text documents into smaller segments for better OCR correction
 * Specialized for handling multi-page PDF text, especially for Italian literary content
 */
@Service
public class TextChunkingService {
    
    private static final Logger logger = LoggerFactory.getLogger(TextChunkingService.class);
    
    @Value("${ocr.chunking.maxChunkSize:5000}")
    private int maxChunkSize;
    
    @Value("${ocr.chunking.minChunkSize:1000}")
    private int minChunkSize;
    
    @Value("${ocr.chunking.enabled:true}")
    private boolean chunkingEnabled;
    
    /**
     * Check if text chunking should be applied based on text length and settings
     * 
     * @param text The text to evaluate
     * @return true if chunking should be applied
     */
    public boolean shouldChunkText(String text) {
        if (!chunkingEnabled) {
            return false;
        }
        
        // Apply chunking if text exceeds max chunk size
        return text.length() > maxChunkSize;
    }
    
    /**
     * Split text into intelligent chunks for OCR correction
     * Intelligently tries to split at natural breakpoints like paragraphs, sentences or pages
     * 
     * @param text The text to split into chunks
     * @return List of text chunks
     */
    public List<String> chunkText(String text) {
        if (!shouldChunkText(text)) {
            return List.of(text);
        }
        
        List<String> chunks = new ArrayList<>();
        
        try {
            // First try splitting by PDF page markers if they exist
            if (text.contains("--- Page ")) {
                chunks = splitByPages(text);
            } else {
                // Otherwise split by paragraphs and sentences
                chunks = splitByParagraphsAndSentences(text);
            }
            
            // Log chunking results
            logger.info("Split text into {} chunks (avg size: {} chars)", 
                    chunks.size(), 
                    chunks.stream().mapToInt(String::length).sum() / chunks.size());
            
            return chunks;
        } catch (Exception e) {
            logger.error("Error during text chunking: {}", e.getMessage());
            // Fallback to simple chunking if something goes wrong
            return simpleChunking(text);
        }
    }
    
    /**
     * Split text by PDF page markers
     * 
     * @param text Text with page markers
     * @return List of page chunks
     */
    private List<String> splitByPages(String text) {
        List<String> pageChunks = new ArrayList<>();
        Pattern pagePattern = Pattern.compile("--- Page \\d+ ---\\s*");
        Matcher pageMatcher = pagePattern.matcher(text);
        
        // Find all page markers
        List<Integer> pageBreakPositions = new ArrayList<>();
        while (pageMatcher.find()) {
            pageBreakPositions.add(pageMatcher.start());
        }
        
        // No page breaks found
        if (pageBreakPositions.isEmpty()) {
            return splitByParagraphsAndSentences(text);
        }
        
        // Extract each page
        for (int i = 0; i < pageBreakPositions.size(); i++) {
            int start = pageBreakPositions.get(i);
            int end = (i < pageBreakPositions.size() - 1) ? pageBreakPositions.get(i + 1) : text.length();
            String pageChunk = text.substring(start, end);
            
            // If a page is too large, break it down further
            if (pageChunk.length() > maxChunkSize) {
                pageChunks.addAll(splitByParagraphsAndSentences(pageChunk));
            } else {
                pageChunks.add(pageChunk);
            }
        }
        
        // If there's content before the first page marker, include it
        if (!pageBreakPositions.isEmpty() && pageBreakPositions.get(0) > 0) {
            pageChunks.add(0, text.substring(0, pageBreakPositions.get(0)));
        }
        
        // Further optimize chunks
        return optimizeChunks(pageChunks);
    }
    
    /**
     * Split text by paragraphs and sentences to create semantically meaningful chunks
     * 
     * @param text Text to split
     * @return List of text chunks
     */
    private List<String> splitByParagraphsAndSentences(String text) {
        List<String> chunks = new ArrayList<>();
        
        // Split by paragraph breaks
        String[] paragraphs = text.split("\\n\\s*\\n");
        
        StringBuilder currentChunk = new StringBuilder();
        
        for (String paragraph : paragraphs) {
            // If adding this paragraph exceeds max size and we already have content,
            // save current chunk and start a new one
            if (currentChunk.length() > 0 && 
                currentChunk.length() + paragraph.length() > maxChunkSize) {
                
                chunks.add(currentChunk.toString());
                currentChunk = new StringBuilder();
            }
            
            // If a single paragraph exceeds max size, split by sentences
            if (paragraph.length() > maxChunkSize) {
                List<String> sentenceChunks = splitIntoSentences(paragraph);
                
                for (String sentenceChunk : sentenceChunks) {
                    if (currentChunk.length() + sentenceChunk.length() > maxChunkSize) {
                        if (currentChunk.length() > 0) {
                            chunks.add(currentChunk.toString());
                            currentChunk = new StringBuilder();
                        }
                        
                        // If a sentence chunk is still too large, use simple chunking as last resort
                        if (sentenceChunk.length() > maxChunkSize) {
                            chunks.addAll(simpleChunking(sentenceChunk));
                        } else {
                            currentChunk.append(sentenceChunk);
                        }
                    } else {
                        currentChunk.append(sentenceChunk);
                    }
                }
            } else {
                // Add paragraph separator if this isn't the first paragraph in the chunk
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(paragraph);
            }
        }
        
        // Don't forget the last chunk
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }
        
        return optimizeChunks(chunks);
    }
    
    /**
     * Split text into sentences, trying to preserve semantic meaning
     * Enhanced for Italian text with specific sentence patterns
     * 
     * @param text Text to split into sentences
     * @return List of sentences grouped into reasonable chunks
     */
    private List<String> splitIntoSentences(String text) {
        List<String> chunks = new ArrayList<>();
        
        // Regex for sentence boundaries, including Italian patterns
        // Handles standard periods, question marks, exclamation points
        String sentenceRegex = "(?<=[.!?])\\s+(?=[A-Z0-9])|(?<=\\.)\\s*\\n+";
        
        // Split the text into sentences
        String[] sentences = text.split(sentenceRegex);
        
        StringBuilder currentChunk = new StringBuilder();
        
        for (String sentence : sentences) {
            // If adding this sentence would exceed max size, save current chunk and start new one
            if (currentChunk.length() > 0 && 
                currentChunk.length() + sentence.length() > maxChunkSize) {
                
                chunks.add(currentChunk.toString());
                currentChunk = new StringBuilder();
            }
            
            // If a single sentence is too large (rare but possible), use simple chunking
            if (sentence.length() > maxChunkSize) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString());
                    currentChunk = new StringBuilder();
                }
                chunks.addAll(simpleChunking(sentence));
            } else {
                // Add sentence separator if needed
                if (currentChunk.length() > 0 && !currentChunk.toString().endsWith(" ")) {
                    currentChunk.append(" ");
                }
                currentChunk.append(sentence);
            }
        }
        
        // Don't forget the last chunk
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }
        
        return chunks;
    }
    
    /**
     * Simplest chunking method as a fallback - split by character count
     * 
     * @param text Text to chunk
     * @return List of chunks
     */
    private List<String> simpleChunking(String text) {
        List<String> chunks = new ArrayList<>();
        
        // Simple chunking as a last resort - try to break at space characters
        int startIndex = 0;
        while (startIndex < text.length()) {
            int endIndex = Math.min(startIndex + maxChunkSize, text.length());
            
            // Try to end at a space if possible
            if (endIndex < text.length()) {
                int lastSpaceIndex = text.lastIndexOf(' ', endIndex);
                if (lastSpaceIndex > startIndex && lastSpaceIndex > endIndex - 100) {
                    endIndex = lastSpaceIndex + 1;
                }
            }
            
            chunks.add(text.substring(startIndex, endIndex));
            startIndex = endIndex;
        }
        
        return chunks;
    }
    
    /**
     * Optimize chunks by merging very small chunks with adjacent chunks
     * 
     * @param chunks List of text chunks
     * @return Optimized list of chunks
     */
    private List<String> optimizeChunks(List<String> chunks) {
        if (chunks.size() <= 1) {
            return chunks;
        }
        
        List<String> optimizedChunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder(chunks.get(0));
        
        for (int i = 1; i < chunks.size(); i++) {
            String nextChunk = chunks.get(i);
            
            // If current chunk is small and adding next chunk won't exceed max, combine them
            if (currentChunk.length() < minChunkSize && 
                currentChunk.length() + nextChunk.length() <= maxChunkSize) {
                
                // Add a separator if needed
                if (!currentChunk.toString().endsWith("\n")) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(nextChunk);
            } 
            // If next chunk is too small, try to add it to current chunk
            else if (nextChunk.length() < minChunkSize && 
                     currentChunk.length() + nextChunk.length() <= maxChunkSize) {
                
                // Add a separator if needed
                if (!currentChunk.toString().endsWith("\n")) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(nextChunk);
            } 
            // Otherwise save current chunk and start a new one
            else {
                optimizedChunks.add(currentChunk.toString());
                currentChunk = new StringBuilder(nextChunk);
            }
        }
        
        // Don't forget the last chunk
        if (currentChunk.length() > 0) {
            optimizedChunks.add(currentChunk.toString());
        }
        
        return optimizedChunks;
    }
    
    /**
     * Merge corrected text chunks back together
     * 
     * @param chunks List of corrected text chunks
     * @param preservePageMarkers Whether to preserve page markers in the output
     * @return Combined text
     */
    public String mergeChunks(List<String> chunks, boolean preservePageMarkers) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        
        if (chunks.size() == 1) {
            return chunks.get(0);
        }
        
        StringBuilder merged = new StringBuilder();
        Pattern pagePattern = Pattern.compile("^--- Page (\\d+) ---\\s*");
        
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            
            // Handle page markers
            Matcher pageMatcher = pagePattern.matcher(chunk);
            if (pageMatcher.find()) {
                // Keep page marker if requested
                if (preservePageMarkers) {
                    merged.append(chunk);
                } else {
                    // Otherwise, skip the page marker line but keep the content
                    merged.append(chunk.substring(pageMatcher.end()));
                }
            } else {
                // Add a separator between chunks that don't start with page markers
                // if the previous chunk doesn't end with a newline
                if (i > 0 && !merged.toString().endsWith("\n") && !chunk.startsWith("\n")) {
                    merged.append("\n\n");
                }
                merged.append(chunk);
            }
        }
        
        return merged.toString();
    }
}
