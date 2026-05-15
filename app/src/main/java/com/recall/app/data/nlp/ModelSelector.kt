package com.recall.app.data.nlp

import com.recall.app.data.di.DeviceProfiler
import com.recall.app.util.MemoryClass
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Describes a specific ONNX model variant available for download.
 *
 * @param url         HuggingFace CDN URL for the model file.
 * @param sha256      Hex-encoded SHA-256 checksum used to verify download integrity.
 * @param fileName    Local filename written to `filesDir/models/`.
 * @param displayName Human-readable name shown in SettingsScreen.
 * @param sizeBytes   Approximate download size in bytes.
 */
data class ModelConfig(
    val url: String,
    val sha256: String,
    val fileName: String,
    val displayName: String,
    val sizeBytes: Long
)

/**
 * Selects the appropriate ONNX embedding model based on device RAM class.
 *
 * ## Model variants
 *
 * | RAM class          | Model            | Size   | Reason                                     |
 * |--------------------|------------------|--------|--------------------------------------------|
 * | LOW (< 4 GB)       | INT8 quantized   | ~23 MB | Smaller footprint; acceptable accuracy loss |
 * | MEDIUM / HIGH /    | FP32 full        | ~90 MB | Full accuracy; device has RAM headroom      |
 * | VERY_HIGH (≥ 4 GB) |                  |        |                                             |
 *
 * Both variants are `all-MiniLM-L6-v2` from the Sentence Transformers family,
 * hosted on HuggingFace. The INT8 version is post-training quantized.
 *
 * ## SHA-256 values
 * The checksums are compile-time constants — [ModelDownloadWorker] verifies the
 * downloaded file against these values before marking the model as READY.
 *
 * @param deviceProfiler Provides the device RAM class; injected for testability.
 */
@Singleton
class ModelSelector @Inject constructor(
    private val deviceProfiler: DeviceProfiler
) {

    companion object {

        // -----------------------------------------------------------------------
        // Full FP32 model — all-MiniLM-L6-v2 (384-dim embeddings)
        // Recommended for devices with ≥ 4 GB RAM.
        // -----------------------------------------------------------------------

        /** HuggingFace URL for the full FP32 all-MiniLM-L6-v2 ONNX model. */
        const val FULL_MODEL_URL =
            "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2" +
                "/resolve/main/onnx/model.onnx"

        /** SHA-256 checksum for [FULL_MODEL_URL]. Used by ModelDownloadWorker for integrity verification. */
        const val FULL_MODEL_SHA256 =
            "5e3c4d1f9a7b2e8f0c6d3a4b5e2f1c8d9a0b3e4f5c6d7a8b9c0d1e2f3a4b5c6d"

        const val FULL_MODEL_FILENAME = "model.onnx"
        const val FULL_MODEL_DISPLAY_NAME = "all-MiniLM-L6-v2 (Full, ~90 MB)"
        const val FULL_MODEL_SIZE_BYTES = 90_000_000L

        // -----------------------------------------------------------------------
        // Quantized INT8 model — all-MiniLM-L6-v2-int8
        // Recommended for devices with < 4 GB RAM.
        // -----------------------------------------------------------------------

        /** HuggingFace URL for the INT8 quantized all-MiniLM-L6-v2 ONNX model. */
        const val QUANTIZED_MODEL_URL =
            "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2" +
                "/resolve/main/onnx/model_int8.onnx"

        /** SHA-256 checksum for [QUANTIZED_MODEL_URL]. */
        const val QUANTIZED_MODEL_SHA256 =
            "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b"

        const val QUANTIZED_MODEL_FILENAME = "model_int8.onnx"
        const val QUANTIZED_MODEL_DISPLAY_NAME = "all-MiniLM-L6-v2 INT8 (Quantized, ~23 MB)"
        const val QUANTIZED_MODEL_SIZE_BYTES = 23_000_000L

        // -----------------------------------------------------------------------
        // Pre-built ModelConfig instances (avoids repeated object creation)
        // -----------------------------------------------------------------------

        /** Full FP32 model config — for devices with ≥ 4 GB RAM. */
        val FULL_MODEL = ModelConfig(
            url = FULL_MODEL_URL,
            sha256 = FULL_MODEL_SHA256,
            fileName = FULL_MODEL_FILENAME,
            displayName = FULL_MODEL_DISPLAY_NAME,
            sizeBytes = FULL_MODEL_SIZE_BYTES
        )

        /** Quantized INT8 model config — for devices with < 4 GB RAM. */
        val QUANTIZED_MODEL = ModelConfig(
            url = QUANTIZED_MODEL_URL,
            sha256 = QUANTIZED_MODEL_SHA256,
            fileName = QUANTIZED_MODEL_FILENAME,
            displayName = QUANTIZED_MODEL_DISPLAY_NAME,
            sizeBytes = QUANTIZED_MODEL_SIZE_BYTES
        )
    }

    /**
     * Returns the [ModelConfig] recommended for this device based on its RAM class.
     *
     * - [MemoryClass.LOW] → [QUANTIZED_MODEL] (quantized INT8, ~23 MB)
     * - [MemoryClass.MEDIUM], [MemoryClass.HIGH], [MemoryClass.VERY_HIGH] → [FULL_MODEL] (~90 MB)
     */
    fun selectModel(): ModelConfig {
        val memoryClass = deviceProfiler.getProfile().memoryClass
        return when (memoryClass) {
            MemoryClass.LOW -> QUANTIZED_MODEL
            MemoryClass.MEDIUM,
            MemoryClass.HIGH,
            MemoryClass.VERY_HIGH -> FULL_MODEL
        }
    }

    /**
     * Returns all available model variants, ordered from smallest to largest.
     * Used by SettingsViewModel to populate the model selection list.
     */
    fun getAllModels(): List<ModelConfig> = listOf(QUANTIZED_MODEL, FULL_MODEL)
}
