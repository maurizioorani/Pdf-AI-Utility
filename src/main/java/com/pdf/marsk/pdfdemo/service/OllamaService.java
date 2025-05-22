package com.pdf.marsk.pdfdemo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for interacting with Ollama LLM to enhance OCR text
 * Enhanced with chunking support for large documents
 */
@Service
public class OllamaService {
    private static final Logger logger = LoggerFactory.getLogger(OllamaService.class);
    private final ChatClient chatClient;
    private final TextChunkingService textChunkingService;
    
    @Value("${ollama.baseurl:http://localhost:11434}")
    private String ollamaApiBaseUrl;
    
    @Value("${ollama.chunking.maxWorkers:3}")
    private int maxChunkingWorkers;
    
    @Value("${ollama.chunking.enabled:true}")
    private boolean chunkingEnabled;

    // Pattern to find common prompt markers followed by ```text```
    private static final Pattern PROMPT_ECHO_PATTERN = Pattern.compile(
        "(TEXT TO CORRECT:|BUSINESS DOCUMENT TO CORRECT:|ACADEMIC DOCUMENT TO CORRECT:|TECHNICAL DOCUMENT TO CORRECT:|LEGAL DOCUMENT TO CORRECT:|ITALIAN LITERARY TEXT TO CORRECT:|LITERARY TEXT TO CORRECT:)\\s*`{0,3}\\s*([\\s\\S]*?)\\s*(`{0,3}\\s*(Remember: ONLY the corrected text. Nothing else.|Remember: ONLY the corrected Italian text. Nothing else.))?$", 
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
     private static final Pattern SIMPLE_PROMPT_ECHO_PATTERN = Pattern.compile(
        "(LITERARY TEXT TO CORRECT:)\\s*`{0,3}\\s*([\\s\\S]*?)\\s*(`{0,3})?$", 
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );


    public OllamaService(ChatClient chatClient, TextChunkingService textChunkingService) {
        this.chatClient = chatClient;
        this.textChunkingService = textChunkingService;
    }

    public EnhancementResult enhanceText(String text, String modelName, String customPrompt) {
        return enhanceText(text, modelName, customPrompt, null);
    }
    
    public EnhancementResult enhanceText(String text, String modelName, String customPrompt, Boolean enableChunking) {
        if (text == null || text.trim().isEmpty()) {
            logger.warn("Empty text provided for enhancement");
            return new EnhancementResult(text, false);
        }

        if (modelName == null || modelName.trim().isEmpty()) {
            logger.warn("No model specified for text enhancement");
            return new EnhancementResult(text, false);
        }

        boolean shouldApplyChunking;
        if (enableChunking != null) {
            shouldApplyChunking = enableChunking && textChunkingService != null && textChunkingService.shouldChunkText(text);
            logger.info("Using user-specified chunking preference: {}", enableChunking);
        } else {
            shouldApplyChunking = this.chunkingEnabled && textChunkingService != null && textChunkingService.shouldChunkText(text);
        }

        if (shouldApplyChunking) {
            logger.info("Text exceeds maximum chunk size or chunking explicitly enabled, applying chunking for Ollama model: {}", modelName);
            return processWithChunking(text, modelName, customPrompt);
        } else {
            logger.info("Enhancing OCR text with Ollama model: {} (chunking disabled or not needed)", modelName);
            return processSingleText(text, modelName, customPrompt);
        }
    }
    
    private EnhancementResult processSingleText(String text, String modelName, String customPromptToUse) {
        try {
            String promptText;
            if (customPromptToUse != null && !customPromptToUse.trim().isEmpty()) {
                promptText = customPromptToUse.contains("%s") 
                    ? String.format(customPromptToUse, text) 
                    : customPromptToUse + "\n\n" + text; // Fallback if %s is missing
            } else {
                // Default generic prompt
                promptText = getSpecializedPrompt("generic", text);
            }
            
            UserMessage userMessage = new UserMessage(promptText);
            Prompt prompt = new Prompt(userMessage);
            
            String llmResponse = chatClient.call(prompt).getResult().getOutput().getContent();
            
            LlmResponseResult result = detectAndFixProblematicResponse(text, llmResponse, modelName);
            
            logger.info("Successfully enhanced OCR text. Fix applied: {}", result.wasFixed());
            return new EnhancementResult(result.getText(), result.wasFixed());
            
        } catch (Exception e) {
            logger.error("Error enhancing OCR text with model {}: {}", modelName, e.getMessage(), e);
            return new EnhancementResult(text, false); // Fallback to original text on error
        }
    }
    
    private EnhancementResult processWithChunking(String text, String modelName, String customPrompt) {
        try {
            List<String> chunks = textChunkingService.chunkText(text);
            logger.info("Split text into {} chunks for processing with model {}", chunks.size(), modelName);
            
            if (chunks.size() > 1 && maxChunkingWorkers > 1) {
                return processChunksInParallel(chunks, modelName, customPrompt);
            } else {
                return processChunksSequentially(chunks, modelName, customPrompt);
            }
        } catch (Exception e) {
            logger.error("Error during chunked text enhancement with model {}: {}", modelName, e.getMessage(), e);
            return new EnhancementResult(text, false);
        }
    }

    private EnhancementResult processChunksSequentially(List<String> chunks, String modelName, String customPrompt) {
        List<String> enhancedChunks = new ArrayList<>();
        boolean anyChunkFixed = false;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            logger.info("Processing chunk {}/{} (size: {} chars) with model {}", i + 1, chunks.size(), chunk.length(), modelName);
            EnhancementResult chunkResult = processSingleText(chunk, modelName, customPrompt); // Use the main processing logic
            enhancedChunks.add(chunkResult.getEnhancedText());
            if (chunkResult.wasAnalysisFixed()) {
                anyChunkFixed = true;
            }
        }
        boolean preservePageMarkers = chunks.size() > 0 && chunks.get(0).contains("--- Page ");
        String combinedText = textChunkingService.mergeChunks(enhancedChunks, preservePageMarkers);
        logger.info("Successfully processed and recombined {} text chunks with model {}", chunks.size(), modelName);
        return new EnhancementResult(combinedText, anyChunkFixed);
    }

