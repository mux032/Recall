package com.recall.app.domain.model

import java.util.concurrent.TimeUnit

/**
 * User-selectable background indexing interval for [BackgroundOcrWorker].
 *
 * WorkManager enforces a minimum periodic interval of 15 minutes.
 * Values below 60 minutes are flagged as battery-intensive in the UI.
 *
 * @param displayName  Human-readable label shown in the Settings dropdown.
 * @param minutes      Interval in minutes passed to [PeriodicWorkRequestBuilder].
 */
enum class IndexingInterval(val displayName: String, val minutes: Long) {
    EVERY_15_MIN("Every 15 minutes", 15L),
    EVERY_30_MIN("Every 30 minutes", 30L),
    EVERY_1_HOUR("Every hour", 60L),
    EVERY_3_HOURS("Every 3 hours", 180L),
    EVERY_6_HOURS("Every 6 hours", 360L),
    EVERY_12_HOURS("Every 12 hours", 720L);

    val timeUnit: TimeUnit get() = TimeUnit.MINUTES

    /** True when the interval is aggressive enough to noticeably impact battery. */
    val isHighBatteryImpact: Boolean get() = minutes < 60L

    companion object {
        /**
         * Production default: every hour — a good balance between freshness and battery life.
         * 15 min is available for power users but is not selected by default.
         */
        val DEFAULT = EVERY_1_HOUR

        fun fromName(name: String): IndexingInterval =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
