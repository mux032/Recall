package com.recall.app.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.recall.app.data.local.ModelDownloadState
import com.recall.app.data.local.ModelRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Downloads the ONNX embedding model from HuggingFace, verifies its SHA-256 integrity,
 * and saves it to [Context.filesDir]/models/.
 *
 * ## Input data (set by the enqueue caller)
 * - [KEY_MODEL_URL]      — Full HuggingFace CDN URL
 * - [KEY_MODEL_SHA256]   — Expected 64-char hex SHA-256 checksum
 * - [KEY_MODEL_FILENAME] — Filename to write in `filesDir/models/`
 *
 * ## Lifecycle
 * ```
 * enqueue → DOWNLOADING state set
 *         → streaming download with setProgress() updates
 *         → SHA-256 verified
 *         → READY state set          (success)
 *         → FAILED state + file deleted (SHA mismatch or error)
 * ```
 *
 * ## Streaming
 * The response body is piped directly from the HTTP socket to disk in 8 KB chunks.
 * The file is never fully loaded into RAM — safe for the 32–127 MB model files.
 *
 * ## Cancellation
 * WorkManager sets [isStopped] to true when the work is cancelled. The download loop
 * checks this flag and cleans up the partial file before returning [Result.failure].
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val modelRepository: ModelRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ModelDownloadWorker"

        /** Input data key — full download URL. */
        const val KEY_MODEL_URL = "model_url"

        /** Input data key — expected SHA-256 hex string (64 chars). */
        const val KEY_MODEL_SHA256 = "model_sha256"

        /** Input data key — filename to write under filesDir/models/. */
        const val KEY_MODEL_FILENAME = "model_filename"

        /** Progress output key — Float 0.0–1.0 reported via setProgress(). */
        const val PROGRESS_KEY = "download_progress"

        /** Sub-directory inside filesDir where models are stored. */
        const val MODELS_DIR = "models"

        /** Buffer size for streaming download — 8 KB is a safe balance of speed and memory. */
        private const val BUFFER_SIZE = 8 * 1024

        /** OkHttp connect timeout. */
        private const val CONNECT_TIMEOUT_S = 30L

        /** OkHttp read timeout — generous for large file downloads on slow connections. */
        private const val READ_TIMEOUT_S = 120L
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_MODEL_URL)
        val expectedSha256 = inputData.getString(KEY_MODEL_SHA256)
        val fileName = inputData.getString(KEY_MODEL_FILENAME)

        if (url.isNullOrBlank() || expectedSha256.isNullOrBlank() || fileName.isNullOrBlank()) {
            Log.e(TAG, "Missing required input data: url=$url, sha256=$expectedSha256, fileName=$fileName")
            modelRepository.setDownloadState(ModelDownloadState.FAILED)
            return@withContext Result.failure()
        }

        Log.i(TAG, "Starting download: $fileName from $url")
        modelRepository.setDownloadState(ModelDownloadState.DOWNLOADING)
        modelRepository.setDownloadProgress(0f)

        // Ensure models directory exists
        val modelsDir = File(appContext.filesDir, MODELS_DIR).also { it.mkdirs() }
        val outputFile = File(modelsDir, fileName)

        return@withContext try {
            downloadFile(url, outputFile)

            if (isStopped) {
                Log.w(TAG, "Worker cancelled — cleaning up partial file")
                outputFile.delete()
                modelRepository.setDownloadState(ModelDownloadState.FAILED)
                return@withContext Result.failure()
            }

            // Verify SHA-256 integrity
            Log.i(TAG, "Download complete. Verifying SHA-256...")
            val actualSha256 = sha256Hex(outputFile)

            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                Log.e(TAG, "SHA-256 mismatch! expected=$expectedSha256 actual=$actualSha256")
                outputFile.delete()
                modelRepository.setDownloadState(ModelDownloadState.FAILED)
                Result.failure(
                    workDataOf("error" to "SHA-256 mismatch")
                )
            } else {
                Log.i(TAG, "SHA-256 verified ✓ — model ready at ${outputFile.absolutePath}")
                modelRepository.setDownloadedModelPath(outputFile.absolutePath)
                modelRepository.setDownloadProgress(1f)
                modelRepository.setDownloadState(ModelDownloadState.READY)
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            outputFile.delete()
            modelRepository.setDownloadState(ModelDownloadState.FAILED)
            Result.retry()
        }
    }

    /**
     * Streams the HTTP response body to [outputFile] in [BUFFER_SIZE] chunks,
     * reporting progress via [setProgress] after each chunk.
     *
     * @throws Exception on network error or if [isStopped] is true before completion.
     */
    private suspend fun downloadFile(url: String, outputFile: File) {
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.message}")
        }

        val body = response.body ?: throw Exception("Empty response body")
        val totalBytes = body.contentLength().takeIf { it > 0 } ?: -1L
        var downloadedBytes = 0L

        body.byteStream().use { input ->
            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (isStopped) break

                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    // Report progress if content-length is known
                    if (totalBytes > 0) {
                        val progress = downloadedBytes.toFloat() / totalBytes
                        modelRepository.setDownloadProgress(progress)
                        setProgress(workDataOf(PROGRESS_KEY to progress))
                    }
                }
                output.flush()
            }
        }

        Log.d(TAG, "Streamed $downloadedBytes bytes to ${outputFile.name}")
    }

    /**
     * Computes the SHA-256 hex digest of [file].
     * Reads in chunks to avoid OOM on large model files.
     */
    internal fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        file.inputStream().use { stream ->
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
