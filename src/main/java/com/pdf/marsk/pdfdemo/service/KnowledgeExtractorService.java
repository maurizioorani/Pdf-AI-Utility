package com.pdf.marsk.pdfdemo.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;
import java.io.InputStream; // Added for PDFBox
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map; // Ensure Map is imported
import org.apache.pdfbox.pdmodel.PDDocument; // Added for PDFBox
import org.apache.pdfbox.text.PDFTextStripper; // Added for PDFBox
import java.util.concurrent.ExecutorService; // Added
import java.util.concurrent.Executors; // Added
import java.util.stream.Collectors; // Added for stream operations
import org.springframework.mock.web.MockMultipartFile; // Added for OCR

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service; // Added for @PostConstruct

import com.pdf.marsk.pdfdemo.config.RagConfigurationProperties;

import dev.langchain4j.data.document.Document;
import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocuments;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel; // Added
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;

@Service
public class KnowledgeExtractorService { // Renamed from Easy_RAG_Example
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeExtractorService.class);

    private final ChatModel chatModel;
    // private final String openaiApiKey; // Removed
    private final RagConfigurationProperties ragConfig;
    private final EmbeddingModel embeddingModel;
    private final OcrService ocrService; // Added for OCR support
    private Assistant assistant;
    private List<Document> loadedDocuments = new java.util.ArrayList<>();
    private final ExecutorService executorService; // Added for background tasks

    // Define Assistant interface locally or in a shared package if not existing
    interface Assistant {
        String chat(String message);
    }
    
    public KnowledgeExtractorService(@Value("${spring.ai.ollama.base-url}") String ollamaBaseUrl,
                                     RagConfigurationProperties ragConfig,
                                     EmbeddingModel embeddingModel,
                                     OcrService ocrService) { // Added OcrService
        this.ragConfig = ragConfig;
        this.embeddingModel = embeddingModel;
        this.ocrService = ocrService; // Added OCR service
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ragConfig.getChatModelName()) // Ensure this provides an Ollama model name
                .logRequests(true)
                .logResponses(true)
                .build();
        this.executorService = Executors.newCachedThreadPool(); // Initialize executor
    }

    @PostConstruct
    public void initializeAssistant() {
        logger.info("Initializing RAG Assistant...");

        if (!ragConfig.isEnabled()) {
            logger.warn("RAG is disabled. Assistant will not be initialized.");
            return;
        }

        // Initialize with an empty list of documents. Documents will be added via upload.
        this.loadedDocuments = new java.util.ArrayList<>();
        logger.info("Initialized with an empty document list. Documents will be added via upload on the /rag page.");
        
        // Create an assistant with an initially empty knowledge base.
        this.assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .contentRetriever(createContentRetriever(this.loadedDocuments)) // Will be an empty retriever initially
                .build();
        logger.info("RAG Assistant created and initialized successfully.");
    }

    /**
     * Processes a chat message using the RAG assistant.
     * @param message The user's message.
     * @return The assistant's response.
     */
    public String chat(String message) {
        if (this.assistant == null) {
            logger.error("RAG Assistant is not initialized. Cannot process chat message.");
            if (!ragConfig.isEnabled()) {
                 return "Error: RAG system is disabled. Assistant not available.";
            }
            // Attempt to initialize now if it failed earlier, or if called before PostConstruct (unlikely in normal flow)
            logger.warn("Attempting to initialize RAG assistant on-demand...");
            initializeAssistant();
            if (this.assistant == null) {
                 return "Error: RAG Assistant could not be initialized. Please check logs.";
            }
        }
        logger.info("Processing chat message: '{}'", message);
        try {
            String response = this.assistant.chat(message);
            logger.info("Assistant response: '{}'", response);
            return response;
        } catch (Exception e) {
            logger.error("Error during chat processing with RAG assistant: {}", e.getMessage(), e);
            return "Sorry, I encountered an error while processing your message.";
        }
    }

    /**
     * Adds a new document from its content and filename, then re-initializes the assistant.
     * @param fileName The name of the document.
     * @param content The text content of the document.
     * @param progressTrackingService Service to report progress to.
     * @param taskId The ID for tracking this task.
     * @param pdfInputStream InputStream of the PDF file.
     * @param useOcr Whether to use OCR processing for the PDF.
     * @return The taskId.
     */
    public String addPdfDocumentAndReinitializeAsync(String fileName, InputStream pdfInputStream,
                                                     ProgressTrackingService progressTrackingService, String taskId,
                                                     boolean useOcr) {
        logger.info("Task {}: Asynchronously adding PDF document '{}' to RAG knowledge base. OCR: {}", taskId, fileName, useOcr);

        executorService.submit(() -> {
            List<Document> pageDocuments = new ArrayList<>();
            int totalPages = 0;
            try {
                progressTrackingService.updateTaskProgress(taskId, "PDF Parsing", 10, "Loading PDF document...");
                
                if (useOcr) {
                    // Create a temporary file from the input stream to pass to OCR service
                    File tempFile = File.createTempFile("ocr_rag_", "_" + fileName);
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = pdfInputStream.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                      progressTrackingService.updateTaskProgress(taskId, "OCR Processing", 15, 
                        "Starting OCR processing for scanned PDF...");
                    
                    // Process the PDF with OCR (using English as default language)
                    // Create a MultipartFile from the temp file to use with the public performOcr method
                    org.springframework.web.multipart.MultipartFile multipartFile = 
                        new org.springframework.mock.web.MockMultipartFile(
                            fileName, 
                            fileName,
                            "application/pdf", 
                            Files.readAllBytes(tempFile.toPath())
                        );
                    String ocrText = ocrService.performOcr(multipartFile, "eng", taskId);
                    
                    // Create a single document from the OCR text
                    dev.langchain4j.data.document.Metadata docMetadata = new dev.langchain4j.data.document.Metadata();
                    docMetadata.put("source", fileName);
                    docMetadata.put("original_source", fileName);
                    docMetadata.put("ocr_processed", "true");
                    docMetadata.put("processed_on", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    
                    pageDocuments.add(Document.from(ocrText, docMetadata));
                    
                    // Clean up the temp file
                    tempFile.delete();
                } else {
                    // Standard text extraction for regular PDFs
                    try (PDDocument pdfDoc = PDDocument.load(pdfInputStream)) {
                        PDFTextStripper pdfStripper = new PDFTextStripper();
                        totalPages = pdfDoc.getNumberOfPages();
                        progressTrackingService.updateTaskProgress(taskId, "PDF Parsing", 15, "Found " + totalPages + " pages. Extracting text...");

                        for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                            pdfStripper.setStartPage(pageNum);
                            pdfStripper.setEndPage(pageNum);
                            String pageText = pdfStripper.getText(pdfDoc);

                            dev.langchain4j.data.document.Metadata pageMetadata = new dev.langchain4j.data.document.Metadata();
                            pageMetadata.put("source", fileName + "_page_" + pageNum);
                            pageMetadata.put("original_source", fileName);
                            pageMetadata.put("page_number", String.valueOf(pageNum));
                            pageMetadata.put("processed_on", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                            
                            if (pageText != null && !pageText.trim().isEmpty()) {
                                pageDocuments.add(Document.from(pageText, pageMetadata));
                            } else {
                                logger.warn("Task {}: Page {} of document '{}' contained no extractable text. Skipping.", taskId, pageNum, fileName);
                            }
                            
                            int currentProgress = 15 + (int) (((double) pageNum / totalPages) * 60); // Text extraction: 15% to 75%
                            progressTrackingService.updateTaskProgress(taskId, "Text Extraction", currentProgress, "Processed page " + pageNum + " of " + totalPages);
                        }
                    } // PDDocument is closed here
                }

                progressTrackingService.updateTaskProgress(taskId, "Ingesting Document", 75, "Ingesting " + pageDocuments.size() + " page(s) into embedding store...");
                synchronized (this) { // Synchronize access to loadedDocuments and assistant re-initialization
                    this.loadedDocuments.addAll(pageDocuments); // Add all page documents
                    
                    if (ragConfig.isEnabled()) {
                         this.assistant = AiServices.builder(Assistant.class)
                            .chatModel(chatModel)
                            .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                            .contentRetriever(createContentRetriever(this.loadedDocuments))
                            .build();
                        logger.info("Task {}: RAG Assistant re-initialized with {} total documents ({} pages from {}).", taskId, this.loadedDocuments.size(), pageDocuments.size(), fileName);
                        progressTrackingService.updateTaskProgress(taskId, "Assistant Re-initialized", 95, "RAG assistant updated.");
                    } else {
                        logger.warn("Task {}: RAG is disabled. Assistant not re-initialized after adding document.", taskId);
                    }
                }
                progressTrackingService.completeTask(taskId, true, "Document '" + fileName + "' processed and added (" + pageDocuments.size() + " pages).");
            } catch (Exception e) {
                logger.error("Task {}: Error during asynchronous PDF document addition for '{}': {}", taskId, fileName, e.getMessage(), e);
                progressTrackingService.completeTask(taskId, false, "Failed to add PDF document '" + fileName + "': " + e.getMessage());
            }
        });
        return taskId;
    }

    /**
     * Removes a document by its filename (used as a pseudo-ID here) and re-initializes the assistant.
     * @param filename The filename of the document to remove.
     * @return true if a document was removed, false otherwise.
     */
    public synchronized boolean removeDocumentAndReinitialize(String originalFilenameToDelete) {
        if (originalFilenameToDelete == null) return false;
        
        // We need to remove all page-documents that belong to this original_filename
        boolean removed = this.loadedDocuments.removeIf(doc -> {
            dev.langchain4j.data.document.Metadata metadata = doc.metadata();
            String originalSource = metadata != null ? metadata.getString("original_source") : null;
            return originalFilenameToDelete.equals(originalSource);
        });

        if (removed) {
            logger.info("All pages for document '{}' removed from RAG knowledge base. Re-initializing assistant.", originalFilenameToDelete);
            // Re-initialize the assistant with the updated document list
            if (ragConfig.isEnabled()) {
                 this.assistant = AiServices.builder(Assistant.class)
                    .chatModel(chatModel)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .contentRetriever(createContentRetriever(this.loadedDocuments))
                    .build();
                logger.info("RAG Assistant re-initialized with {} total documents.", this.loadedDocuments.size());
            } else {
                logger.warn("RAG is disabled. Assistant not re-initialized after removing document.");
            }
        } else {
            logger.warn("No pages found for document with original filename '{}' for removal.", originalFilenameToDelete);
        }
        return removed;
    }

    /**
     * Removes all documents from the in-memory store and re-initializes the assistant.
     */
    public synchronized void removeAllDocumentsAndReinitialize() {
        int count = this.loadedDocuments.size();
        this.loadedDocuments.clear();
        logger.info("Removed all {} documents from RAG knowledge base. Re-initializing assistant.", count);
        
        // Re-initialize the assistant with an empty document list
        if (ragConfig.isEnabled()) {
             this.assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .contentRetriever(createContentRetriever(this.loadedDocuments)) // Will be an empty retriever
                .build();
            logger.info("RAG Assistant re-initialized with 0 documents.");
        } else {
            logger.warn("RAG is disabled. Assistant not re-initialized after clearing documents.");
        }
    }

    private ContentRetriever createContentRetriever(List<Document> documents) {
        logger.info("Creating content retriever...");
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        if (!documents.isEmpty()) {
            // Explicitly provide the EmbeddingModel to the ingestor
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .embeddingModel(this.embeddingModel) // Use injected embedding model
                    .embeddingStore(embeddingStore)
                    .build();
            ingestor.ingest(documents);
            logger.info("{} documents ingested into in-memory embedding store.", documents.size());
        } else {
            logger.warn("No documents provided to ingest into the embedding store.");
        }
        // Explicitly provide EmbeddingModel to the retriever
        ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(this.embeddingModel)
                .maxResults(5) // Retrieve more segments for potentially better summarization context
              //.minScore(0.6) // Optionally, set a minimum score to filter less relevant segments
                .build();
        logger.info("Content retriever created from in-memory embedding store.");
        return retriever;
    }

    /**
     * Returns a summary list of currently loaded documents for display.
     * Format: List<Object[]>, where Object[] is [original_filename, original_filename, page_count, first_page_processed_on_date]
     */
    public List<Object[]> getLoadedDocumentSummaries() {
        logger.info("getLoadedDocumentSummaries called. Number of page-documents: {}", (loadedDocuments != null ? loadedDocuments.size() : "null"));
        if (loadedDocuments == null || loadedDocuments.isEmpty()) {
            return new ArrayList<>();
        }

        // Group documents by their "original_source" metadata
        Map<String, List<Document>> groupedByOriginalSource = loadedDocuments.stream()
            .filter(doc -> doc.metadata() != null && doc.metadata().getString("original_source") != null)
            .collect(Collectors.groupingBy(doc -> doc.metadata().getString("original_source")));

        List<Object[]> summaries = new ArrayList<>();
        for (Map.Entry<String, List<Document>> entry : groupedByOriginalSource.entrySet()) {
            String originalFilename = entry.getKey();
            List<Document> pageDocs = entry.getValue();
            int pageCount = pageDocs.size();
            
            LocalDateTime firstProcessedOn = null;
            if (!pageDocs.isEmpty()) {
                // Attempt to get the processed_on date from the first page's metadata
                dev.langchain4j.data.document.Metadata firstPageMetadata = pageDocs.get(0).metadata();
                String processedOnStr = firstPageMetadata != null ? firstPageMetadata.getString("processed_on") : null;
                if (processedOnStr != null) {
                    try {
                        firstProcessedOn = LocalDateTime.parse(processedOnStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    } catch (java.time.format.DateTimeParseException e) {
                        logger.warn("Could not parse 'processed_on' date string: {} for document: {}", processedOnStr, originalFilename, e);
                    }
                }
            }
            
            summaries.add(new Object[]{
                originalFilename,    // ID for deletion (original filename)
                originalFilename,    // Display filename
                pageCount,           // Number of pages/segments
                firstProcessedOn     // Timestamp of first page processing
            });
        }
        
        logger.info("Returning {} aggregated document summaries.", summaries.size());
        return summaries;
    }

    private static Path toPath(String relativeOrAbsolutePath) {
        logger.debug("Attempting to resolve path for: {}", relativeOrAbsolutePath);
        // Try to resolve as a classpath resource first
        URL resourceUrl = KnowledgeExtractorService.class.getClassLoader().getResource(relativeOrAbsolutePath);
        if (resourceUrl != null) {
            try {
                Path path = Paths.get(resourceUrl.toURI());
                logger.info("Resolved '{}' as classpath resource to: {}", relativeOrAbsolutePath, path);
                return path;
            } catch (URISyntaxException e) {
                logger.warn("URISyntaxException for classpath resource '{}': {}. Trying as file path.", relativeOrAbsolutePath, e.getMessage());
            }
        } else {
            logger.info("'{}' not found as classpath resource. Trying as file path.", relativeOrAbsolutePath);
        }

        // Try to resolve as an absolute or relative file path
        Path filePath = Paths.get(relativeOrAbsolutePath);
        if (filePath.toFile().exists()) {
             logger.info("Resolved '{}' as file system path to: {}", relativeOrAbsolutePath, filePath.toAbsolutePath());
            return filePath.toAbsolutePath();
        } else {
            // If it's a relative path, try to resolve it against a known base, e.g., user.dir or a configured base.
            // For simplicity, if not found as classpath or direct file, and it's relative, try from user.dir
            if (!filePath.isAbsolute()) {
                Path currentDir = Paths.get(System.getProperty("user.dir"));
                Path resolvedRelative = currentDir.resolve(relativeOrAbsolutePath);
                if (resolvedRelative.toFile().exists()) {
                    logger.info("Resolved relative path '{}' against current working directory to: {}", relativeOrAbsolutePath, resolvedRelative.toAbsolutePath());
                    return resolvedRelative.toAbsolutePath();
                } else {
                    logger.warn("Relative path '{}' not found in current working directory: {}", relativeOrAbsolutePath, currentDir);
                }
            }
            logger.error("Could not resolve path for '{}'. It was not found in classpath or as a direct/relative file system path. Path used: {}", relativeOrAbsolutePath, filePath.toAbsolutePath());
            // Create the directory if it doesn't exist, assuming it's a directory path for documents.
            // This is a common use case for document loading.
            File dir = filePath.toFile();
            if (!dir.exists()) {
                logger.warn("Directory '{}' does not exist. Attempting to create it.", filePath.toAbsolutePath());
                if (dir.mkdirs()) {
                    logger.info("Successfully created directory: {}", filePath.toAbsolutePath());
                    return filePath.toAbsolutePath();
                } else {
                    logger.error("Failed to create directory: {}. Please ensure the path is correct and permissions are set.", filePath.toAbsolutePath());
                    return null; // Indicate failure to resolve/create path
                }
            }
            return filePath.toAbsolutePath(); // Return the path if it's a directory that now exists or already existed
        }
    }

    private static PathMatcher glob(String globPattern) {
        return FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
    }
}