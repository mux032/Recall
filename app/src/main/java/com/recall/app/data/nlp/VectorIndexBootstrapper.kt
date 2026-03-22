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
                val screenshots = screenshotDao.getAllScreenshots().firstOrNull() ?: emptyList()
                Log.i(TAG, "Total screenshots in DB: ${screenshots.size}")

                var withEmbeddings = 0
                var withoutEmbeddings = 0

                val vectorData = screenshots.mapNotNull { entity ->
                    if (entity.embeddingByteArray != null) {
                        withEmbeddings++
                        entity.id to entity.embeddingByteArray
                    } else {
                        withoutEmbeddings++
                        null
                    }
                }.toMap()

                Log.i(TAG, "Screenshots with embeddings: $withEmbeddings")
                Log.i(TAG, "Screenshots without embeddings: $withoutEmbeddings")

                vectorIndex.loadAll(vectorData)
                Log.i(TAG, "Vector Index loaded with ${vectorData.size} embeddings")
                Log.i(TAG, "VectorIndex.isReady() = ${vectorIndex.isReady()}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bootstrap Vector Index", e)
            }
        }
    }

    companion object {
        const val TAG = "VectorIndexBootstrapper"
    }
}
