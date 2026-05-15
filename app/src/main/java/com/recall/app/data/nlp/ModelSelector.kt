package com.recall.app.data.nlp

import com.recall.app.data.di.DeviceProfiler
import com.recall.app.util.MemoryClass
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Describes a specific ONNX model variant available for download.
 *
 * @param url         HuggingFace CDN URL for the model file.
 * @param sha256      Hex-encoded SHA-256 checksum used by [ModelDownloadWorker] for integrity verification.
 * @param fileName    Local filename written to `filesDir/models/`.
 * @param displayName Human-readable name shown in SettingsScreen.
 * @param sizeBytes   Exact download size in bytes (from LFS metadata).
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
 * ## Model — BAAI/bge-small-en-v1.5
 *
 * Both variants are ONNX exports of **BAAI/bge-small-en-v1.5** — a retrieval-optimised
 * sentence embedding model that significantly outperforms the previous `all-MiniLM-L6-v2`
 * while using the **same 384 output dimensions**. This means zero changes to
 * `VectorIndexOptimized`, `OnnxEmbeddingGenerator`, or the embedding storage schema.
 *
 * ### Performance comparison (MTEB English benchmark)
 * | Model                     | MTEB Avg | Dims | Size   |
 * |---------------------------|----------|------|--------|
 * | all-MiniLM-L6-v2 (old)    | 56.26    | 384  | ~90 MB |
 * | bge-small-en-v1.5 (full)  | **62.17** | 384 | 133 MB |
 * | bge-small-en-v1.5 (quant) | ~59.x    | 384  | 34 MB  |
 *
 * ### Variants
 * | RAM class          | Variant             | Size    | Source                     |
 * |--------------------|---------------------|---------|----------------------------|
 * | LOW (< 4 GB)       | INT8 quantized ONNX | 34 MB   | Xenova/bge-small-en-v1.5   |
 * | MEDIUM / HIGH /    | Full FP32 ONNX      | 133 MB  | BAAI/bge-small-en-v1.5     |
 * | VERY_HIGH (≥ 4 GB) |                     |         |                            |
 *
 * ## SHA-256 checksums
 * Values are taken directly from the HuggingFace LFS pointer files and are used by
 * [ModelDownloadWorker] to verify download integrity before marking the model as READY.
 *
 * @param deviceProfiler Provides the device RAM class; injected for testability.
 */
@Singleton
class ModelSelector @Inject constructor(
    private val deviceProfiler: DeviceProfiler
) {

    companion object {

        // -----------------------------------------------------------------------
        // Full FP32 model — BAAI/bge-small-en-v1.5
        // Recommended for devices with ≥ 4 GB RAM (MEDIUM / HIGH / VERY_HIGH).
        // MTEB Avg: 62.17 | Dims: 384 | Size: 133 MB
        // -----------------------------------------------------------------------

        /** HuggingFace URL for the full FP32 bge-small-en-v1.5 ONNX model. */
        const val FULL_MODEL_URL =
            "https://huggingface.co/BAAI/bge-small-en-v1.5/resolve/main/onnx/model.onnx"

        /**
         * SHA-256 checksum for [FULL_MODEL_URL].
         * Source: LFS pointer at BAAI/bge-small-en-v1.5/raw/main/onnx/model.onnx
         */
        const val FULL_MODEL_SHA256 =
            "828e1496d7fabb79cfa4dcd84fa38625c0d3d21da474a00f08db0f559940cf35"

        const val FULL_MODEL_FILENAME = "bge-small-en-v1.5.onnx"
        const val FULL_MODEL_DISPLAY_NAME = "bge-small-en-v1.5 (Full FP32, 133 MB)"
        const val FULL_MODEL_SIZE_BYTES = 133_093_490L

        // -----------------------------------------------------------------------
        // Quantized INT8 model — Xenova/bge-small-en-v1.5 (model_quantized.onnx)
        // Recommended for devices with < 4 GB RAM (LOW).
        // MTEB Avg: ~59.x | Dims: 384 | Size: 34 MB
        // -----------------------------------------------------------------------

        /** HuggingFace URL for the INT8 quantized bge-small-en-v1.5 ONNX model (Xenova conversion). */
        const val QUANTIZED_MODEL_URL =
            "https://huggingface.co/Xenova/bge-small-en-v1.5/resolve/main/onnx/model_quantized.onnx"

        /**
         * SHA-256 checksum for [QUANTIZED_MODEL_URL].
         * Source: LFS pointer at Xenova/bge-small-en-v1.5/raw/main/onnx/model_quantized.onnx
         */
        const val QUANTIZED_MODEL_SHA256 =
            "6c9c6101a956d62dfb5e7190c538226c0c5bb9cb27b651234b6df063ee7dbfe4"

        const val QUANTIZED_MODEL_FILENAME = "bge-small-en-v1.5-int8.onnx"
        const val QUANTIZED_MODEL_DISPLAY_NAME = "bge-small-en-v1.5 INT8 (Quantized, 34 MB)"
        const val QUANTIZED_MODEL_SIZE_BYTES = 34_014_426L

        // -----------------------------------------------------------------------
        // Pre-built ModelConfig instances
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
     * The quantized model is the **safe default** for the majority of devices.
     * Only devices with ≥ 8 GB RAM (HIGH / VERY_HIGH) receive the full FP32 model,
     * which avoids OOM risk on 4–8 GB devices where the 127 MB model loaded on top
     * of app baseline RAM (~200 MB) + vector cache (~75 MB) could exceed heap limits.
     *
     * | RAM class  | Model      | Size  | MTEB  |
     * |------------|------------|-------|-------|
     * | LOW (< 4GB)| Quantized  | 32 MB | ~59.x |
     * | MEDIUM (4–8GB) | Quantized | 32 MB | ~59.x |
     * | HIGH (8–16GB)  | Full FP32 | 127 MB | 62.17 |
     * | VERY_HIGH (16GB+) | Full FP32 | 127 MB | 62.17 |
     */
    fun selectModel(): ModelConfig {
        val memoryClass = deviceProfiler.getProfile().memoryClass
        return when (memoryClass) {
            MemoryClass.LOW,
            MemoryClass.MEDIUM -> QUANTIZED_MODEL
            MemoryClass.HIGH,
            MemoryClass.VERY_HIGH -> FULL_MODEL
        }
    }

    /**
     * Returns all available model variants ordered smallest to largest.
     * Used by SettingsViewModel to populate the model selection list.
     */
    fun getAllModels(): List<ModelConfig> = listOf(QUANTIZED_MODEL, FULL_MODEL)
}
