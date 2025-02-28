package com.example.audiorecorder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.audiorecorder.utils.Logger

class AudioRecorderApp : Application() {
    
    companion object {
        const val CHANNEL_ID = "AudioRecorderChannel"
        const val TRANSCRIPT_CHANNEL_ID = "TranscriptNotificationChannel"
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize logger first for better debugging
        Logger.init(this)
        Logger.ui("Application starting")
        
        // Create notification channels
        createNotificationChannels()
        
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
} 