package com.recall.app.util

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for detecting device memory and calculating optimal cache limits.
 *
 * Memory Class Classification:
 * - LOW: < 4GB RAM (budget devices)
 * - MEDIUM: 4-8GB RAM (mid-range devices)
 * - HIGH: 8-16GB RAM (high-end devices)
 * - VERY_HIGH: 16GB+ RAM (power user devices)
 *
 * Cache Limit Calculation:
 * - Uses 5-10% of available RAM for vector cache
 * - Each embedding is ~1.5KB (384 floats * 4 bytes)
 * - Example: 100,000 embeddings ≈ 150MB
 */
@Singleton
class MemoryInfoHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "MemoryInfoHelper"

        // Memory thresholds in bytes
        private const val RAM_4GB = 4L * 1024 * 1024 * 1024
        private const val RAM_8GB = 8L * 1024 * 1024 * 1024
        private const val RAM_16GB = 16L * 1024 * 1024 * 1024

        // Cache limits based on memory class
        private const val CACHE_LIMIT_LOW = 50_000      // ~75MB for low RAM devices
        private const val CACHE_LIMIT_MEDIUM = 100_000   // ~150MB for medium RAM devices
        private const val CACHE_LIMIT_HIGH = 200_000     // ~300MB for high RAM devices
        private const val CACHE_LIMIT_VERY_HIGH = 500_000 // ~750MB for very high RAM devices

        // Percentage of RAM to use for cache (5-10%)
        private const val RAM_PERCENTAGE_FOR_CACHE = 0.075 // 7.5% (Double)
    }

    /**
     * Get the total physical RAM of the device in bytes.
     * Uses ActivityManager.MemoryInfo for accurate measurement.
     */
    fun getTotalMemory(): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            // totalMem is available from API 16+
            memoryInfo.totalMem
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total memory", e)
            // Issue #2 Fix: Fallback to safe default (4GB) instead of heap size
            // Runtime.getRuntime().totalMemory() returns heap size, not device RAM
            // Using RAM_4GB as a conservative default for mid-range devices
            RAM_4GB.also {
                Log.w(TAG, "Using fallback total memory: ${formatBytes(it)}")
            }
        }
    }

    /**
     * Get the available (free) RAM of the device in bytes.
     * This is the memory currently not in use by the system or apps.
     */
    fun getAvailableMemory(): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.availMem
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available memory", e)
            Runtime.getRuntime().freeMemory()
        }
    }

    /**
     * Get the memory class of the device based on total RAM.
     * This helps categorize devices for appropriate cache sizing.
     */
    fun getMemoryClass(): MemoryClass {
        val totalRam = getTotalMemory()

        return when {
            totalRam < RAM_4GB -> {
                Log.d(TAG, "Device memory class: LOW (< 4GB, ${formatBytes(totalRam)})")
                MemoryClass.LOW
            }
            totalRam < RAM_8GB -> {
                Log.d(TAG, "Device memory class: MEDIUM (4-8GB, ${formatBytes(totalRam)})")
                MemoryClass.MEDIUM
            }
            totalRam < RAM_16GB -> {
                Log.d(TAG, "Device memory class: HIGH (8-16GB, ${formatBytes(totalRam)})")
                MemoryClass.HIGH
            }
            else -> {
                Log.d(TAG, "Device memory class: VERY_HIGH (16GB+, ${formatBytes(totalRam)})")
                MemoryClass.VERY_HIGH
            }
        }
    }

    /**
     * Calculate the optimal cache limit based on device RAM.
     * Uses a percentage-based approach with sensible defaults.
     *
     * Formula:
     * - Low RAM (<4GB): 50,000 embeddings (~75MB)
     * - Medium RAM (4-8GB): 100,000 embeddings (~150MB)
     * - High RAM (8-16GB): 200,000 embeddings (~300MB)
     * - Very High RAM (16GB+): 500,000 embeddings (~750MB)
     *
     * Issue #2 Fix: Overflow protection applied BEFORE .toInt() cast
     * - Apply .coerceAtMost(Long.MAX_VALUE / 2) to cacheBudgetBytes before division
     * - Then apply .coerceAtMost(Int.MAX_VALUE / 2) after .toInt()
     * This prevents integer overflow for devices with 64GB+ RAM
     *
     * @return Optimal cache limit as number of embeddings
     */
    fun calculateOptimalCacheLimit(): Int {
        val memoryClass = getMemoryClass()
        val totalRam = getTotalMemory()

        // Calculate based on percentage of total RAM
        // Each embedding is approximately 1.5KB (384 floats * 4 bytes)
        val embeddingSizeBytes = 384 * 4 // 1536 bytes per embedding
        
        // Issue #2 Fix: Protect against Long overflow BEFORE cast to Int
        val cacheBudgetBytes = (totalRam * RAM_PERCENTAGE_FOR_CACHE).toLong()
            .coerceAtMost(Long.MAX_VALUE / 2)
        val calculatedLimit = (cacheBudgetBytes / embeddingSizeBytes)
            .toInt()
            .coerceAtMost(Int.MAX_VALUE / 2)  // Protect AFTER cast

        // Apply minimum and maximum bounds based on memory class
        val limit = when (memoryClass) {
            MemoryClass.LOW -> {
                // Conservative limit for low RAM devices
                calculatedLimit.coerceIn(25_000, CACHE_LIMIT_LOW)
            }
            MemoryClass.MEDIUM -> {
                // Balanced limit for medium RAM devices
                calculatedLimit.coerceIn(50_000, CACHE_LIMIT_MEDIUM)
            }
            MemoryClass.HIGH -> {
                // Generous limit for high RAM devices
                calculatedLimit.coerceIn(100_000, CACHE_LIMIT_HIGH)
            }
            MemoryClass.VERY_HIGH -> {
                // High limit for power users (no upper bound)
                calculatedLimit.coerceAtLeast(CACHE_LIMIT_VERY_HIGH)
            }
        }

        Log.i(TAG, "Calculated optimal cache limit: $limit embeddings (~${formatBytes(limit * embeddingSizeBytes.toLong())})")
        return limit
    }

    /**
     * Get the current memory usage of the app's heap.
     * Useful for monitoring and debugging memory pressure.
     */
    fun getHeapMemoryBytes(): HeapMemoryInfo {
        return try {
            val runtime = Runtime.getRuntime()
            val totalHeap = runtime.totalMemory()
            val freeHeap = runtime.freeMemory()
            val usedHeap = totalHeap - freeHeap
            val maxHeap = runtime.maxMemory()

            HeapMemoryInfo(
                usedBytes = usedHeap,
                totalBytes = totalHeap,
                maxBytes = maxHeap,
                freeBytes = freeHeap,
                usagePercent = (usedHeap.toFloat() / maxHeap.toFloat() * 100).toInt()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting heap memory info", e)
            HeapMemoryInfo(0, 0, 0, 0, 0)
        }
    }

    /**
     * Check if the device is under memory pressure.
     * Returns true if the system is running low on memory.
     */
    fun isUnderMemoryPressure(): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            // Consider under pressure if less than 20% of total memory is available
            memoryInfo.availMem < (memoryInfo.totalMem * 0.2)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking memory pressure", e)
            false
        }
    }

    /**
     * Get native heap memory info (for JNI/NDK allocations).
     */
    fun getNativeHeapMemoryBytes(): NativeHeapMemoryInfo {
        return try {
            // Note: Debug.getNativeHeapStats() is not available in all Android versions
            // Using alternative approach with Runtime for native heap estimation
            val runtime = Runtime.getRuntime()
            NativeHeapMemoryInfo(
                allocatedBytes = runtime.totalMemory() - runtime.freeMemory(),
                freeBytes = runtime.freeMemory(),
                sizeBytes = runtime.totalMemory()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting native heap info", e)
            NativeHeapMemoryInfo(0, 0, 0)
        }
    }

    /**
     * Format bytes into human-readable string (KB, MB, GB).
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= RAM_16GB -> String.format("%.2f GB", bytes / RAM_16GB.toDouble())
            bytes >= RAM_8GB -> String.format("%.2f GB", bytes / RAM_8GB.toDouble() * 2)
            bytes >= RAM_4GB -> String.format("%.2f GB", bytes / RAM_4GB.toDouble() * 4)
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024 * 1024).toDouble())
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

/**
 * Enum representing device memory class.
 */
enum class MemoryClass {
    LOW,        // < 4GB RAM
    MEDIUM,     // 4-8GB RAM
    HIGH,       // 8-16GB RAM
    VERY_HIGH   // 16GB+ RAM
}

/**
 * Data class for heap memory information.
 */
data class HeapMemoryInfo(
    val usedBytes: Long,
    val totalBytes: Long,
    val maxBytes: Long,
    val freeBytes: Long,
    val usagePercent: Int
)

/**
 * Data class for native heap memory information.
 */
data class NativeHeapMemoryInfo(
    val allocatedBytes: Long,
    val freeBytes: Long,
    val sizeBytes: Long
)
