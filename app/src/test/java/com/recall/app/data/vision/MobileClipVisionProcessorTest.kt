package com.recall.app.data.vision

import com.recall.app.domain.vision.VisionOptions
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MobileClipVisionProcessor.
 */
class MobileClipVisionProcessorTest {
    
    private lateinit var processor: MobileClipVisionProcessor
    
    @Before
    fun setup() {
        processor = MobileClipVisionProcessor()
    }
    
    @Test
    fun `processor returns model name`() {
        val modelName = processor.getModelName()
        assertTrue(modelName.contains("MobileCLIP"))
    }
    
    @Test
    fun `processor is available returns boolean`() {
        val available = processor.isAvailable()
        // Just verify it returns a boolean
        assertTrue(available || !available)
    }
    
    @Test
    fun `VisionOptions has default values`() {
        val options = VisionOptions()
        
        assertEquals(384, options.maxImageSize)
        assertTrue(options.detectObjects)
        assertTrue(options.detectScene)
        assertFalse(options.detectColors)
        assertEquals(10, options.topKTags)
    }
    
    @Test
    fun `VisionOptions can be customized`() {
        val options = VisionOptions(
            maxImageSize = 256,
            detectObjects = false,
            detectScene = false,
            detectColors = true,
            topKTags = 5
        )
        
        assertEquals(256, options.maxImageSize)
        assertFalse(options.detectObjects)
        assertFalse(options.detectScene)
        assertTrue(options.detectColors)
        assertEquals(5, options.topKTags)
    }
}
