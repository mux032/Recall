package com.recall.app.data.di

import android.content.Context
import android.os.Build
import android.util.Log
import com.recall.app.util.MemoryClass
import com.recall.app.util.MemoryInfoHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A snapshot of the device's hardware capabilities relevant to AI model selection.
 *
 * @param totalRamBytes    Total physical RAM in bytes (from [MemoryInfoHelper.getTotalMemory]).
 * @param availableCores   Number of logical CPU cores available to the JVM.
 * @param supportedAbis    Ordered list of supported ABIs, e.g. `["arm64-v8a", "armeabi-v7a"]`.
 *                         The first entry is the device's preferred ABI.
 * @param memoryClass      RAM class bucket used by [ModelSelector] to pick the right model variant.
 */
data class DeviceProfile(
    val totalRamBytes: Long,
    val availableCores: Int,
    val supportedAbis: List<String>,
    val memoryClass: MemoryClass
)

/**
 * Detects device hardware capabilities required by Phase 7 model selection.
 *
 * This is the **foundation** of the Phase 7 pipeline:
 * ```
 * DeviceProfiler → ModelSelector → ModelRepository → ModelDownloadWorker
 *                                                   → OnnxEmbeddingGenerator
 * ```
 *
 * All fields are read lazily and cached — [getProfile] is cheap to call repeatedly.
 *
 * @param context          Application context (Hilt-injected).
 * @param memoryInfoHelper Provides total RAM and memory class; injected for testability.
 */
@Singleton
class DeviceProfiler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryInfoHelper: MemoryInfoHelper
) {

    companion object {
        private const val TAG = "DeviceProfiler"
    }

    /**
     * Cached profile — computed once on first call, reused thereafter.
     * Using [lazy] with the default [LazyThreadSafetyMode.SYNCHRONIZED] guarantees
     * the profile is computed at most once even under concurrent access.
     */
    private val _profile: DeviceProfile by lazy { buildProfile() }

    /**
     * Returns a [DeviceProfile] describing the device's hardware capabilities.
     *
     * The result is cached after the first call — safe and cheap to invoke repeatedly.
     */
    fun getProfile(): DeviceProfile = _profile

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun buildProfile(): DeviceProfile {
        val totalRam = memoryInfoHelper.getTotalMemory()
        val memoryClass = memoryInfoHelper.getMemoryClass()
        val cores = Runtime.getRuntime().availableProcessors()
        @Suppress("DEPRECATION")
        val abis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.toList()
        } else {
            listOf(Build.CPU_ABI, Build.CPU_ABI2).filter { it.isNotBlank() }
        }

        val profile = DeviceProfile(
            totalRamBytes = totalRam,
            availableCores = cores,
            supportedAbis = abis,
            memoryClass = memoryClass
        )

        Log.i(
            TAG,
            "DeviceProfile: RAM=${totalRam / (1024 * 1024)}MB, " +
                "class=$memoryClass, cores=$cores, ABIs=${abis.take(2)}"
        )

        return profile
    }
}
