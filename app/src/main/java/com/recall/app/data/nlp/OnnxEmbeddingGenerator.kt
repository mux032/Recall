package com.recall.app.data.nlp

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.recall.app.domain.usecase.EmbeddingGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * ONNX-based embedding generator using sentence-transformers (all-MiniLM-L6-v2).
 * 
 * Features:
 * - Singleton pattern ensures single ONNX session instance (prevents memory leaks)
 * - Session created once on first use (lazy initialization)
 * - Proper resource cleanup via close() method
 * 
 * Note: The ONNX session is heavy (~50MB) and should only be created once.
 * This implementation ensures the session is reused across all embedding requests.
 */
@Singleton
class OnnxEmbeddingGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) : EmbeddingGenerator {

    companion object {
        private const val TAG = "OnnxEmbeddingGenerator"
    }

    private val tokenizer = WordPieceTokenizer(context, "vocab.txt")

    // Nullable references for proper lifecycle management
    // Session is created on first use and can be closed in onTerminate()
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var isInitialized = false

    /**
     * Initialize the ONNX session.
     * Called automatically on first generate() call.
     */
    private fun initializeSession() {
        if (isInitialized) return
        
        try {
            env = OrtEnvironment.getEnvironment()
            
            val bytes = context.assets.open("model.onnx").readBytes()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setIntraOpNumThreads(2) // Run safely on device CPU without locking
            }
            
            session = env?.createSession(bytes, sessionOptions)
            isInitialized = true
            
            Log.i(TAG, "ONNX session initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ONNX session", e)
            throw e
        }
    }

    /**
     * Close the ONNX session to release native resources.
     * Call this in Application.onTerminate() or when the generator is no longer needed.
     */
    override fun close() {
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

        // Initialize session on first use (lazy initialization)
        if (!isInitialized) {
            initializeSession()
        }

        val currentSession = session ?: return@withContext null
        val currentEnv = env ?: return@withContext null

        try {
            // 1. Tokenize Text
            val maxLength = 256
            val tokens = tokenizer.tokenize(text, maxLength)

            // 2. Prepare ONNX Array inputs (Shape: [batch_size=1, seq_length=256])
            val inputIdsArray = Array(1) { tokens.inputIds }
            val attentionMaskArray = Array(1) { tokens.attentionMask }
            val tokenTypeIdsArray = Array(1) { tokens.tokenTypeIds }

            // 3. Create Tensors
            val inputIdsTensor = OnnxTensor.createTensor(currentEnv, inputIdsArray)
            val attentionMaskTensor = OnnxTensor.createTensor(currentEnv, attentionMaskArray)
            val tokenTypeIdsTensor = OnnxTensor.createTensor(currentEnv, tokenTypeIdsArray)

            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor,
                "token_type_ids" to tokenTypeIdsTensor
            )

            // 4. Run Inference
            val result = currentSession.run(inputs)

            // all-MiniLM-L6-v2 output is typically named "last_hidden_state"
            // The shape is usually [1, seq_length, 384]
            val outputTensor = result.get(0) as? OnnxTensor
            val outputValue = outputTensor?.value as? Array<Array<FloatArray>>

            // Clean up tensors immediately
            inputIdsTensor.close()
            attentionMaskTensor.close()
            tokenTypeIdsTensor.close()
            result.close()

            if (outputValue == null) return@withContext null

            // 5. Mean Pooling & L2 Normalization required for sentence transformers
            val hiddenStates = outputValue[0] // Get batch 0
            val pooledOutput = meanPooling(hiddenStates, tokens.attentionMask)

            return@withContext l2Normalize(pooledOutput)

        } catch (e: Exception) {
            Log.e(TAG, "Error generating embedding", e)
            e.printStackTrace()
            null
        }
    }

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
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares.toDouble()).toFloat()

        // Avoid division by zero
        val eps = 1e-12f
        val denominator = if (norm < eps) eps else norm

        val normalized = FloatArray(vector.size)
        for (i in vector.indices) {
            normalized[i] = vector[i] / denominator
        }
        return normalized
    }
}
