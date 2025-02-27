package com.example.audiorecorder.api

import android.util.Log
import com.example.audiorecorder.config.Config
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "https://api.elevenlabs.io/"
    private const val TAG = "ApiClient"
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("xi-api-key", Config.ELEVEN_LABS_API_KEY)
                .method(original.method, original.body)
                .build()
            
            Log.d(TAG, "Making request to: ${request.url}")
            Log.d(TAG, "Headers: ${request.headers}")
            
            val response = chain.proceed(request)
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Request failed: ${response.code} ${response.message}")
                Log.e(TAG, "Error body: ${response.body?.string()}")
            }
            
            response
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val elevenLabsApi: ElevenLabsApi = retrofit.create(ElevenLabsApi::class.java)
} 