package com.recall.app

import android.app.Application
import com.recall.app.util.NotificationUtils
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Recall app.
 * Initializes Hilt dependency injection.
 */
@HiltAndroidApp
class RecallApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Create notification channels
        NotificationUtils.createNotificationChannels(this)
    }
    
    companion object {
        lateinit var instance: RecallApplication
            private set
    }
}
