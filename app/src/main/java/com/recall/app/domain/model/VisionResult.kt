package com.recall.app.domain.model

/**
 * Domain model representing vision model analysis result.
 */
data class VisionResult(
    val caption: String,
    val confidence: Float,
    val tags: List<String>,
    val objects: List<DetectedObject>,
    val scene: String?,
    val colors: List<String>,
    val processingTimeMs: Long,
    val modelUsed: String
) {
    val isEmpty: Boolean = caption.isBlank() && tags.isEmpty()
}

/**
 * Represents a detected object in the image.
 */
data class DetectedObject(
    val name: String,
    val confidence: Float,
    val boundingBox: BoundingBox?
)

/**
 * Screenshot context combining OCR and vision results.
 */
data class ScreenshotContext(
    val ocrText: String?,
    val visionCaption: String?,
    val visionTags: List<String>,
    val detectedObjects: List<DetectedObject>,
    val scene: String?,
    val appType: String?,
    val contentType: ContentType
) {
    /**
     * Generate a unified summary from OCR and vision results.
     */
    fun generateSummary(): String {
        val parts = mutableListOf<String>()
        
        // Add vision caption if available
        visionCaption?.let { parts.add(it) }
        
        // Add app type if detected
        appType?.let { parts.add("App: $it") }
        
        // Add content type
        parts.add("Type: ${contentType.displayName}")
        
        // Add scene context
        scene?.let { parts.add("Context: $it") }
        
        return parts.joinToString(". ")
    }
    
    /**
     * Generate combined tags from all sources.
     */
    fun generateTags(): List<String> {
        val allTags = mutableSetOf<String>()
        
        // Add vision tags
        allTags.addAll(visionTags)
        
        // Add object-based tags
        detectedObjects.forEach { obj ->
            allTags.add(obj.name.lowercase().replace(" ", "_"))
        }
        
        // Add content type tag
        allTags.add(contentType.name.lowercase())
        
        // Add app type tag
        appType?.let { allTags.add(it.lowercase().replace(" ", "_")) }
        
        return allTags.toList()
    }
}

/**
 * Type of screenshot content.
 */
enum class ContentType(val displayName: String) {
    SOCIAL_MEDIA("Social Media Post"),
    CODE_SNIPPET("Code Snippet"),
    WEB_PAGE("Web Page"),
    PRODUCT_LISTING("Product Listing"),
    MESSAGE_CHAT("Message/Chat"),
    EMAIL("Email"),
    MAP_LOCATION("Map/Location"),
    BOOKING_CONFIRMATION("Booking Confirmation"),
    RECIPE("Recipe"),
    NEWS_ARTICLE("News Article"),
    DOCUMENT("Document"),
    SETTINGS_SCREEN("Settings Screen"),
    GAME("Game"),
    VIDEO_PLAYER("Video Player"),
    MUSIC_PLAYER("Music Player"),
    SHOPPING_APP("Shopping App"),
    FINANCE_APP("Finance App"),
    UNKNOWN("Unknown")
}
