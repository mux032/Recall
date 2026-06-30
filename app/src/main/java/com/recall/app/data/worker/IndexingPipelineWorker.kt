package com.recall.app.data.worker

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.recall.app.RecallApplication
import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.domain.model.ProcessingState
import com.recall.app.domain.usecase.EmbeddingGenerator
import com.recall.app.domain.usecase.OcrProcessor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

@HiltWorker
class IndexingPipelineWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val screenshotDao: ScreenshotDao,
    private val ocrProcessor: OcrProcessor,
    private val embeddingGenerator: EmbeddingGenerator
) : CoroutineWorker(appContext, workerParams) {

    private data class OcrResult(
        val entity: ScreenshotEntity,
        val text: String?
    )

    override suspend fun doWork(): Result {
        Log.i(TAG, "IndexingPipelineWorker started")

        val total = screenshotDao.getPendingCount()
        if (total == 0) {
            Log.i(TAG, "No pending screenshots — exiting")
            return Result.success()
        }

        Log.i(TAG, "Found $total pending screenshots")

        if (total >= FOREGROUND_THRESHOLD) {
            try {
                setForeground(buildForegroundInfo(0, total))
            } catch (e: Exception) {
                Log.w(TAG, "Could not set foreground: ${e.message}")
            }
        }

        _indexingProgress.value = IndexingProgress(0, total)

        val scanChannel = Channel<ScreenshotEntity>(capacity = SCAN_CHANNEL_CAPACITY)
        val ocrChannel = Channel<OcrResult>(capacity = OCR_CHANNEL_CAPACITY)
        val ocrWorkerCount = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, MAX_OCR_WORKERS)

        // AtomicInteger ensures thread-safe increments across the two concurrent embedding coroutines
        val completedCount = AtomicInteger(0)

        try {
            supervisorScope {
                // Stage 1: Scanner — produces into scanChannel
                launch {
                    runScannerStage(scanChannel)
                }

                // Stage 2: OCR pool — fan-out from scanChannel, fan-in to ocrChannel.
                // We wrap all OCR workers in an inner coroutineScope so we can close
                // ocrChannel after ALL OCR workers finish, signalling Stage 3 to terminate.
                launch {
                    coroutineScope {
                        repeat(ocrWorkerCount) {
                            launch {
                                runOcrStage(scanChannel, ocrChannel)
                            }
                        }
                    }
                    // All OCR workers done — close ocrChannel so Stage 3 consumers terminate
                    ocrChannel.close()
                }

                // Stage 3: Embedding pool — fan-out from ocrChannel
                repeat(EMBEDDING_WORKER_COUNT) {
                    launch {
                        runEmbeddingStage(ocrChannel) {
                            val done = completedCount.incrementAndGet()
                            _indexingProgress.value = IndexingProgress(done, total)
                            if (total >= FOREGROUND_THRESHOLD) {
                                try { setForeground(buildForegroundInfo(done, total)) } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pipeline error: ${e.message}", e)
        }

        val finalCount = completedCount.get()
        _indexingProgress.value = IndexingProgress(finalCount, total)

        val remaining = screenshotDao.getPendingCount()
        if (remaining > 0 && !isStopped) {
            Log.i(TAG, "More items remain ($remaining) — self-chaining")
            val next = OneTimeWorkRequestBuilder<IndexingPipelineWorker>()
                .addTag(RecallApplication.INDEXING_TAG)
                .build()
            WorkManager.getInstance(appContext)
                .enqueueUniqueWork(PIPELINE_WORK_NAME, ExistingWorkPolicy.KEEP, next)
        }

        Log.i(TAG, "IndexingPipelineWorker completed. Processed: $finalCount / $total")
        return Result.success()
    }

    private suspend fun runScannerStage(scanChannel: Channel<ScreenshotEntity>) {
        try {
            while (!isStopped) {
                val batch = screenshotDao.getOcrPendingScreenshots(
                    limit = SCAN_BATCH_SIZE,
                    maxRetries = MAX_OCR_RETRIES
                )
                if (batch.isEmpty()) break

                // Sort newest first for recency priority
                val sorted = batch.sortedByDescending { it.dateCreated }
                for (entity in sorted) {
                    if (isStopped) break
                    scanChannel.send(entity)
                }

                // Also fetch embedding-pending items
                val embeddingBatch = screenshotDao.getEmbeddingPendingScreenshots(
                    limit = SCAN_BATCH_SIZE,
                    maxEmbeddingRetries = MAX_EMBEDDING_RETRIES
                )
                for (entity in embeddingBatch) {
                    if (isStopped) break
                    // Entity already has ocrText — OCR stage will pass it through
                    scanChannel.send(entity)
                }

                if (batch.size < SCAN_BATCH_SIZE && embeddingBatch.size < SCAN_BATCH_SIZE) break
            }
        } finally {
            scanChannel.close()
            Log.d(TAG, "Scanner stage complete")
        }
    }

    private suspend fun runOcrStage(
        scanChannel: Channel<ScreenshotEntity>,
        ocrChannel: Channel<OcrResult>
    ) {
        for (entity in scanChannel) {
            if (isStopped) break

            checkThermal()

            // If already has OCR text — skip to embedding
            if (entity.ocrText != null) {
                ocrChannel.send(OcrResult(entity, entity.ocrText))
                continue
            }

            val text = try {
                ocrProcessor.process(entity.filePath)
            } catch (e: Exception) {
                Log.w(TAG, "OCR error for ${entity.fileName}: ${e.message}")
                null
            }

            if (text == null) {
                val newCount = entity.ocrRetryCount + 1
                if (newCount >= MAX_OCR_RETRIES) {
                    screenshotDao.update(entity.copy(processingState = ProcessingState.Failed, ocrRetryCount = newCount))
                    Log.w(TAG, "OCR permanently failed for ${entity.fileName}")
                } else {
                    screenshotDao.incrementOcrRetryCount(entity.id)
                }
                continue
            }

            ocrChannel.send(OcrResult(entity, text))
        }
    }

    private suspend fun runEmbeddingStage(
        ocrChannel: Channel<OcrResult>,
        onProgress: suspend () -> Unit
    ) {
        var ftsRebuildCounter = 0

        for (result in ocrChannel) {
            if (isStopped) break

            val entity = result.entity
            val ocrText = result.text ?: entity.ocrText ?: continue

            val embedding = try {
                embeddingGenerator.generate(ocrText)
            } catch (e: Exception) {
                Log.w(TAG, "Embedding error for ${entity.fileName}: ${e.message}")
                null
            }

            if (embedding == null) {
                val newCount = entity.embeddingRetryCount + 1
                if (newCount >= MAX_EMBEDDING_RETRIES) {
                    screenshotDao.update(entity.copy(
                        ocrText = ocrText,
                        processingState = ProcessingState.Failed,
                        embeddingRetryCount = newCount
                    ))
                    Log.w(TAG, "Embedding permanently failed for ${entity.fileName}")
                } else {
                    screenshotDao.update(entity.copy(
                        ocrText = ocrText,
                        processingState = ProcessingState.Pending,
                        embeddingRetryCount = newCount
                    ))
                }
                onProgress()
                continue
            }

            screenshotDao.update(entity.copy(
                ocrText = ocrText,
                embeddingByteArray = floatToByteArray(embedding),
                processingState = ProcessingState.Done,
                ocrRetryCount = 0,
                embeddingRetryCount = 0
            ))

            ftsRebuildCounter++
            if (ftsRebuildCounter >= FTS_REBUILD_BATCH) {
                screenshotDao.rebuildFtsIndex()
                ftsRebuildCounter = 0
            }

            onProgress()
            Log.i(TAG, "Indexed: ${entity.fileName}")
        }

        // Final FTS rebuild for any remaining
        if (ftsRebuildCounter > 0) {
            screenshotDao.rebuildFtsIndex()
        }
    }

    private suspend fun checkThermal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val pm = appContext.getSystemService(PowerManager::class.java) ?: return
        if (pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
            Log.w(TAG, "Thermal throttling — pausing for ${THERMAL_DELAY_MS}ms")
            delay(THERMAL_DELAY_MS)
        }
    }

    private fun buildForegroundInfo(completed: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(appContext, RecallApplication.INDEXING_CHANNEL_ID)
            .setContentTitle("Indexing screenshots")
            .setContentText(if (total > 0) "Processing $completed of $total" else "Scanning screenshots...")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setProgress(total, completed, total == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun floatToByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4)
        floats.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    companion object {
        private const val TAG = "IndexingPipeline"
        const val PIPELINE_WORK_NAME = "indexing_pipeline_work"
        const val FOREGROUND_THRESHOLD = 50
        const val NOTIFICATION_ID = 2001
        const val SCAN_CHANNEL_CAPACITY = 50
        const val OCR_CHANNEL_CAPACITY = 20
        const val MAX_OCR_WORKERS = 4
        const val EMBEDDING_WORKER_COUNT = 2
        const val SCAN_BATCH_SIZE = 100
        const val FTS_REBUILD_BATCH = 10
        const val THERMAL_DELAY_MS = 30_000L
        const val MAX_OCR_RETRIES = BackgroundOcrWorker.MAX_OCR_RETRIES
        const val MAX_EMBEDDING_RETRIES = BackgroundOcrWorker.MAX_EMBEDDING_RETRIES

        private val _indexingProgress = MutableStateFlow(IndexingProgress(0, 0))
        val indexingProgress: StateFlow<IndexingProgress> = _indexingProgress.asStateFlow()
    }
}
