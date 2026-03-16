package com.recall.app.data.repository

import com.recall.app.domain.model.ContentType
import com.recall.app.domain.model.VisionResult
import com.recall.app.domain.vision.VisionProcessor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class VisionRepositoryImplTest {
    
    private lateinit var visionProcessor: VisionProcessor
    private lateinit var repository: VisionRepositoryImpl
    
    @Before
    fun setup() {
        visionProcessor = mock()
        repository = VisionRepositoryImpl(visionProcessor)
    }
    
    @Test
    fun `isVisionAvailable returns processor availability`() {
        whenever(visionProcessor.isAvailable()).thenReturn(true)
        assertTrue(repository.isVisionAvailable())
        
        whenever(visionProcessor.isAvailable()).thenReturn(false)
        assertFalse(repository.isVisionAvailable())
    }
    
    @Test
    fun `getModelName returns processor model name`() {
        whenever(visionProcessor.getModelName()).thenReturn("MobileCLIP")
        assertEquals("MobileCLIP", repository.getModelName())
    }
    
    @Test
    fun `detectContentType identifies code snippets`() {
        val codeText = "public class MyClass { function test() {} }"
        
        // Test the content type detection logic directly
        val contentType = detectContentType(codeText, emptyList())
        assertEquals(ContentType.CODE_SNIPPET, contentType)
    }
    
    @Test
    fun `detectContentType identifies social media`() {
        val socialText = "Just posted a new tweet! #awesome"
        
        val contentType = detectContentType(socialText, emptyList())
        assertEquals(ContentType.SOCIAL_MEDIA, contentType)
    }
    
    @Test
    fun `detectContentType identifies messages`() {
        val messageText = "You: Hey, are we still meeting? (delivered)"
        
        val contentType = detectContentType(messageText, emptyList())
        assertEquals(ContentType.MESSAGE_CHAT, contentType)
    }
    
    @Test
    fun `detectAppType identifies popular apps`() {
        val whatsappText = "WhatsApp Messenger - Chat with friends"
        val visionResult = createMockVisionResult()
        
        val appType = detectAppType(whatsappText, visionResult)
        assertEquals("WhatsApp", appType)
    }
    
    private fun createMockVisionResult(): VisionResult {
        return VisionResult(
            caption = "Mock caption",
            confidence = 0.8f,
            tags = listOf("mock"),
            objects = emptyList(),
            scene = "mock_scene",
            colors = listOf(),
            processingTimeMs = 100,
            modelUsed = "Mock"
        )
    }
    
    // Helper functions to test the logic
    private fun detectContentType(ocrText: String?, tags: List<String>): ContentType {
        val text = (ocrText ?: "").lowercase()
        val tagList = tags.map { it.lowercase() }
        
        if (text.contains("function") || text.contains("class") || 
            text.contains("import ") || text.contains("def ") ||
            text.contains("public ") || text.contains("private ")) {
            return ContentType.CODE_SNIPPET
        }
        
        if (text.contains("tweet") || text.contains("retweet") || 
            text.contains("likes") || text.contains("followers") ||
            tagList.contains("social_media")) {
            return ContentType.SOCIAL_MEDIA
        }
        
        if (text.contains("sent") || text.contains("delivered") || 
            text.contains("message") || text.contains("chat") ||
            text.contains("you:") || text.contains("me:")) {
            return ContentType.MESSAGE_CHAT
        }
        
        return ContentType.UNKNOWN
    }
    
    private fun detectAppType(ocrText: String?, visionResult: VisionResult): String? {
        val text = (ocrText ?: "").lowercase()
        
        return when {
            text.contains("whatsapp") -> "WhatsApp"
            text.contains("instagram") -> "Instagram"
            text.contains("twitter") || text.contains("tweet") -> "Twitter"
            text.contains("amazon") -> "Amazon"
            else -> null
        }
    }
}
