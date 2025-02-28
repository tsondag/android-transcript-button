package com.example.audiorecorder.service

import android.content.Context
import com.example.audiorecorder.SettingsFragment
import com.example.audiorecorder.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

class TranscriptionService(private val context: Context) {
    private val client = OkHttpClient.Builder().build()
    
    suspend fun transcribeAudio(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        Logger.api("Starting transcription for file: ${audioFile.name}")
        
        try {
            // Log file details
            Logger.api("Audio file size: ${audioFile.length()} bytes")
            Logger.api("Audio file path: ${audioFile.absolutePath}")
            Logger.api("Audio file exists: ${audioFile.exists()}")
            Logger.api("Audio file can read: ${audioFile.canRead()}")
            Logger.api("Audio file extension: ${audioFile.extension}")
            Logger.api("Audio file last modified: ${java.util.Date(audioFile.lastModified())}")
            
            if (!audioFile.exists()) {
                val error = IOException("Audio file does not exist: ${audioFile.absolutePath}")
                Logger.api("Transcription failed - file does not exist", error)
                return@withContext Result.failure(error)
            }
            
            if (!audioFile.canRead()) {
                val error = IOException("Cannot read audio file: ${audioFile.absolutePath}")
                Logger.api("Transcription failed - file not readable", error)
                return@withContext Result.failure(error)
            }
            
            if (audioFile.length() == 0L) {
                val error = IOException("Audio file is empty: ${audioFile.absolutePath}")
                Logger.api("Transcription failed - file is empty", error)
                return@withContext Result.failure(error)
            }

            // Check if auto-detect language is enabled
            val isAutoDetectLanguageEnabled = SettingsFragment.isAutoDetectLanguageEnabled(context)
            Logger.api("Auto-detect language enabled: $isAutoDetectLanguageEnabled")

            // Create multipart request
            val requestBodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/m4a".toMediaType())
                )
                .addFormDataPart("model_id", "scribe_v1")
            
            // Only add language_code if auto-detect is disabled
            if (!isAutoDetectLanguageEnabled) {
                requestBodyBuilder.addFormDataPart("language_code", "en")
                Logger.api("Using fixed language code: en")
            } else {
                Logger.api("Using auto-detect language (no language_code parameter)")
            }
            
            val requestBody = requestBodyBuilder.build()

            Logger.api("Preparing API request to transcription service")
            Logger.api("API URL: $TRANSCRIPTION_API_URL")
            Logger.api("Request content type: ${requestBody.contentType()}")
            Logger.api("Request content length: ${requestBody.contentLength()} bytes")
            
            val request = Request.Builder()
                .url(TRANSCRIPTION_API_URL)
                .post(requestBody)
                .build()

            Logger.api("Sending request to transcription service")
            
            client.newCall(request).execute().use { response ->
                Logger.api("Received response: ${response.code} ${response.message}")
                Logger.api("Response headers: ${response.headers}")
                
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No error body"
                    val error = IOException("Unexpected response code: ${response.code}, body: $errorBody")
                    Logger.api("Transcription API request failed: ${response.code} - ${response.message}", error)
                    return@withContext Result.failure(error)
                }

                val responseBody = response.body?.string()
                if (responseBody == null) {
                    val error = IOException("Empty response from transcription service")
                    Logger.api("Empty response from transcription service", error)
                    return@withContext Result.failure(error)
                }

                Logger.api("Received successful response from transcription service")
                Logger.api("Response body: $responseBody")
                
                try {
                    val json = JSONObject(responseBody)
                    val transcript = json.getString("transcript")
                    val detectedLanguage = json.optString("language_code", "unknown")
                    Logger.transcript("Successfully parsed transcript (${transcript.length} characters)")
                    Logger.transcript("Detected language: $detectedLanguage")
                    Logger.transcript("Transcript content: ${transcript.take(100)}${if (transcript.length > 100) "..." else ""}")
                    
                    // Save the transcript to a file
                    try {
                        val transcriptFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.txt")
                        transcriptFile.writeText(transcript)
                        Logger.storage("Saved transcript to file: ${transcriptFile.absolutePath}")
                        Logger.storage("Transcript file size: ${transcriptFile.length()} bytes")
                        Logger.storage("Transcript file exists after save: ${transcriptFile.exists()}")
                    } catch (e: Exception) {
                        Logger.storage("Failed to save transcript to file", e)
                    }
                    
                    Result.success(transcript)
                } catch (e: Exception) {
                    Logger.api("Failed to parse transcription response", e)
                    Logger.api("Raw response body: $responseBody")
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Logger.api("Transcription request failed", e)
            Logger.api("Exception details: ${e.message}")
            Logger.api("Stack trace: ${e.stackTraceToString()}")
            Result.failure(e)
        }
    }

    companion object {
        private const val TRANSCRIPTION_API_URL = "https://api.example.com/transcribe" // Replace with actual API URL
    }
} 