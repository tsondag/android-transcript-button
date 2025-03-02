package com.example.audiorecorder.repository

import android.content.Context
import android.util.Log
import com.example.audiorecorder.SettingsFragment
import com.example.audiorecorder.api.ApiClient
import com.example.audiorecorder.api.TranscriptionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class TranscriptionRepository(private val context: Context) {
    private val TAG = "TranscriptionRepository"

    suspend fun transcribeAudioFile(audioFile: File): Result<TranscriptionResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting transcription for file: ${audioFile.absolutePath}")
            Log.d(TAG, "File exists: ${audioFile.exists()}")
            Log.d(TAG, "File size: ${audioFile.length()} bytes")
            Log.d(TAG, "File readable: ${audioFile.canRead()}")
            Log.d(TAG, "File extension: ${audioFile.extension}")
            
            if (!audioFile.exists() || !audioFile.canRead()) {
                return@withContext Result.failure(Exception("File is not accessible: ${audioFile.absolutePath}"))
            }
            
            if (audioFile.length() == 0L) {
                return@withContext Result.failure(Exception("File is empty: ${audioFile.absolutePath}"))
            }
            
            val requestFile = audioFile.asRequestBody("audio/mp3".toMediaType())
            
            // Check if auto-detect language is enabled
            val isAutoDetectLanguageEnabled = SettingsFragment.isAutoDetectLanguageEnabled(context)
            Log.d(TAG, "Auto-detect language enabled: $isAutoDetectLanguageEnabled")
            
            // Check if tagging audio events is enabled
            val isTagAudioEventsEnabled = SettingsFragment.isTagAudioEventsEnabled(context)
            Log.d(TAG, "Tag audio events enabled: $isTagAudioEventsEnabled")
            
            // Create parts list based on settings
            val parts = mutableListOf(
                MultipartBody.Part.createFormData("file", audioFile.name, requestFile),
                MultipartBody.Part.createFormData("model_id", "scribe_v1"),
                MultipartBody.Part.createFormData("tag_audio_events", isTagAudioEventsEnabled.toString())
            )
            
            // Only add language_code if auto-detect is disabled
            if (!isAutoDetectLanguageEnabled) {
                parts.add(MultipartBody.Part.createFormData("language_code", "en"))
                Log.d(TAG, "Using fixed language code: en")
            } else {
                Log.d(TAG, "Using auto-detect language (no language_code parameter)")
            }
            
            Log.d(TAG, "Making API request with parameters:")
            Log.d(TAG, "- File name: ${audioFile.name}")
            Log.d(TAG, "- Model ID: scribe_v1")
            Log.d(TAG, "- Tag audio events: $isTagAudioEventsEnabled")
            if (!isAutoDetectLanguageEnabled) {
                Log.d(TAG, "- Language code: en")
            }
            
            val response = ApiClient.elevenLabsApi.transcribeAudio(parts)
            
            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d(TAG, "Transcription successful: ${it.text}")
                    Log.d(TAG, "Detected language: ${it.language ?: "unknown"}")
                    Result.success(it)
                } ?: run {
                    Log.e(TAG, "Empty response body")
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = "Transcription failed: ${response.code()} ${response.message()}"
                Log.e(TAG, errorMsg)
                Log.e(TAG, "Error body: $errorBody")
                Result.failure(Exception("$errorMsg - $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription", e)
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            Result.failure(e)
        }
    }
} 