    private EnhancementResult processChunksInParallel(List<String> chunks, String modelName, String customPrompt) {
        ExecutorService executor = null;
        try {
            int workerCount = Math.min(chunks.size(), maxChunkingWorkers);
            executor = Executors.newFixedThreadPool(workerCount);
            logger.info("Processing {} chunks in parallel with {} workers for model {}", chunks.size(), workerCount, modelName);
            
            List<CompletableFuture<EnhancementResult>> futures = new ArrayList<>();
            for (String chunk : chunks) {
                futures.add(CompletableFuture.supplyAsync(() -> processSingleText(chunk, modelName, customPrompt), executor));
            }
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(); // Wait for all
            
            List<String> enhancedChunks = new ArrayList<>();
            boolean anyChunkFixed = false;
            for (CompletableFuture<EnhancementResult> future : futures) {
                EnhancementResult result = future.get();
                enhancedChunks.add(result.getEnhancedText());
                if (result.wasAnalysisFixed()) anyChunkFixed = true;
            }
            
            boolean preservePageMarkers = chunks.size() > 0 && chunks.get(0).contains("--- Page ");
            String combinedText = textChunkingService.mergeChunks(enhancedChunks, preservePageMarkers);
            logger.info("Successfully processed {} text chunks in parallel with model {}", chunks.size(), modelName);
            return new EnhancementResult(combinedText, anyChunkFixed);
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error during parallel chunk processing with model {}: {}", modelName, e.getMessage(), e);
            Thread.currentThread().interrupt(); // Restore interrupt status
            return new EnhancementResult(String.join("\n\n", chunks), false); // Fallback
        } finally {
            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(30, TimeUnit.SECONDS)) executor.shutdownNow();
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    
    public static class EnhancementResult {
        private final String enhancedText;
        private final boolean wasAnalysisFixed;
        public EnhancementResult(String enhancedText, boolean wasAnalysisFixed) {
            this.enhancedText = enhancedText;
            this.wasAnalysisFixed = wasAnalysisFixed;
        }
        public String getEnhancedText() { return enhancedText; }
        public boolean wasAnalysisFixed() { return wasAnalysisFixed; }
    }

    public List<String> getAvailableModels() {
        List<String> models = List.of("llama3", "llama3:8b", "llama3:70b", "mistral", "mistral-small", "mixtral", "gemma:7b", "gemma:2b", "phi3:small", "phi3:medium", "codellama", "llava");
        try {
            logger.info("Returning hardcoded list of Ollama models. Count: {}", models.size());
        } catch (Exception e) {
            System.err.println("OllamaService: Failed to log in getAvailableModels: " + e.getMessage()); 
        }
        return models;
    }
    
    public String getSpecializedPrompt(String documentType, String text) {
        String commonHeader = "IMPORTANT: Your entire response MUST be ONLY the corrected text. No preambles, no explanations, no apologies, no conversational filler. Start directly with the corrected text. The text to correct is provided below the line 'TEXT TO CORRECT:'.\n\nYou are a meticulous and highly accurate OCR error correction engine. Your SOLE AND ONLY task is to identify and fix OCR errors in the provided scanned text.\n\nCRITICAL INSTRUCTIONS:\n- ABSOLUTELY DO NOT analyze, interpret, summarize, rephrase, or explain the text content in any way.\n- ABSOLUTELY DO NOT add any new information, opinions, or interpretations.\n- ABSOLUTELY DO NOT add any introductory phrases (like \"Okay, here is the corrected text:\", \"I understand...\", \"Certainly...\"), concluding remarks, or any text other than the corrected OCR output.\n- Your output MUST be ONLY the corrected version of the input text.\n- DO NOT ask for the text; it is provided below.\n\nDETAILED CORRECTION GUIDELINES:\n1.  Correct spelling mistakes that are clearly OCR errors (e.g., \"lettcr\" -> \"letter\", \"num8er\" -> \"number\").\n2.  Fix word segmentation problems (e.g., \"wor d\" -> \"word\", \"helloworld\" -> \"hello world\" if contextually appropriate).\n3.  Restore correct punctuation and capitalization where it's obviously missing or incorrect due to OCR.\n4.  Meticulously preserve the original paragraph structure, line breaks, and formatting (indentation, spacing). If the original has specific formatting, replicate it.\n5.  DO NOT change sentence structure or word order unless it's a clear and unambiguous OCR error causing nonsensical phrasing. (e.g., \"the cat sat\" should NOT become \"the feline was seated\").\n6.  Pay close attention to numbers, dates, and special characters, ensuring they are accurately transcribed.\n7.  If unsure about a correction, err on the side of preserving the original text segment. It's better to leave a potential minor OCR error than to introduce an incorrect \"fix\".\n";
        String commonFooter = "\nRemember: ONLY the corrected text. Nothing else.";

        switch (documentType.toLowerCase()) {
            case "business":
                return commonHeader +
                    "\nDETAILED CORRECTION GUIDELINES FOR BUSINESS DOCUMENTS:\n" +
                    "4.  PAY EXTREME ATTENTION TO: Financial figures (e.g., $1,000.00, €50.75), dates (e.g., 2023-10-27, Oct 27, 2023), proper nouns (company names, people's names, product names), addresses, and contact information. Ensure these are transcribed with perfect accuracy.\n" +
                    "5.  Ensure consistent formatting for lists, tables (if present and discernible), and headings.\n" +
                    "\nEXAMPLE:\nINPUT TEXT (below 'BUSINESS DOCUMENT TO CORRECT:'): \"Acme C0rp. Q3 rep0rt shows revnue of $1,234,S67 for peroid ending Sept 3O, 2O23. Contact: Jhon Doe at (SSS) SSS-S4SS.\"\n" +
                    "CORRECTED OUTPUT (your entire response): \"Acme Corp. Q3 report shows revenue of $1,234,567 for period ending Sept 30, 2023. Contact: John Doe at (555) 555-5455.\"\n" +
                    "\nBUSINESS DOCUMENT TO CORRECT:\n```\n" + text + "\n```" + commonFooter;
            case "academic":
                return commonHeader +
                    "\nDETAILED CORRECTION GUIDELINES FOR ACADEMIC DOCUMENTS:\n" +
                    "4.  PAY EXTREME ATTENTION TO: Citation formats (e.g., APA, MLA, (Author, Year)), references, footnotes, endnotes, technical terms, equations, mathematical notations, and scientific symbols. Ensure these are transcribed with perfect accuracy and formatting.\n" +
                    "5.  Preserve formatting of abstracts, headings, subheadings, and lists.\n" +
                    "\nEXAMPLE:\nINPUT TEXT (below 'ACADEMIC DOCUMENT TO CORRECT:'): \"Resrch by Jnes et a1 (2Ol9) sh0ws that X = Y^2 + Z / (n-1). The p-va1ue was < O.O1.\"\n" +
                    "CORRECTED OUTPUT (your entire response): \"Research by Jones et al. (2019) shows that X = Y^2 + Z / (n-1). The p-value was < 0.01.\"\n" +
                    "\nACADEMIC DOCUMENT TO CORRECT:\n```\n" + text + "\n```" + commonFooter;
            case "technical":
                return commonHeader +
                    "\nDETAILED CORRECTION GUIDELINES FOR TECHNICAL DOCUMENTS:\n" +
                    "4.  PAY EXTREME ATTENTION TO:\n    - Code snippets (preserve indentation, syntax, special characters like ;, {}, (), []).\n    - Technical terms, acronyms, and jargon specific to the domain.\n    - Mathematical equations, formulas, and symbols.\n    - Units of measurement (e.g., kg, m/s, °C).\n    - Part numbers, version numbers, and model identifiers.\n" +
                    "5.  Preserve formatting of diagrams (if text within them), tables, and technical specifications.\n" +
                    "\nEXAMPLE:\nINPUT TEXT (below 'TECHNICAL DOCUMENT TO CORRECT:'): \"The `calculate_sum` funct1on takes two 1ntegers (a, b) and retums their sum. See Fig. 3.1 for details. Max V0ltage: S.SV\"\n" +
                    "CORRECTED OUTPUT (your entire response): \"The `calculate_sum` function takes two integers (a, b) and returns their sum. See Fig. 3.1 for details. Max Voltage: 5.5V\"\n" +
                    "\nTECHNICAL DOCUMENT TO CORRECT:\n```\n" + text + "\n```" + commonFooter;
            case "legal":
                return commonHeader +
                    "\nDETAILED CORRECTION GUIDELINES FOR LEGAL DOCUMENTS:\n" +
                    "4.  PAY EXTREME ATTENTION TO:\n    - Legal terminology (e.g., \"heretofore\", \"res ipsa loquitur\", \"inter alia\").\n    - Case citations (e.g., Smith v. Jones, 123 U.S. 456 (2023)).\n    - Statutes and section numbers (e.g., 28 U.S.C. § 1331).\n    - Names of parties, courts, judges, and legal authorities.\n    - Dates, monetary amounts, and specific clauses.\n" +
                    "5.  Preserve formatting of numbered/lettered paragraphs, indentations, and block quotes.\n" +
                    "\nEXAMPLE:\nINPUT TEXT (below 'LEGAL DOCUMENT TO CORRECT:'): \"Pursu@nt to Sectoin 1O(b) of the Act, the Plaint1ff alleges fraud. See also, Roe v. Wade, 4lO U.S. ll3 (l973).\"\n" +
                    "CORRECTED OUTPUT (your entire response): \"Pursuant to Section 10(b) of the Act, the Plaintiff alleges fraud. See also, Roe v. Wade, 410 U.S. 113 (1973).\"\n" +
                    "\nLEGAL DOCUMENT TO CORRECT:\n```\n" + text + "\n```" + commonFooter;
            case "italian-literary":
                return "IMPORTANT: Your entire response MUST be ONLY the corrected Italian text. No preambles, no explanations, no apologies, no conversational filler. Start directly with the corrected Italian text. The text to correct is provided below the line 'ITALIAN LITERARY TEXT TO CORRECT:'.\n\n" +
                    "You are a meticulous and highly accurate OCR error correction engine, specializing in ITALIAN LITERARY TEXTS. Your SOLE AND ONLY task is to identify and fix OCR errors in the provided scanned Italian text, PRESERVING IT IN ITALIAN.\n\n" +
                    "CRITICAL INSTRUCTIONS:\n- ABSOLUTELY DO NOT TRANSLATE any part of the text into English or any other language. The output MUST remain in Italian.\n- ABSOLUTELY DO NOT analyze, interpret, summarize, rephrase, or explain the text content in any way.\n- ABSOLUTELY DO NOT add any new information, opinions, or interpretations.\n- ABSOLUTELY DO NOT add any introductory phrases (like \"Certo, ecco il testo:\", \"Ho capito...\", \"Va bene...\"), concluding remarks, or any text other than the corrected Italian OCR output.\n- Your output MUST be ONLY the corrected version of the input text, IN ITALIAN.\n- DO NOT ask for the text; it is provided below.\n\n" +
                    "DETAILED CORRECTION GUIDELINES FOR ITALIAN LITERARY TEXTS:\n" +
                    "1.  Correct spelling and grammar mistakes that are clearly OCR errors in Italian (e.g., \"perche\" -> \"perché\", \"un pò\" -> \"un po'\").\n" +
                    "2.  Fix word segmentation problems (e.g., \"ilsogno\" -> \"il sogno\", \"unaltravita\" -> \"un'altra vita\"). Be very careful with elisions and apostrophes (e.g. \"l'anima\", \"un'ora\", \"dall'alto\").\n" +
                    "3.  Restore correct Italian punctuation (including accents like à, è, ì, ò, ù) and capitalization. PAY EXTREME ATTENTION to apostrophes and accents, as these are critical in Italian and often mangled by OCR. For example, \"E una bella giornata\" should be \"È una bella giornata\". \"Citta\" should be \"Città\".\n" +
                    "4.  Preserve artistic or stylistic choices in the original Italian text (e.g., unusual formatting, dialects if present, poetic line breaks).\n" +
                    "5.  Meticulously preserve the original paragraph structure, line breaks, indentation, and dialogue formatting.\n" +
                    "6.  DO NOT change sentence structure or word order unless it's a clear and unambiguous OCR error causing nonsensical phrasing in Italian.\n\n" +
                    "EXAMPLE:\nINPUT TEXT (below 'ITALIAN LITERARY TEXT TO CORRECT:'): \"Nel mezzo del camin di nostra vita mi ritrvai per una selva oscura, che la diritta via era smarita. Ah quanto a dir qual era e cosa dura esta selva selvaggia...\"\n" +
                    "CORRECTED OUTPUT (your entire response): \"Nel mezzo del cammin di nostra vita mi ritrovai per una selva oscura, ché la diritta via era smarrita. Ahi quanto a dir qual era è cosa dura esta selva selvaggia...\"\n\n" +
                    "ITALIAN LITERARY TEXT TO CORRECT:\n```\n" + text + "\n```\nRemember: ONLY the corrected Italian text. Nothing else.";
            case "literary":
                return commonHeader +
                    "\nDETAILED CORRECTION GUIDELINES FOR LITERARY TEXTS:\n" +
                    "4.  Preserve artistic or stylistic choices in the original text (e.g., unusual formatting, dialects if discernible, poetic line breaks, intentional misspellings if clearly part of the author's style).\n" +
                    "5.  Meticulously preserve the original paragraph structure, line breaks, indentation, and dialogue formatting (e.g., quotation marks, new lines for new speakers).\n" +
                    "\nEXAMPLE OF CORRECTION:\n" + // Changed "INPUT TEXT" to "EXAMPLE OF CORRECTION"
                    "If the provided text was: \"It was a dark and st0rmy n1ght; the rain fell in t0rrents—except at 0ccasional intervals, when it was checkd by a vi0lent gust 0f wind...\"\n" +
                    "Your corrected output should be: \"It was a dark and stormy night; the rain fell in torrents—except at occasional intervals, when it was checked by a violent gust of wind...\"\n" +
                    "\n--- ACTUAL TEXT FOR YOUR CORRECTION BELOW ---\n" + // More distinct marker
                    "```\n" +
                    text +
                    "\n```\n" +
                    commonFooter;
            default: // generic
                 return commonHeader +
                    "\nEXAMPLE:\nINPUT TEXT (below 'TEXT TO CORRECT:'): \"Thc qu1ck brOwn f0x jumpS ov3r the l@zy dog. It was a br1ght day.\"\n" +
                    "CORRECTED OUTPUT (your entire response): \"The quick brown fox jumps over the lazy dog. It was a bright day.\"\n" +
                    "\nTEXT TO CORRECT:\n```\n" + text + "\n```" + commonFooter;
        }
    }
    
    public LlmResponseResult detectAndFixProblematicResponse(String originalInputText, String llmResponse, String modelName) {
        String currentResponse = llmResponse;
        boolean fixApplied = false;
        boolean wasInitiallyEchoingPrompt = false; 

        // Attempt to strip prompt echoes first
        String[] promptEchoMarkers = {
            "TEXT TO CORRECT:", "BUSINESS DOCUMENT TO CORRECT:", "ACADEMIC DOCUMENT TO CORRECT:", 
            "TECHNICAL DOCUMENT TO CORRECT:", "LEGAL DOCUMENT TO CORRECT:", 
            "ITALIAN LITERARY TEXT TO CORRECT:", "LITERARY TEXT TO CORRECT:",
            "CORRECTED OUTPUT:", "INPUT TEXT:" 
        };

        for (String marker : promptEchoMarkers) {
            int markerIndex = currentResponse.toUpperCase().indexOf(marker.toUpperCase());
            if (markerIndex != -1) {
                String partAfterMarker = currentResponse.substring(markerIndex + marker.length());
                partAfterMarker = partAfterMarker.replaceAll("^\\s*`{0,3}\\s*", "").replaceAll("\\s*`{0,3}\\s*$", "").trim();
                
                if (!partAfterMarker.isEmpty() && !partAfterMarker.equals(currentResponse)) {
                    logger.info("Stripped echoed prompt marker '{}'. Original response length: {}, Cleaned response candidate length: {}", marker, currentResponse.length(), partAfterMarker.length());
                    currentResponse = partAfterMarker;
                    wasInitiallyEchoingPrompt = true; // Set the flag correctly
                    fixApplied = true; 
                    break; 
                }
            }
        }
        
        boolean isLikelySummary = currentResponse.length() < originalInputText.length() * 0.8 && currentResponse.length() > 0 && originalInputText.length() > 0;
        
        String lowerCaseResponse = currentResponse.toLowerCase();
        boolean containsProblematicLanguage = 
            lowerCaseResponse.contains("this text") ||
            lowerCaseResponse.contains("the passage") ||
            lowerCaseResponse.contains("this appears to be") ||
            lowerCaseResponse.contains("this seems to be") ||
            lowerCaseResponse.contains("the document") ||
            lowerCaseResponse.contains("the author") ||
            lowerCaseResponse.contains("the text is about") ||
            lowerCaseResponse.contains("it appears that") ||
            lowerCaseResponse.contains("in this text") ||
            lowerCaseResponse.contains("in summary") ||
            lowerCaseResponse.contains("overall,") ||
            lowerCaseResponse.startsWith("i've corrected") ||
            lowerCaseResponse.startsWith("here is the corrected") ||
            lowerCaseResponse.startsWith("here's the corrected") ||
            lowerCaseResponse.startsWith("i understand") ||
            lowerCaseResponse.startsWith("okay, here is") ||
            lowerCaseResponse.startsWith("certainly") ||
            lowerCaseResponse.contains("please provide the input text") ||
            lowerCaseResponse.startsWith("i will follow your instructions");
            
        boolean appearsTranslated = false;
        boolean likelyItalianOriginal = originalInputText.toLowerCase().matches(".*\\b(della|sono|una|questo|nella|degli|degli|alla)\\b.*");
        boolean responseMostlyEnglish = lowerCaseResponse.matches(".*\\b(the|and|this|is|was)\\b.*") && 
                                       !lowerCaseResponse.matches(".*\\b(della|sono|una|questo|nella|degli|degli|alla)\\b.*");
        
        if (likelyItalianOriginal && responseMostlyEnglish) {
            appearsTranslated = true;
            logger.warn("Detected problematic response that appears to be a translation from Italian for model {}", modelName);
        }
            
        if (isLikelySummary || containsProblematicLanguage || appearsTranslated || (wasInitiallyEchoingPrompt && currentResponse.isEmpty())) {
            logger.warn("Detected problematic LLM response for model {}. Initial Echo Strip Applied: {}, Likely Summary: {}, Problematic Language: {}, Appears Translated: {}. Attempting fix prompt.", 
                       modelName, fixApplied, isLikelySummary, containsProblematicLanguage, appearsTranslated);
            
            String fixPromptText;
            if (appearsTranslated && likelyItalianOriginal) {
                fixPromptText = """
                    CRITICAL ERROR: You have TRANSLATED the text rather than correcting OCR errors. This is wrong.
                    The original text is in ITALIAN and MUST remain in ITALIAN.
                    Your ONLY task is to fix OCR errors (typos, run-together words, missing spaces, incorrect accents/apostrophes).
                    DO NOT translate. DO NOT explain. DO NOT add any preamble.
                    Output ONLY the corrected ITALIAN text.
                    Original Italian OCR text to correct:
                    ```
                    %s
                    ```
                    Corrected Italian text ONLY:
                    """.formatted(originalInputText);
            } else {
                fixPromptText = """
                    You FAILED the previous instruction. You provided analysis, summary, or explanation instead of ONLY the corrected text.
                    YOUR ONLY TASK IS TO CORRECT OCR ERRORS IN THE PROVIDED TEXT.
                    CRITICAL RULES:
                    1. OUTPUT ONLY THE CORRECTED TEXT.
                    2. DO NOT ADD ANY EXTRA WORDS, PHRASES, EXPLANATIONS, OR INTRODUCTIONS.
                    3. DO NOT SUMMARIZE. DO NOT ANALYZE.
                    4. FOCUS SOLELY ON FIXING TYPOS, WORD SEGMENTATION, AND OCR-RELATED MISTAKES.
                    Original OCR text that needs correction:
                    ```
                    %s
                    ```
                    Provide ONLY the corrected version of the above text:
                    """.formatted(originalInputText);
            }
                
            try {
                UserMessage fixUserMessage = new UserMessage(fixPromptText);
                Prompt fixSystemPrompt = new Prompt(fixUserMessage);
                String fixedResponse = chatClient.call(fixSystemPrompt).getResult().getOutput().getContent();
                
                String finalFixedResponse = fixedResponse.replaceAll("^\\s*`{0,3}\\s*", "").replaceAll("\\s*`{0,3}\\s*$", "").trim();

                logger.info("Successfully attempted to fix problematic LLM response for model {}. Original problematic: '{}', Fixed attempt: '{}'", modelName, currentResponse, finalFixedResponse);
                // Even the fixed response might have a preamble if the LLM is stubborn
                finalFixedResponse = finalFixedResponse.replaceAll("(?i)^CORRECTED OUTPUT:\\s*\\n?", "").trim();
                return new LlmResponseResult(finalFixedResponse, true);
            } catch (Exception e) {
                logger.error("Error during attempt to fix problematic LLM response for model {}: {}", modelName, e.getMessage(), e);
                // Fall back to current (possibly initially cleaned) response if fix fails
                currentResponse = currentResponse.replaceAll("(?i)^CORRECTED OUTPUT:\\s*\\n?", "").trim();
                return new LlmResponseResult(currentResponse, fixApplied);
            }
        }
        
        // If no major issues triggered the fix prompt, but initial stripping might have occurred,
        // ensure "CORRECTED OUTPUT:" is removed from the version we return.
        currentResponse = currentResponse.replaceAll("(?i)^CORRECTED OUTPUT:\\s*\\n?", "").trim();
        return new LlmResponseResult(currentResponse, fixApplied);
    }
    
    public static class LlmResponseResult {
        private final String text;
        private final boolean wasFixed;
        public LlmResponseResult(String text, boolean wasFixed) {
            this.text = text;
            this.wasFixed = wasFixed;
        }
        public String getText() { return text; }
        public boolean wasFixed() { return wasFixed; }
    }
}
