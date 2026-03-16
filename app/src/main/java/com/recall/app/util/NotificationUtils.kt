package com.recall.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.recall.app.R
import com.recall.app.presentation.MainActivity

/**
 * Utility class for managing notifications.
 */
object NotificationUtils {
    
    const val CHANNEL_ID_PROCESSING = "screenshot_processing"
    const val CHANNEL_ID_GENERAL = "general"
    const val NOTIFICATION_ID_PROCESSING = 1001
    const val NOTIFICATION_ID_COMPLETE = 1002
    const val NOTIFICATION_ID_ERROR = 1003
    
    /**
     * Create notification channels (required for Android 8.0+).
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Processing channel
            val processingChannel = NotificationChannel(
                CHANNEL_ID_PROCESSING,
                "Screenshot Processing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while processing screenshots"
                setShowBadge(false)
            }
            
            // General channel
            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General app notifications"
                setShowBadge(true)
            }
            
            notificationManager.createNotificationChannel(processingChannel)
            notificationManager.createNotificationChannel(generalChannel)
        }
    }
    
    /**
     * Show processing notification.
     */
    fun showProcessingNotification(context: Context, progress: Int = 0) {
        if (!hasNotificationPermission(context)) return
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_PROCESSING)
            .setContentTitle("Processing Screenshots")
            .setContentText("Indexing your screenshots for search")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .build()
        
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID_PROCESSING,
            notification
        )
    }
    
    /**
     * Show processing complete notification.
     */
    fun showProcessingCompleteNotification(context: Context, count: Int) {
        if (!hasNotificationPermission(context)) return
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_GENERAL)
            .setContentTitle("Screenshots Indexed")
            .setContentText("$count screenshots ready for search")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID_COMPLETE,
            notification
        )
    }
    
    /**
     * Show error notification.
     */
    fun showErrorNotification(context: Context, message: String) {
        if (!hasNotificationPermission(context)) return
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_GENERAL)
            .setContentTitle("Screenshot Processing Error")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID_ERROR,
            notification
        )
    }
    
    /**
     * Cancel processing notification.
     */
    fun cancelProcessingNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROCESSING)
    }
    
    /**
     * Check if notification permission is granted.
     */
    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
