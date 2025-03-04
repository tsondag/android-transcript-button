package com.example.audiorecorder.workers

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.audiorecorder.SettingsFragment
import com.example.audiorecorder.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Worker class that handles automatic deletion of old voice memo files
 * based on the user's preferences.
 */
class AutoDeleteWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AutoDeleteWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting auto-delete worker")
            
            // Check if auto-delete is enabled
            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val isAutoDeleteEnabled = prefs.getBoolean(SettingsFragment.PREF_AUTO_DELETE_ENABLED, false)
            
            if (!isAutoDeleteEnabled) {
                Log.d(TAG, "Auto-delete is disabled, skipping")
                return@withContext Result.success()
            }
            
            // Get the auto-delete period
            val autoDeletePeriod = prefs.getString(SettingsFragment.PREF_AUTO_DELETE_PERIOD, "1 month") ?: "1 month"
            val cutoffTime = calculateCutoffTime(autoDeletePeriod)
            
            if (cutoffTime == null) {
                Log.e(TAG, "Invalid auto-delete period: $autoDeletePeriod")
                return@withContext Result.failure()
            }
            
            // Get all voice memo files
            val voiceMemoDir = applicationContext.getExternalFilesDir(null)
            if (voiceMemoDir == null || !voiceMemoDir.exists()) {
                Log.d(TAG, "Voice memo directory does not exist")
                return@withContext Result.success()
            }
            
            // Filter for voice memo files
            val voiceMemoFiles = voiceMemoDir.listFiles { file ->
                file.isFile && file.name.startsWith("voice_memo_") && 
                (file.name.endsWith(".m4a") || file.name.endsWith(".mp3") || file.name.endsWith(".wav"))
            }
            
            if (voiceMemoFiles == null || voiceMemoFiles.isEmpty()) {
                Log.d(TAG, "No voice memo files found")
                return@withContext Result.success()
            }
            
            var deletedCount = 0
            
            // Delete files older than the cutoff time
            for (file in voiceMemoFiles) {
                if (file.lastModified() < cutoffTime) {
                    val success = file.delete()
                    if (success) {
                        deletedCount++
                        Log.d(TAG, "Deleted old voice memo: ${file.name}")
                    } else {
                        Log.e(TAG, "Failed to delete voice memo: ${file.name}")
                    }
                }
            }
            
            Log.d(TAG, "Auto-delete completed. Deleted $deletedCount files")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in auto-delete worker: ${e.message}")
            Result.failure()
        }
    }
    
    /**
     * Calculate the cutoff time based on the auto-delete period.
     * @param period The period string (e.g., "1 day", "1 week", "1 month", etc.)
     * @return The cutoff time in milliseconds, or null if the period is invalid
     */
    private fun calculateCutoffTime(period: String): Long? {
        val calendar = Calendar.getInstance()
        
        return when (period) {
            "1 hour" -> {
                calendar.add(Calendar.HOUR_OF_DAY, -1)
                calendar.timeInMillis
            }
            "4 hours" -> {
                calendar.add(Calendar.HOUR_OF_DAY, -4)
                calendar.timeInMillis
            }
            "1 day" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.timeInMillis
            }
            "1 week" -> {
                calendar.add(Calendar.WEEK_OF_YEAR, -1)
                calendar.timeInMillis
            }
            "1 month" -> {
                calendar.add(Calendar.MONTH, -1)
                calendar.timeInMillis
            }
            "3 months" -> {
                calendar.add(Calendar.MONTH, -3)
                calendar.timeInMillis
            }
            "6 months" -> {
                calendar.add(Calendar.MONTH, -6)
                calendar.timeInMillis
            }
            "1 year" -> {
                calendar.add(Calendar.YEAR, -1)
                calendar.timeInMillis
            }
            else -> null
        }
    }
} 