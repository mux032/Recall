package com.recall.app.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recall.app.RecallApplication
import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.domain.model.ProcessingState
import com.recall.app.domain.usecase.EmbeddingGenerator
import com.recall.app.domain.usecase.OcrProcessor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Background worker that processes OCR for screenshots that don't have extracted text yet.
 * Prioritizes newer images first (by dateCreated DESC).
 *
 * This worker runs periodically to ensure all screenshots have OCR text without blocking
 * the UI or putting load on the device when user is interacting with the app.
 *
 * RETRY LOGIC: Tracks ocrRetryCount to prevent infinite loops on persistent failures.
 * Screenshots with ocrRetryCount >= MAX_OCR_RETRIES are skipped from automatic processing.
 */
@HiltWorker
class BackgroundOcrWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val ocrProcessor: OcrProcessor,
    private val embeddingGenerator: EmbeddingGenerator,
    private val screenshotDao: ScreenshotDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting background OCR processing...")

            var totalProcessed = 0
            var totalSuccess  = 0
            var totalErrors   = 0

            // ── Pass 1: OCR-pending rows ─────────────────────────────────────
            // Fetches only rows where ocrText IS NULL — filtering done in SQL,
            // so we never load the entire table into memory.
            val ocrPending = screenshotDao.getOcrPendingScreenshots(
                limit      = MAX_SCREENSHOTS_PER_RUN,
                maxRetries = MAX_OCR_RETRIES
            )

            if (ocrPending.isNotEmpty()) {
                Log.i(TAG, "Pass 1: ${ocrPending.size} screenshots need OCR")

                val batches = ocrPending.chunked(BATCH_SIZE)
                for ((batchIndex, batch) in batches.withIndex()) {
                    if (isStopped()) {
                        Log.w(TAG, "Worker stopped during Pass 1 batch ${batchIndex + 1}")
                        return@withContext Result.retry()
                    }
                    Log.d(TAG, "Pass 1 batch ${batchIndex + 1}/${batches.size} (${batch.size} items)")

                    // Parallel OCR — ML Kit TextRecognizer is thread-safe
                    val ocrResults = coroutineScope {
                        batch.map { screenshot ->
                            async(Dispatchers.IO) { runOcrForScreenshot(screenshot) }
                        }.awaitAll()
                    }

                    // Sequential embedding + DB save
                    for ((screenshot, extractedText) in ocrResults) {
                        try {
                            finalizeScreenshot(screenshot, extractedText)
                            totalSuccess++
                            Log.d(TAG, "✓ OCR+embed: ${screenshot.fileName}")
                        } catch (e: Exception) {
                            totalErrors++
                            Log.e(TAG, "✗ Finalize failed: ${screenshot.fileName}", e)
                        }
                        totalProcessed++
                    }

                    if (batchIndex < batches.size - 1) {
                        kotlinx.coroutines.delay(INTER_BATCH_DELAY_MS)
                    }
                }
            } else {
                Log.i(TAG, "Pass 1: no screenshots need OCR")
            }

            // ── Pass 2: Embedding-pending rows ───────────────────────────────
            // Rows where OCR succeeded but embeddingGenerator.generate() returned null
            // (model not loaded, OOM, etc.). Re-attempt embedding only — no re-OCR.
            val remainingSlots = MAX_SCREENSHOTS_PER_RUN - ocrPending.size
            if (remainingSlots > 0) {
                val embeddingPending = screenshotDao.getEmbeddingPendingScreenshots(
                    limit              = remainingSlots,
                    maxEmbeddingRetries = MAX_EMBEDDING_RETRIES
                )

                if (embeddingPending.isNotEmpty()) {
                    Log.i(TAG, "Pass 2: ${embeddingPending.size} screenshots need embedding only")

                    for (screenshot in embeddingPending) {
                        if (isStopped()) {
                            Log.w(TAG, "Worker stopped during Pass 2")
                            return@withContext Result.retry()
                        }
                        try {
                            retryEmbedding(screenshot)
                            totalSuccess++
                            Log.d(TAG, "✓ Embed-only: ${screenshot.fileName}")
                        } catch (e: Exception) {
                            totalErrors++
                            Log.e(TAG, "✗ Embed-only failed: ${screenshot.fileName}", e)
                        }
                        totalProcessed++
                    }
                } else {
                    Log.i(TAG, "Pass 2: no screenshots need embedding")
                }
            }

            if (totalProcessed == 0) {
                Log.i(TAG, "No screenshots need OCR processing")
            } else {
                Log.i(TAG, "=== Batch complete: $totalProcessed processed ($totalSuccess ok, $totalErrors errors) ===")

                // Self-chain: if there are still pending items after this batch, immediately
                // re-enqueue so processing continues without waiting for the next periodic run.
                // KEEP policy means a duplicate is silently dropped if one is already queued.
                // Pause still works: cancelAllWorkByTag(INDEXING_TAG) cancels both the running
                // instance and any queued successor in one call.
                val moreOcrPending = screenshotDao.getOcrPendingScreenshots(
                    limit      = 1,
                    maxRetries = MAX_OCR_RETRIES
                ).isNotEmpty()
                val moreEmbeddingPending = screenshotDao.getEmbeddingPendingScreenshots(
                    limit               = 1,
                    maxEmbeddingRetries = MAX_EMBEDDING_RETRIES
                ).isNotEmpty()

                if (moreOcrPending || moreEmbeddingPending) {
                    Log.i(TAG, "More items pending — re-enqueueing next batch")
                    val next = androidx.work.OneTimeWorkRequestBuilder<BackgroundOcrWorker>()
                        .addTag(RecallApplication.INDEXING_TAG)
                        .build()
                    androidx.work.WorkManager.getInstance(appContext)
                        .enqueueUniqueWork(SELF_CHAIN_WORK_NAME, androidx.work.ExistingWorkPolicy.KEEP, next)
                } else {
                    Log.i(TAG, "All screenshots indexed — indexing complete")
                }
            }

            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in background OCR processing", e)
            Result.retry()
        }
    }

    /**
     * Phase 1 — OCR only (safe to call concurrently across a batch).
     *
     * Runs OCR on [screenshot] and returns a [Pair] of the screenshot and the
     * extracted text (null if OCR failed, empty, or the screenshot should be skipped).
     * Increments the retry counter in the DB on failure but does NOT throw — a single
     * failed OCR should not abort the parallel phase for the rest of the batch.
     */
    private suspend fun runOcrForScreenshot(screenshot: ScreenshotEntity): Pair<ScreenshotEntity, String?> {
        if (screenshot.isUserEdited) {
            Log.d(TAG, "Skipping OCR - user edited: ${screenshot.id}")
            return Pair(screenshot, null)
        }

        if (screenshot.ocrRetryCount >= MAX_OCR_RETRIES) {
            Log.w(TAG, "Skipping OCR - max retries exceeded: ${screenshot.id}")
            return Pair(screenshot, null)
        }

        val file = java.io.File(screenshot.filePath)
        if (!file.exists()) {
            Log.w(TAG, "File not found: ${screenshot.filePath}")
            screenshotDao.incrementOcrRetryCount(screenshot.id)
            return Pair(screenshot, null)
        }

        return try {
            val extractedText = ocrProcessor.process(screenshot.filePath)
            Log.d(TAG, "OCR extracted ${extractedText?.length ?: 0} chars for ${screenshot.fileName}")

            if (extractedText.isNullOrBlank()) {
                Log.w(TAG, "OCR returned empty text for: ${screenshot.filePath}")
                screenshotDao.incrementOcrRetryCount(screenshot.id)
                Pair(screenshot, null)
            } else {
                Pair(screenshot, extractedText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed for ${screenshot.filePath}", e)
            screenshotDao.incrementOcrRetryCount(screenshot.id)
            Pair(screenshot, null)
        }
    }

    /**
     * Pass 1 finalization — Embedding generation + DB save (called sequentially after parallel OCR).
     *
     * Takes the [extractedText] from [runOcrForScreenshot] and:
     * 1. Generates an ONNX embedding vector
     * 2. If embedding succeeds → saves ocrText + embedding + [ProcessingState.Done]
     * 3. If embedding returns null (model not loaded, OOM, etc.) → saves ocrText only,
     *    leaves [ProcessingState.Pending] so the row appears in [getEmbeddingPendingScreenshots]
     *    and is picked up by Pass 2 on the next worker run.
     *
     * If [extractedText] is null, Phase 1 already handled the failure (retry count incremented)
     * — this function is a no-op in that case.
     */
    private suspend fun finalizeScreenshot(screenshot: ScreenshotEntity, extractedText: String?) {
        if (extractedText.isNullOrBlank()) return  // Phase 1 already handled failure

        try {
            val embedding = embeddingGenerator.generate(extractedText)

            if (embedding != null) {
                // Full success — OCR text + embedding ready → mark Done
                screenshotDao.update(screenshot.copy(
                    ocrText = extractedText,
                    embeddingByteArray = floatToByteArray(embedding),
                    processingState = ProcessingState.Done,
                    ocrRetryCount = 0
                ))
                screenshotDao.rebuildFtsIndex()
                Log.i(TAG, "Saved OCR + embedding for: ${screenshot.fileName}")
            } else {
                // Embedding failed (model unavailable / OOM) — save OCR text but stay Pending
                // so Pass 2 can retry the embedding on the next worker run.
                Log.w(TAG, "Embedding returned null for ${screenshot.fileName} — saving OCR only, will retry embedding")
                screenshotDao.update(screenshot.copy(
                    ocrText = extractedText,
                    embeddingByteArray = null,
                    processingState = ProcessingState.Pending,
                    ocrRetryCount = 0
                ))
                screenshotDao.rebuildFtsIndex()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize ${screenshot.fileName}", e)
            screenshotDao.incrementOcrRetryCount(screenshot.id)
            throw e
        }
    }

    /**
     * Pass 2 — Retry embedding for a row that already has [ScreenshotEntity.ocrText]
     * but is missing [ScreenshotEntity.embeddingByteArray].
     *
     * Does not re-run OCR. On success saves embedding + [ProcessingState.Done] and
     * resets [ScreenshotEntity.embeddingRetryCount] to 0.
     * On failure increments [ScreenshotEntity.embeddingRetryCount] — intentionally
     * separate from [ScreenshotEntity.ocrRetryCount] so that transient embedding errors
     * (model not loaded, OOM) never permanently orphan rows with valid OCR text.
     */
    private suspend fun retryEmbedding(screenshot: ScreenshotEntity) {
        val ocrText = screenshot.ocrText
        if (ocrText.isNullOrBlank()) return  // Shouldn't happen given the SQL filter, but be safe

        try {
            val embedding = embeddingGenerator.generate(ocrText)
            if (embedding != null) {
                screenshotDao.update(screenshot.copy(
                    embeddingByteArray = floatToByteArray(embedding),
                    processingState = ProcessingState.Done,
                    embeddingRetryCount = 0
                ))
                screenshotDao.rebuildFtsIndex()
                Log.i(TAG, "Embedding retry succeeded for: ${screenshot.fileName}")
            } else {
                Log.w(TAG, "Embedding retry returned null for: ${screenshot.fileName}")
                screenshotDao.incrementEmbeddingRetryCount(screenshot.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Embedding retry failed for ${screenshot.fileName}", e)
            screenshotDao.incrementEmbeddingRetryCount(screenshot.id)
            throw e
        }
    }

    /**
     * Convert FloatArray to ByteArray for storage
     */
    private fun floatToByteArray(floatArray: FloatArray): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(floatArray.size * 4)
        for (f in floatArray) {
            buffer.putFloat(f)
        }
        return buffer.array()
    }

    companion object {
        const val TAG = "BackgroundOcrWorker"

        /**
         * Maximum number of OCR retry attempts per screenshot.
         * Prevents infinite loops on persistent OCR failures (e.g. corrupt image, ML Kit bug).
         */
        const val MAX_OCR_RETRIES = 3

        /**
         * Maximum number of embedding-only retry attempts per screenshot.
         *
         * Higher than [MAX_OCR_RETRIES] because embedding failures are almost always
         * transient (model not yet downloaded, low RAM, cold start) rather than structural
         * (corrupt file, ML Kit bug). A row with valid OCR text should never be permanently
         * orphaned due to a temporary embedding failure.
         */
        const val MAX_EMBEDDING_RETRIES = 10

        /**
         * Maximum number of screenshots to process in one worker run.
         * Caps total run time to prevent the OS from killing a long-running worker
         * and to avoid draining battery on large backlogs.
         */
        const val MAX_SCREENSHOTS_PER_RUN = 20

        /**
         * Unique work name used when the worker re-enqueues itself after completing a
         * full batch while more items remain. [ExistingWorkPolicy.KEEP] prevents the
         * periodic scheduler from spawning a second instance while one is already queued.
         */
        const val SELF_CHAIN_WORK_NAME = "background_ocr_self_chain"

        /**
         * Number of screenshots processed per batch.
         *
         * OCR tasks within a batch run in parallel ([runOcrForScreenshot] via async/awaitAll),
         * so raising this from 5 → 20 increases throughput without proportionally increasing
         * wall-clock time. On a 6-core device, 20 parallel ML Kit tasks complete in roughly
         * the same time as 5 did previously (~2–3 s), giving ~4× more throughput per batch.
         *
         * The [INTER_BATCH_DELAY_MS] cool-down between batches is the only thermal throttle now.
         */
        const val BATCH_SIZE = 20

        /**
         * Cool-down delay (ms) between consecutive batches.
         * Allows CPU and thermal sensors to recover between processing bursts,
         * reducing the risk of thermal throttling on mid-range devices.
         */
        const val INTER_BATCH_DELAY_MS = 2_000L
    }
}
