package com.recall.app.domain.model

data class AIModel(
    val id: String,
    val name: String,
    val description: String,
    val size: String,
    val iconType: ModelIconType,
    val status: ModelStatus,
    val isRequired: Boolean = false,
    val isRecommended: Boolean = false,
    val warning: String? = null,
    val downloadProgress: Int? = null // 0-100, null if not downloading
)

enum class ModelIconType {
    HUB,           // Embedding models
    PSYCHOLOGY,    // LLM models
    MODEL_TRAINING, // Training models
    TOKEN,         // Token models
    AUTO_AWESOME   // Smart features
}

enum class ModelStatus {
    RUNNING,
    READY,
    DOWNLOADING,
    INACTIVE,
    INCOMPATIBLE,
    AVAILABLE
}

data class SystemStatus(
    val isFloatingCaptureEnabled: Boolean = true,
    val npuUsage: Int = 14,
    val cpuUsage: Int = 23,
    val deviceHealth: DeviceHealth = DeviceHealth.OPTIMIZED
)

enum class DeviceHealth {
    OPTIMIZED,
    WARNING,
    CRITICAL
}
