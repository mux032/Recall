package com.recall.app

import android.app.Application
import android.provider.MediaStore
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.recall.app.data.service.ScreenshotContentObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import com.recall.app.data.nlp.VectorIndexBootstrapper

@HiltAndroidApp
class RecallApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var vectorIndexBootstrapper: VectorIndexBootstrapper
    
    private val applicationScope = MainScope()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    
    private lateinit var contentObserver: ScreenshotContentObserver

    override fun onCreate() {
        super.onCreate()
        
        // Initialize the in-memory semantic Vector Index
        vectorIndexBootstrapper.initialize(applicationScope)
        
        contentObserver = ScreenshotContentObserver(this)
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }
}
