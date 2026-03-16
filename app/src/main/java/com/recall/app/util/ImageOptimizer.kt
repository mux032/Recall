package com.recall.app.util

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.collection.LruCache

/**
 * Image processing optimizer for efficient screenshot handling.
 */
object ImageOptimizer {
    
    // LRU cache for processed bitmaps
    private val bitmapCache: LruCache<String, Bitmap>
    
    init {
        // Use 1/8th of available memory for bitmap cache
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        bitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }
    
    /**
     * Get optimal sample size for image loading.
     */
    fun calculateInSampleSize(
        imageWidth: Int,
        imageHeight: Int,
        maxWidth: Int = 1024,
        maxHeight: Int = 1024
    ): Int {
        var inSampleSize = 1
        
        if (imageHeight > maxHeight || imageWidth > maxWidth) {
            val halfHeight = imageHeight / 2
            val halfWidth = imageWidth / 2
            
            while (halfHeight / inSampleSize >= maxHeight &&
                   halfWidth / inSampleSize >= maxWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * Resize bitmap to max dimensions while maintaining aspect ratio.
     */
    fun resizeBitmap(bitmap: Bitmap, maxSize: Int = 1024): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }
        
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        
        if (width > height) {
            newWidth = maxSize
            newHeight = (maxSize / ratio).toInt()
        } else {
            newHeight = maxSize
            newWidth = (maxSize * ratio).toInt()
        }
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Rotate bitmap by specified degrees.
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) {
            return bitmap
        }
        
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * Add bitmap to cache.
     */
    fun addToCache(key: String, bitmap: Bitmap) {
        bitmapCache.put(key, bitmap)
    }
    
    /**
     * Get bitmap from cache.
     */
    fun getFromCache(key: String): Bitmap? {
        return bitmapCache.get(key)
    }
    
    /**
     * Remove bitmap from cache.
     */
    fun removeFromCache(key: String) {
        bitmapCache.remove(key)
    }
    
    /**
     * Clear bitmap cache.
     */
    fun clearCache() {
        bitmapCache.evictAll()
    }
    
    /**
     * Get cache size in KB.
     */
    fun getCacheSize(): Int {
        return bitmapCache.size()
    }
}
