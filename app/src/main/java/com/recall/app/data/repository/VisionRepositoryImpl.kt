package com.recall.app.data.repository

import com.recall.app.domain.model.ContentType
import com.recall.app.domain.model.ScreenshotContext
import com.recall.app.domain.model.VisionResult
import com.recall.app.domain.repository.VisionRepository
import com.recall.app.domain.vision.VisionOptions
import com.recall.app.domain.vision.VisionProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of vision repository.
 */
@Singleton
class VisionRepositoryImpl @Inject constructor(
    private val visionProcessor: VisionProcessor
) : VisionRepository {
    
    override suspend fun analyzeImage(imageFile: File): VisionResult = withContext(Dispatchers.IO) {
        // Validate file
        if (!imageFile.exists()) {
            throw IllegalArgumentException("Image file does not exist: ${imageFile.absolutePath}")
        }
        
        // Check file size (limit to 10MB)
        val maxSize = 10 * 1024 * 1024 // 10MB
        if (imageFile.length() > maxSize) {
            throw IllegalArgumentException("Image file too large: ${imageFile.length()} bytes")
        }
        
        // Process with vision model
        val options = VisionOptions(
            maxImageSize = 384,
            detectObjects = true,
            detectScene = true,
            detectColors = false,
            topKTags = 10
        )
        
        visionProcessor.analyzeImage(imageFile, options)
    }
    
    override suspend fun analyzeScreenshot(
        imageFile: File,
        ocrText: String?
    ): ScreenshotContext = withContext(Dispatchers.IO) {
        // Get vision results
        val visionResult = analyzeImage(imageFile)
        
        // Detect content type from OCR and vision
        val contentType = detectContentType(ocrText, visionResult)
        
        // Detect app type from content
        val appType = detectAppType(ocrText, visionResult)
        
        ScreenshotContext(
            ocrText = ocrText,
            visionCaption = visionResult.caption,
            visionTags = visionResult.tags,
            detectedObjects = visionResult.objects,
            scene = visionResult.scene,
            appType = appType,
            contentType = contentType
        )
    }
    
    /**
     * Detect content type from OCR and vision results.
     */
    private fun detectContentType(
        ocrText: String?,
        visionResult: VisionResult
    ): ContentType {
        val text = (ocrText ?: "").lowercase()
        val tags = visionResult.tags.map { it.lowercase() }
        
        // Check for code snippets
        if (text.contains("function") || text.contains("class") || 
            text.contains("import ") || text.contains("def ") ||
            text.contains("public ") || text.contains("private ")) {
            return ContentType.CODE_SNIPPET
        }
        
        // Check for social media
        if (text.contains("tweet") || text.contains("retweet") || 
            text.contains("likes") || text.contains("followers") ||
            tags.contains("social_media")) {
            return ContentType.SOCIAL_MEDIA
        }
        
        // Check for messages/chat
        if (text.contains("sent") || text.contains("delivered") || 
            text.contains("message") || text.contains("chat") ||
            text.contains("you:") || text.contains("me:")) {
            return ContentType.MESSAGE_CHAT
        }
        
        // Check for email
        if (text.contains("to:") || text.contains("from:") || 
            text.contains("subject:") || text.contains("@")) {
            return ContentType.EMAIL
        }
        
        // Check for booking/travel
        if (text.contains("booking") || text.contains("confirmation") || 
            text.contains("flight") || text.contains("hotel") ||
            text.contains("reservation")) {
            return ContentType.BOOKING_CONFIRMATION
        }
        
        // Check for shopping
        if (text.contains("add to cart") || text.contains("price") || 
            text.contains("₹") || text.contains("$") ||
            text.contains("buy now")) {
            return ContentType.PRODUCT_LISTING
        }
        
        // Check for recipe
        if (text.contains("ingredients") || text.contains("recipe") || 
            text.contains("cook") || text.contains("minutes")) {
            return ContentType.RECIPE
        }
        
        // Check for web page
        if (text.contains("http") || text.contains("www.") || 
            text.contains(".com") || text.contains("url")) {
            return ContentType.WEB_PAGE
        }
        
        // Check for map/location
        if (text.contains("map") || text.contains("location") || 
            text.contains("directions") || tags.contains("map")) {
            return ContentType.MAP_LOCATION
        }
        
        // Check for settings
        if (text.contains("settings") || text.contains("preferences") || 
            text.contains("toggle") || text.contains("enable")) {
            return ContentType.SETTINGS_SCREEN
        }
        
        // Default
        return ContentType.UNKNOWN
    }
    
    /**
     * Detect app type from content.
     */
    private fun detectAppType(
        ocrText: String?,
        visionResult: VisionResult
    ): String? {
        val text = (ocrText ?: "").lowercase()
        
        // Check for popular apps
        return when {
            text.contains("whatsapp") -> "WhatsApp"
            text.contains("instagram") -> "Instagram"
            text.contains("twitter") || text.contains("tweet") -> "Twitter"
            text.contains("facebook") -> "Facebook"
            text.contains("youtube") -> "YouTube"
            text.contains("gmail") -> "Gmail"
            text.contains("amazon") -> "Amazon"
            text.contains("flipkart") -> "Flipkart"
            text.contains("google maps") || text.contains("maps") -> "Google Maps"
            text.contains("chrome") -> "Chrome Browser"
            text.contains("github") -> "GitHub"
            text.contains("stackoverflow") -> "Stack Overflow"
            text.contains("make my trip") -> "MakeMyTrip"
            text.contains("uber") -> "Uber"
            text.contains("swiggy") -> "Swiggy"
            text.contains("zomato") -> "Zomato"
            text.contains("paytm") -> "Paytm"
            text.contains("phonepe") -> "PhonePe"
            else -> null
        }
    }
    
    override fun isVisionAvailable(): Boolean {
        return visionProcessor.isAvailable()
    }
    
    override fun getModelName(): String {
        return visionProcessor.getModelName()
    }
}
