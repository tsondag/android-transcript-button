package com.example.audiorecorder.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.example.audiorecorder.utils.Logger
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class VoiceRecordingService(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun startRecording(): Result<File> {
        Logger.recording("Starting new recording")
        
        return try {
            val outputFile = createOutputFile()
            Logger.storage("Created output file: ${outputFile.absolutePath}")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                
                try {
                    prepare()
                    Logger.recording("MediaRecorder prepared successfully")
                } catch (e: IOException) {
                    Logger.recording("Failed to prepare MediaRecorder", e)
                    throw e
                }
                
                try {
                    start()
                    Logger.recording("Recording started successfully")
                } catch (e: IllegalStateException) {
                    Logger.recording("Failed to start recording", e)
                    throw e
                }
            }

            currentRecordingFile = outputFile
            Result.success(outputFile)
        } catch (e: Exception) {
            Logger.recording("Failed to start recording", e)
            Result.failure(e)
        }
    }

    fun stopRecording(): Result<File> {
        Logger.recording("Stopping recording")
        
        return try {
            mediaRecorder?.apply {
                try {
                    stop()
                    Logger.recording("Recording stopped successfully")
                } catch (e: IllegalStateException) {
                    Logger.recording("Failed to stop recording", e)
                    throw e
                }
                
                release()
                Logger.recording("MediaRecorder released")
            }
            mediaRecorder = null

            currentRecordingFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    Logger.storage("Recording saved successfully: ${file.absolutePath} (${file.length()} bytes)")
                    Result.success(file)
                } else {
                    val error = IOException("Recording file is empty or does not exist")
                    Logger.storage("Recording file validation failed", error)
                    Result.failure(error)
                }
            } ?: run {
                val error = IllegalStateException("No recording file available")
                Logger.recording("No recording file available", error)
                Result.failure(error)
            }
        } catch (e: Exception) {
            Logger.recording("Failed to stop recording", e)
            Result.failure(e)
        }
    }

    private fun createOutputFile(): File {
        val timestamp = dateFormat.format(Date())
        val fileName = "voice_memo_$timestamp.m4a"
        
        return File(context.getExternalFilesDir(null), fileName).also {
            Logger.storage("Creating new recording file: ${it.absolutePath}")
        }
    }
} 