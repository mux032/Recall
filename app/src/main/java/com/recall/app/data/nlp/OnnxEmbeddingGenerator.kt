package com.recall.app.data.nlp

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.recall.app.data.local.ModelDownloadState
import com.recall.app.data.local.ModelRepository
import com.recall.app.domain.usecase.EmbeddingGenerator
import com.recall.app.util.MemoryInfoHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * ONNX-based sentence embedding generator.
 *
 * ## Model resolution (priority order)
 * 1. **`filesDir/models/<fileName>`** — model downloaded by [ModelDownloadWorker] (Phase 7)
 * 2. **`assets/model.onnx`** — bundled model for development builds
 * 3. **`null`** — no model available; [generate] returns `null` gracefully, no crash
 *
 * ## Session lifecycle
 * - Session is created lazily on the first [generate] call
 * - Session is **reinitialised automatically** when [ModelRepository.downloadState]
 *   transitions to [ModelDownloadState.READY] — so a freshly downloaded model is picked
 *   up without an app restart
 * - [close] releases all native ONNX resources; call from [Application.onTerminate]
 *
 * ## Graceful degradation
 * When no model file is available [generate] returns `null` for every call.
 * The caller ([SearchScreenshotsUseCase]) falls back to FTS-only search.
 */
@Singleton
class OnnxEmbeddingGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryInfoHelper: MemoryInfoHelper,
    private val modelRepository: ModelRepository
) : EmbeddingGenerator {

    companion object {
        private const val TAG = "OnnxEmbeddingGenerator"
        private const val MIN_FREE_MEMORY_BYTES = 100 * 1024 * 1024L // 100 MB minimum
        private const val ASSETS_MODEL_FILENAME = "model.onnx"
    }

    private val tokenizer = WordPieceTokenizer(context, "vocab.txt")

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var isInitialized = false
    private var initializationFailed = false
    private var failureReason: String? = null

    /** Scope used to observe [ModelRepository.downloadState] for auto-reinit. */
    private val observerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Observe downloadState — reinitialise session when a new model becomes READY
        observerScope.launch {
            modelRepository.downloadState
                .distinctUntilChanged()
                .filter { it == ModelDownloadState.READY }
                .collect {
                    Log.i(TAG, "New model READY — reinitialising ONNX session")
                    reinitialise()
                }
        }
    }

    // -----------------------------------------------------------------------
    // Model path resolution
    // -----------------------------------------------------------------------

    /**
     * Resolves the model file path using the priority order described in the class KDoc.
     *
     * @return Absolute path string, or `null` if no model is available.
     */
    internal fun resolveModelPath(): String? {
        // 1. Check filesDir for downloaded model (primary)
        val persistedPath = runBlocking {
            modelRepository.downloadedModelPath.first()
        }

        if (persistedPath != null && File(persistedPath).exists()) {
            Log.i(TAG, "Using downloaded model: $persistedPath")
            return persistedPath
        }

        // 2. Fall back to assets/model.onnx (dev builds with bundled model)
        return try {
            context.assets.open(ASSETS_MODEL_FILENAME).use { /* verify accessible */ }
            Log.i(TAG, "Using bundled assets model: $ASSETS_MODEL_FILENAME")
            "assets://$ASSETS_MODEL_FILENAME"
        } catch (e: IOException) {
            Log.w(TAG, "No model available — not in filesDir and not in assets")
            null
        }
    }

    // -----------------------------------------------------------------------
    // Session lifecycle
    // -----------------------------------------------------------------------

    /**
     * Closes the current session and resets state so the next [generate] call
     * reinitialises from the most recently available model path.
     */
    private fun reinitialise() {
        synchronized(this) {
            try {
                session?.close()
                env?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing old session during reinit", e)
            } finally {
                session = null
                env = null
                isInitialized = false
                initializationFailed = false
                failureReason = null
            }
        }
        Log.i(TAG, "ONNX session reset — will reinitialise on next generate() call")
    }

    /**
     * Initialises the ONNX session from the best available model source.
     * Checks available memory before loading to prevent OOM.
     */
    private fun initializeSession(): Result<Unit> {
        if (isInitialized) return Result.success(Unit)
        if (initializationFailed) return Result.failure(
            Exception(failureReason ?: "Initialization previously failed")
        )

        val availableMemory = memoryInfoHelper.getAvailableMemory()
        if (availableMemory < MIN_FREE_MEMORY_BYTES) {
            val errorMsg = "Insufficient memory for AI model: " +
                "${availableMemory / 1024 / 1024}MB available, " +
                "need ${MIN_FREE_MEMORY_BYTES / 1024 / 1024}MB"
            Log.w(TAG, errorMsg)
            initializationFailed = true
            failureReason = errorMsg
            return Result.failure(OutOfMemoryError(errorMsg))
        }

        return try {
            val modelPath = resolveModelPath()

            if (modelPath == null) {
                val errorMsg = "No model file available (not downloaded, not in assets)"
                Log.w(TAG, errorMsg)
                initializationFailed = true
                failureReason = errorMsg
                return Result.failure(Exception(errorMsg))
            }

            env = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setIntraOpNumThreads(2)
            }

            val bytes = if (modelPath.startsWith("assets://")) {
                // Load from assets
                context.assets.open(ASSETS_MODEL_FILENAME).readBytes()
            } else {
                // Load from filesDir
                File(modelPath).readBytes()
            }

            session = env?.createSession(bytes, sessionOptions)
            isInitialized = true
            Log.i(TAG, "ONNX session initialised from: $modelPath")
            Result.success(Unit)
        } catch (e: OutOfMemoryError) {
            val errorMsg = "OutOfMemoryError loading AI model: ${e.message}"
            Log.e(TAG, errorMsg, e)
            initializationFailed = true
            failureReason = errorMsg
            Result.failure(e)
        } catch (e: Exception) {
            val errorMsg = "Failed to initialise ONNX session: ${e.message}"
            Log.e(TAG, errorMsg, e)
            initializationFailed = true
            failureReason = errorMsg
            Result.failure(e)
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Returns true when the ONNX session is loaded and ready. */
    fun isModelLoaded(): Boolean = isInitialized && session != null

    /** Returns the failure reason if initialisation failed, or null if successful. */
    fun getFailureReason(): String? = failureReason

    /** Releases all native ONNX resources. Call from [Application.onTerminate]. */
    override fun close() {
        observerScope.cancel()
        try {
            session?.close()
            env?.close()
            session = null
            env = null
            isInitialized = false
            Log.i(TAG, "ONNX session closed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ONNX session", e)
        }
    }

    override suspend fun generate(text: String): FloatArray? = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext null

        synchronized(this@OnnxEmbeddingGenerator) {
            if (!isInitialized) {
                val initResult = initializeSession()
                if (initResult.isFailure) {
                    Log.w(TAG, "Skipping embedding: ${initResult.exceptionOrNull()?.message}")
                    return@withContext null
                }
            }
        }

        val currentSession = session ?: return@withContext null
        val currentEnv = env ?: return@withContext null

        try {
            val maxLength = 256
            val tokens = tokenizer.tokenize(text, maxLength)

            val inputIdsArray = Array(1) { tokens.inputIds }
            val attentionMaskArray = Array(1) { tokens.attentionMask }
            val tokenTypeIdsArray = Array(1) { tokens.tokenTypeIds }

            val inputIdsTensor = OnnxTensor.createTensor(currentEnv, inputIdsArray)
            val attentionMaskTensor = OnnxTensor.createTensor(currentEnv, attentionMaskArray)
            val tokenTypeIdsTensor = OnnxTensor.createTensor(currentEnv, tokenTypeIdsArray)

            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor,
                "token_type_ids" to tokenTypeIdsTensor
            )

            val result = currentSession.run(inputs)
            val outputTensor = result.get(0) as? OnnxTensor
            val outputValue = outputTensor?.value as? Array<Array<FloatArray>>

            inputIdsTensor.close()
            attentionMaskTensor.close()
            tokenTypeIdsTensor.close()
            result.close()

            if (outputValue == null) return@withContext null

            val hiddenStates = outputValue[0]
            val pooledOutput = meanPooling(hiddenStates, tokens.attentionMask)
            return@withContext l2Normalize(pooledOutput)

        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError during embedding generation", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error generating embedding", e)
            null
        }
    }

    // -----------------------------------------------------------------------
    // Private — inference helpers (unchanged)
    // -----------------------------------------------------------------------

    private fun meanPooling(hiddenStates: Array<FloatArray>, attentionMask: LongArray): FloatArray {
        val seqLen = hiddenStates.size
        val hiddenSize = hiddenStates[0].size
        val result = FloatArray(hiddenSize)
        var sumAttentionMask = 0f

        for (i in 0 until seqLen) {
            val mask = attentionMask[i].toFloat()
            sumAttentionMask += mask
            for (j in 0 until hiddenSize) {
                result[j] += hiddenStates[i][j] * mask
            }
        }

        if (sumAttentionMask > 0f) {
            for (j in 0 until hiddenSize) {
                result[j] /= sumAttentionMask
            }
        }
        return result
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0f
        for (v in vector) { sumSquares += v * v }
        val norm = sqrt(sumSquares.toDouble()).toFloat()
        val eps = 1e-12f
        val denominator = if (norm < eps) eps else norm
        return FloatArray(vector.size) { i -> vector[i] / denominator }
    }
}
