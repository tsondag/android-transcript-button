package com.example.audiorecorder.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

object Logger {
    private const val TAG = "VoiceMemos"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private var logFile: File? = null
    private var isFileLoggingEnabled = false

    fun init(context: Context) {
        try {
            val logsDir = File(context.getExternalFilesDir(null), "logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }
            
            // Create a new log file for each session with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            logFile = File(logsDir, "voice_memos_$timestamp.log")
            
            // Write header to log file
            FileWriter(logFile, true).use { writer ->
                writer.append("=== Voice Memos Log Started at ${dateFormat.format(Date())} ===\n")
                writer.append("Android SDK: ${android.os.Build.VERSION.SDK_INT}\n")
                writer.append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
                writer.append("===========================================\n\n")
            }
            
            isFileLoggingEnabled = true
            log("Logger", "File logging initialized: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize file logging", e)
            isFileLoggingEnabled = false
        }
    }

    fun recording(message: String, throwable: Throwable? = null) {
        log("Recording", message, throwable)
    }

    fun storage(message: String, throwable: Throwable? = null) {
        log("Storage", message, throwable)
    }

    fun api(message: String, throwable: Throwable? = null) {
        log("API", message, throwable)
    }

    fun transcript(message: String, throwable: Throwable? = null) {
        log("Transcript", message, throwable)
    }

    fun ui(message: String, throwable: Throwable? = null) {
        log("UI", message, throwable)
    }

    fun error(message: String, throwable: Throwable? = null) {
        log("ERROR", message, throwable, isError = true)
    }

    private fun log(component: String, message: String, throwable: Throwable? = null, isError: Boolean = false) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp][$component] $message"
        
        // Log to Android system log
        if (throwable != null || isError) {
            Log.e(TAG, logMessage, throwable)
        } else {
            Log.d(TAG, logMessage)
        }
        
        // Log to file if enabled
        if (isFileLoggingEnabled && logFile != null) {
            try {
                FileWriter(logFile, true).use { writer ->
                    writer.append("$logMessage\n")
                    
                    if (throwable != null) {
                        PrintWriter(writer).use { printWriter ->
                            throwable.printStackTrace(printWriter)
                        }
                        writer.append("\n")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write to log file", e)
            }
        }
    }
    
    fun getLogFilePath(): String? {
        return logFile?.absolutePath
    }
    
    fun clearLogs(context: Context) {
        try {
            val logsDir = File(context.getExternalFilesDir(null), "logs")
            if (logsDir.exists()) {
                logsDir.listFiles()?.forEach { it.delete() }
                log("Logger", "All log files cleared")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear logs", e)
        }
    }
} 