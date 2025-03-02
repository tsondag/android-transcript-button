package com.example.audiorecorder.utils

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Manages audio playback functionality for voice memos
 * Handles playing, pausing, stopping, and provides state info
 */
class AudioPlaybackManager(private val context: Context) {
    
    private var mediaPlayer: MediaPlayer? = null
    private var currentFile: File? = null
    private var isPrepared = false
    private var playbackCallback: PlaybackCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    
    // Callback interface for playback events
    interface PlaybackCallback {
        fun onPlaybackStarted(file: File)
        fun onPlaybackStopped(file: File)
        fun onPlaybackCompleted(file: File)
        fun onPlaybackError(file: File, error: String)
    }
    
    fun setPlaybackCallback(callback: PlaybackCallback) {
        this.playbackCallback = callback
    }
    
    fun playAudio(file: File) {
        // If we're already playing this file, stop it (toggle behavior)
        if (isPlaying() && currentFile?.absolutePath == file.absolutePath) {
            stopPlayback()
            return
        }
        
        // If we were playing something else before, stop it
        stopPlayback()
        
        // Create a new MediaPlayer instance
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    isPrepared = true
                    start()
                    
                    currentFile = file
                    playbackCallback?.onPlaybackStarted(file)
                    Logger.ui("Started playback of: ${file.name}")
                }
                
                setOnCompletionListener {
                    val completedFile = currentFile
                    stopPlayback()
                    completedFile?.let { nonNullFile ->
                        playbackCallback?.onPlaybackCompleted(nonNullFile)
                        Logger.ui("Completed playback of: ${nonNullFile.name}")
                    }
                }
                
                setOnErrorListener { _, what, extra ->
                    val message = "MediaPlayer error: what=$what, extra=$extra"
                    Log.e("AudioPlaybackManager", message)
                    
                    val errorFile = currentFile
                    stopPlayback()
                    
                    errorFile?.let { nonNullFile ->
                        playbackCallback?.onPlaybackError(nonNullFile, message)
                        Logger.ui("Error during playback of: ${nonNullFile.name}")
                    }
                    true
                }
                
                // Start preparing asynchronously
                prepareAsync()
            } catch (e: IOException) {
                Log.e("AudioPlaybackManager", "Error setting data source", e)
                val errorMessage = "Error preparing audio for playback: ${e.message}"
                playbackCallback?.onPlaybackError(file, errorMessage)
                Logger.ui("Failed to prepare playback for: ${file.name}")
                reset()
                release()
                mediaPlayer = null
            }
        }
    }
    
    fun stopPlayback() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                isPrepared = false
                
                currentFile?.let { file ->
                    playbackCallback?.onPlaybackStopped(file)
                    Logger.ui("Stopped playback of: ${file.name}")
                }
            } catch (e: IllegalStateException) {
                Log.e("AudioPlaybackManager", "Error stopping playback", e)
            }
        }
    }
    
    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (e: IllegalStateException) {
            false
        }
    }
    
    fun getCurrentFile(): File? {
        return currentFile
    }
    
    fun release() {
        stopPlayback()
        mediaPlayer?.release()
        mediaPlayer = null
        currentFile = null
        isPrepared = false
    }
} 