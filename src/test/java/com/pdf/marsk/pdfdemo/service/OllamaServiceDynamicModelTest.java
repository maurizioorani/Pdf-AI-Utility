package com.pdf.marsk.pdfdemo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockConstruction;

/**
 * Test class to verify dynamic model selection functionality in OllamaService
 */
@ExtendWith(MockitoExtension.class)
public class OllamaServiceDynamicModelTest {

    @Mock
    private ChatClient defaultChatClient;
    
    @Mock
    private TextChunkingService textChunkingService;
    
    @Mock
    private OllamaApi ollamaApi;

    @Test
    public void testCreateChatClientForModel_withValidModel() {
        // Arrange
        OllamaService ollamaService = new OllamaService(defaultChatClient, textChunkingService);
        ReflectionTestUtils.setField(ollamaService, "ollamaApi", ollamaApi);
        
        String modelName = "llama3:8b";
        
        // Mock the OllamaChatClient creation using MockedConstruction
        try (MockedConstruction<OllamaChatClient> mockedConstruction = mockConstruction(OllamaChatClient.class, (mock, context) -> {
            lenient().when(mock.withDefaultOptions(any())).thenReturn(mock);
        })) {
            // Get the constructed mock instance (should be only one)
            OllamaChatClient constructedChatClient = mockedConstruction.constructed().get(0);
            
            // Act
            ChatClient result = (ChatClient) ReflectionTestUtils.invokeMethod(
                ollamaService, "createChatClientForModel", modelName);
            
            // Assert
            assertNotNull(result);
            assertEquals(constructedChatClient, result); // Assert that the returned client is our mocked instance
            verify(constructedChatClient).withDefaultOptions(any()); // Verify interactions on the mocked instance
        }
    }

    @Test
    public void testCreateChatClientForModel_withNullModel() {
        // Arrange
        OllamaService ollamaService = new OllamaService(defaultChatClient, textChunkingService);
        
        // Act
        ChatClient result = (ChatClient) ReflectionTestUtils.invokeMethod(
            ollamaService, "createChatClientForModel", (String) null);
        
        // Assert
        assertEquals(defaultChatClient, result);
    }

    @Test
    public void testCreateChatClientForModel_withEmptyModel() {
        // Arrange
        OllamaService ollamaService = new OllamaService(defaultChatClient, textChunkingService);
        
        // Act
        ChatClient result = (ChatClient) ReflectionTestUtils.invokeMethod(
            ollamaService, "createChatClientForModel", "");
        
        // Assert
        assertEquals(defaultChatClient, result);
    }

    @Test
    public void testEnhanceText_usesCorrectModel() {
        // Arrange
        OllamaService ollamaService = new OllamaService(defaultChatClient, textChunkingService);
        ReflectionTestUtils.setField(ollamaService, "ollamaApi", ollamaApi);
        ReflectionTestUtils.setField(ollamaService, "chunkingEnabled", false);
        
        String testText = "Test text to enhance";
        String modelName = "llama3:8b";
        String customPrompt = "Test prompt";
        
        // Mock ChatClient response
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage mockMessage = mock(AssistantMessage.class);
        
        when(mockMessage.getContent()).thenReturn("Enhanced test text");
        when(mockGeneration.getOutput()).thenReturn(mockMessage);
        when(mockResponse.getResults()).thenReturn(java.util.List.of(mockGeneration));
        
        // Mock the OllamaChatClient creation and behavior using MockedConstruction
        try (MockedConstruction<OllamaChatClient> mockedConstruction = mockConstruction(OllamaChatClient.class, (mock, context) -> {
            lenient().when(mock.withDefaultOptions(any())).thenReturn(mock);
            when(mock.call(any(Prompt.class))).thenReturn(mockResponse);
        })) {
            OllamaChatClient constructedChatClient = mockedConstruction.constructed().get(0);
            
            // Act
            OllamaService.EnhancementResult result = ollamaService.enhanceText(testText, modelName, customPrompt);
            
            // Assert
            assertNotNull(result);
            assertEquals("Enhanced test text", result.getEnhancedText());
            verify(constructedChatClient).call(any(Prompt.class));
        }
    }
}
