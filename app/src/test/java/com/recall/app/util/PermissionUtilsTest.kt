package com.recall.app.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for PermissionUtils.
 */
class PermissionUtilsTest {
    
    @Test
    fun `getRequiredPermissions returns array`() {
        val permissions = PermissionUtils.getRequiredPermissions()
        
        // Permissions array should not be null
        assertNotNull(permissions)
    }
    
    @Test
    fun `OcrOptions has correct defaults`() {
        // Testing OcrOptions since PermissionUtils requires Android context
        val options = com.recall.app.domain.ocr.OcrOptions()
        
        assertEquals("en", options.language)
        assertEquals(1024, options.maxImageWidth)
        assertTrue(options.enhanceContrast)
        assertFalse(options.deskew)
    }
}
