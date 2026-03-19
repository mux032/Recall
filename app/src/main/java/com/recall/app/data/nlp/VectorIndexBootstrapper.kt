package com.recall.app.data.nlp

import android.util.Log
import com.recall.app.data.local.dao.ScreenshotDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VectorIndexBootstrapper @Inject constructor(
    private val screenshotDao: ScreenshotDao,
    private val vectorIndex: VectorIndex
) {
    fun initialize(applicationScope: CoroutineScope) {
        applicationScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Bootstrapping Vector Index into memory...")
                // For MVP, we load all at once. For thousands of images we'd paginate
                val screenshots = screenshotDao.getAllScreenshots().firstOrNull() ?: emptyList()
                
                val vectorData = screenshots.mapNotNull { entity ->
                    entity.embeddingByteArray?.let { blob ->
                        entity.id to blob
                    }
                }.toMap()

                vectorIndex.loadAll(vectorData)
                Log.d(TAG, "Vector Index successfully loaded with ${vectorData.size} embeddings")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bootstrap Vector Index", e)
            }
        }
    }

    companion object {
        const val TAG = "VectorIndexBootstrapper"
    }
}
