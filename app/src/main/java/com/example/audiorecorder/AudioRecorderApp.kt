package com.example.audiorecorder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.audiorecorder.utils.Logger
import com.example.audiorecorder.workers.AutoDeleteWorker
import java.util.concurrent.TimeUnit

class AudioRecorderApp : Application() {
    
    companion object {
        const val CHANNEL_ID = "AudioRecorderChannel"
        const val TRANSCRIPT_CHANNEL_ID = "TranscriptNotificationChannel"
        private const val AUTO_DELETE_WORK_NAME = "auto_delete_work"
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize logger first for better debugging
        Logger.init(this)
        Logger.ui("Application starting")
        
        // Create notification channels
        createNotificationChannels()
        
        // Schedule auto-delete worker
        scheduleAutoDeleteWorker()
        
        Logger.ui("Application initialized successfully")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Logger.ui("Creating notification channels")
            
            // Service notification channel
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Audio Recorder Service",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notification channel for Audio Recorder service"
            }
            
            // Transcript notification channel
            val transcriptChannel = NotificationChannel(
                TRANSCRIPT_CHANNEL_ID,
                "Transcript Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for completed transcriptions"
            }
            
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(transcriptChannel)
            
            Logger.ui("Notification channels created successfully")
        } else {
            Logger.ui("Notification channels not created (Android version < O)")
        }
    }
    
    private fun scheduleAutoDeleteWorker() {
        Logger.ui("Scheduling auto-delete worker")
        
        // Create a periodic work request that runs once a day
        val autoDeleteWorkRequest = PeriodicWorkRequestBuilder<AutoDeleteWorker>(
            1, TimeUnit.DAYS
        ).build()
        
        // Schedule the work request
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            AUTO_DELETE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing work if it exists
            autoDeleteWorkRequest
        )
        
        Logger.ui("Auto-delete worker scheduled successfully")
    }
} 