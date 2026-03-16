package com.recall.app.util

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import androidx.work.Constraints
import androidx.work.NetworkType
import com.recall.app.data.worker.ScreenshotProcessingWorker

/**
 * Battery optimization utilities for efficient background processing.
 */
object BatteryOptimizer {
    
    private var isCharging = false
    private var batteryLevel = 0
    
    /**
     * Check if device is charging.
     */
    fun isDeviceCharging(context: Context): Boolean {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.isCharging
    }
    
    /**
     * Get current battery level (0-100).
     */
    fun getBatteryLevel(context: Context): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    
    /**
     * Check if battery level is above threshold.
     */
    fun isBatteryAboveThreshold(context: Context, threshold: Int = 20): Boolean {
        return getBatteryLevel(context) > threshold
    }
    
    /**
     * Create constraints for battery-efficient work.
     */
    fun createBatteryEfficientConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(false) // Don't require charging, but check before processing
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
    }
    
    /**
     * Check if we should process screenshots based on battery status.
     */
    fun shouldProcessScreenshots(context: Context): Boolean {
        // Always process if charging
        if (isDeviceCharging(context)) {
            return true
        }
        
        // Only process if battery is above 20%
        return isBatteryAboveThreshold(context, 20)
    }
    
    /**
     * Update battery status.
     */
    fun updateBatteryStatus(context: Context) {
        isCharging = isDeviceCharging(context)
        batteryLevel = getBatteryLevel(context)
    }
    
    /**
     * Get battery status description.
     */
    fun getBatteryStatusDescription(context: Context): String {
        val level = getBatteryLevel(context)
        val charging = isDeviceCharging(context)
        
        return "Battery: $level% ${if (charging) "(Charging)" else ""}"
    }
}
