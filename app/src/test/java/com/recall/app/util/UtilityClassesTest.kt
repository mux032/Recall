package com.recall.app.util

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for utility classes.
 */
class UtilityClassesTest {
    
    @Before
    fun setup() {
        // Setup if needed
    }
    
    @Test
    fun imageOptimizer_calculateInSampleSize_returnsPowerOf2() {
        val sampleSize = ImageOptimizer.calculateInSampleSize(2048, 2048, 1024, 1024)
        assertTrue(sampleSize >= 1)
        assertTrue(sampleSize and (sampleSize - 1) == 0) // Power of 2
    }
    
    @Test
    fun imageOptimizer_calculateInSampleSize_smallImage_returns1() {
        val sampleSize = ImageOptimizer.calculateInSampleSize(512, 512, 1024, 1024)
        assertEquals(1, sampleSize)
    }
    
    @Test
    fun imageOptimizer_cacheOperations() {
        ImageOptimizer.clearCache()
        assertEquals(0, ImageOptimizer.getCacheSize())
    }
    
    @Test
    fun performanceMonitor_memoryUsageReturnsValidValue() {
        val memoryUsage = PerformanceMonitor.getMemoryUsage()
        assertTrue(memoryUsage > 0)
    }
    
    @Test
    fun performanceMonitor_memoryUsageMBReturnsValidValue() {
        val memoryMB = PerformanceMonitor.getMemoryUsageMB()
        assertTrue(memoryMB > 0)
        assertTrue(memoryMB < 1000) // Should be less than 1GB
    }
    
    @Test
    fun performanceMonitor_isMemoryHigh_withLowThreshold() {
        val isHigh = PerformanceMonitor.isMemoryHigh(0.001)
        assertTrue(isHigh)
    }
    
    @Test
    fun cacheManager_getCacheRecommendations() {
        val recommendations = CacheManager.getCacheRecommendations(150)
        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.any { it.contains("large") })
    }
    
    @Test
    fun cacheManager_getCacheRecommendations_smallCache() {
        val recommendations = CacheManager.getCacheRecommendations(10)
        assertTrue(recommendations.isEmpty())
    }
}
