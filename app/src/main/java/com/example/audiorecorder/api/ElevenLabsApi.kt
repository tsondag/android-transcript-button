package com.example.audiorecorder.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ElevenLabsApi {
    @Multipart
    @POST("v1/speech-to-text")
    suspend fun transcribeAudio(
        @Part parts: List<MultipartBody.Part>
    ): Response<TranscriptionResponse>
}

@Serializable
data class TranscriptionResponse(
    @SerialName("text") val text: String,
    @SerialName("language") val language: String? = null,
    @SerialName("confidence") val confidence: Double? = null
)

@Serializable
data class ErrorResponse(
    @SerialName("detail") val detail: String
) 