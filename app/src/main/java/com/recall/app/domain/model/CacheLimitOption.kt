package com.recall.app.domain.model

/**
 * User-selectable cache limit options for vector cache.
 *
 * These options allow users to override the automatic RAM-based cache limit
 * based on their preferences and device capabilities.
 *
 * @property limit The actual cache limit (number of embeddings)
 * @property displayName User-friendly name for UI display
 * @property description Brief description shown in settings
 * @property estimatedMemoryMb Estimated memory usage for this limit
 */
enum class CacheLimitOption(
    val limit: Int,
    val displayName: String,
    val description: String,
    val estimatedMemoryMb: Int
) {
    /**
     * Automatic mode: Uses RAM-based calculation.
     * Recommended for most users.
     */
    AUTO(
        limit = -1, // -1 indicates auto-calculation
        displayName = "Auto (Recommended)",
        description = "Automatically adjust based on device RAM",
        estimatedMemoryMb = 0 // Calculated at runtime
    ),

    /**
     * Conservative limit for low-RAM devices or users prioritizing memory.
     * Suitable for devices with <4GB RAM.
     */
    CONSERVATIVE(
        limit = 50_000,
        displayName = "Conservative",
        description = "50K embeddings (~75MB RAM)",
        estimatedMemoryMb = 75
    ),

    /**
     * Balanced limit for standard usage.
     * Good for devices with 4-8GB RAM.
     */
    BALANCED(
        limit = 100_000,
        displayName = "Balanced",
        description = "100K embeddings (~150MB RAM)",
        estimatedMemoryMb = 150
    ),

    /**
     * Aggressive limit for high-RAM devices.
     * Good for devices with 8-16GB RAM.
     */
    AGGRESSIVE(
        limit = 500_000,
        displayName = "Aggressive",
        description = "500K embeddings (~750MB RAM)",
        estimatedMemoryMb = 750
    ),

    /**
     * Unlimited cache for power users.
     * Only recommended for devices with 16GB+ RAM.
     */
    UNLIMITED(
        limit = Int.MAX_VALUE,
        displayName = "Unlimited",
        description = "No cache limit (16GB+ RAM recommended)",
        estimatedMemoryMb = 0 // Variable
    );

    companion object {
        /**
         * Get the default option (Auto).
         */
        fun default(): CacheLimitOption = AUTO

        /**
         * Parse from string (for DataStore serialization).
         */
        fun fromString(value: String): CacheLimitOption {
            return try {
                valueOf(value)
            } catch (e: IllegalArgumentException) {
                AUTO // Default fallback
            }
        }

        /**
         * Get option by limit value.
         */
        fun fromLimit(limit: Int): CacheLimitOption {
            return entries.find { it.limit == limit } ?: AUTO
        }

        /**
         * Check if a limit represents unlimited.
         */
        fun isUnlimited(limit: Int): Boolean {
            return limit <= 0 || limit == Int.MAX_VALUE
        }
    }

    /**
     * Check if this option is the auto mode.
     */
    fun isAuto(): Boolean = this == AUTO

    /**
     * Check if this option is unlimited.
     */
    fun isUnlimited(): Boolean = this == UNLIMITED

    /**
     * Get the effective limit, calculating auto if needed.
     * @param autoCalculatedLimit The limit calculated by MemoryInfoHelper
     */
    fun getEffectiveLimit(autoCalculatedLimit: Int): Int {
        return when (this) {
            AUTO -> autoCalculatedLimit
            else -> limit
        }
    }
}
