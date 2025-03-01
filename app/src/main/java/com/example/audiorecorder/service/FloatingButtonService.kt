package com.example.audiorecorder.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.audiorecorder.AudioRecorderApp
import com.example.audiorecorder.AudioRecorderApp.Companion.CHANNEL_ID
import com.example.audiorecorder.MainActivity
import com.example.audiorecorder.R
import com.example.audiorecorder.SettingsFragment
import com.example.audiorecorder.VoiceMemoActivity
import com.example.audiorecorder.floaty.CustomFloaty
import com.example.audiorecorder.receivers.CopyTranscriptReceiver
import com.example.audiorecorder.repository.TranscriptionRepository
import com.example.audiorecorder.utils.ClipboardUtils
import com.example.audiorecorder.utils.AccessibilityUtils
import com.example.audiorecorder.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.preference.PreferenceManager

/**
 * A service that displays a draggable floating action button over other apps.
 * The button can be used to start and stop audio recording.
 */
class FloatingButtonService : Service() {
    
    // Recording state
    private var isRecording = false
    private var isTranscribing = false
    private var transcriptionText: String? = null
    private var currentRecordingFile: File? = null
    private var mediaRecorder: MediaRecorder? = null
    
    // Custom floating button
    private var floatingButton: CustomFloaty? = null
    
    // Coroutine scope for background operations
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val transcriptionRepository = TranscriptionRepository(this)

    // Class-level variable to track if we've shown the transcribing toast
    private var hasShownTranscribingToast = false
    private var recordingStartTime = 0L

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating FloatingButtonService")
        
        try {
            if (!checkOverlayPermission()) {
                Log.e(TAG, "Overlay permission not granted, stopping service")
                stopSelf()
                return
            }
            Log.d(TAG, "Overlay permission check passed")
            
            // Check if a button already exists
            if (isButtonActive) {
                Log.d(TAG, "Floating button already exists, not creating a new one")
            } else {
                setupFloatingButton()
            }
            
            Log.d(TAG, "Service initialization completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during service initialization", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FloatingButtonService onStartCommand")
        
        if (!checkOverlayPermission()) {
            Log.e(TAG, "Overlay permission not granted")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            // Check if a button already exists
            if (isButtonActive) {
                Log.d(TAG, "Floating button already exists, not creating a new one")
            } else {
                setupFloatingButton()
            }
            
            // Check if we should use notification (optional)
            val useNotification = intent?.getBooleanExtra("use_notification", true) ?: true
            
            if (useNotification) {
                startForeground(NOTIFICATION_ID, createNotification())
                Log.d(TAG, "Started service with notification")
            } else {
                // For Android 12+ we still need a notification, but we can make it silent
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val silentNotification = createSilentNotification()
                    startForeground(NOTIFICATION_ID, silentNotification)
                    Log.d(TAG, "Started service with silent notification (Android 12+)")
                } else {
                    // For older versions, we can run without a notification
                    Log.d(TAG, "Started service without notification")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating floating button", e)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun setupFloatingButton() {
        try {
            Log.d(TAG, "Creating floating button")
            
            // Create and show the floating button
            floatingButton = CustomFloaty(
                context = this,
                buttonIconResId = if (isRecording) R.drawable.ic_stop else R.drawable.ic_mic,
                buttonBackgroundColor = if (isRecording) android.graphics.Color.RED else 0xFF6200EE.toInt(),
                buttonIconTint = android.graphics.Color.WHITE,
                buttonSize = 72,
                onButtonClick = {
                    toggleRecording()
                }
            )
            
            floatingButton?.show()
            isButtonActive = true
            
            Log.d(TAG, "Floating button successfully added to window manager")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating floating button", e)
            isButtonActive = false
            stopSelf()
        }
    }

    private fun toggleRecording() {
        if (!checkAudioPermission()) {
            Log.e(TAG, "Audio permission not granted")
            
            // Create an intent to request audio permission
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("REQUEST_AUDIO_PERMISSION", true)
            }
            startActivity(intent)
            return
        }

        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
        isRecording = !isRecording
        
        // Update UI
        updateFloatingButton()
    }

    private fun updateFloatingButton() {
        // Update the floating button icon and color based on recording state
        floatingButton?.updateIcon(if (isRecording) R.drawable.ic_stop else R.drawable.ic_mic)
        floatingButton?.updateBackgroundColor(if (isRecording) android.graphics.Color.RED else 0xFF6200EE.toInt())
        
        // Show transcription text if available
        if (isTranscribing) {
            // Only show "Transcribing..." toast for recordings longer than 30 seconds
            // and only show it once per transcription
            val recordingDurationSeconds = (System.currentTimeMillis() - recordingStartTime) / 1000
            if (recordingDurationSeconds > 30 && !hasShownTranscribingToast) {
                Toast.makeText(this, "Transcribing...", Toast.LENGTH_SHORT).show()
                hasShownTranscribingToast = true
            }
        } else if (transcriptionText != null && !isRecording) {
            Toast.makeText(this, transcriptionText, Toast.LENGTH_LONG).show()
        }
    }

    private fun startRecording() {
        if (!checkAudioPermission()) {
            Log.e(TAG, "Cannot start recording - no permission")
            return
        }

        // Reset the toast flag when starting a new recording
        hasShownTranscribingToast = false
        // Record the start time
        recordingStartTime = System.currentTimeMillis()

        // Verify microphone access by creating a temporary AudioRecord instance
        // This ensures the AppOps for RECORD_AUDIO is properly registered
        verifyMicrophoneAccess()

        // Clear any previous transcription text
        transcriptionText = null

        // Use the same timestamp format as before
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "voice_memo_$timestamp.m4a"
        // Save to the app's external files directory
        currentRecordingFile = File(getExternalFilesDir(null), fileName)
        Log.d(TAG, "Starting recording to file: ${currentRecordingFile?.absolutePath}")

        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Log.d(TAG, "Using Android 12+ MediaRecorder constructor")
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                Log.d(TAG, "Using legacy MediaRecorder constructor")
                MediaRecorder()
            }.apply {
                Log.d(TAG, "Configuring MediaRecorder...")
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000) // 128 kbps for better quality
                setAudioSamplingRate(44100) // CD quality
                setOutputFile(currentRecordingFile?.absolutePath)
                Log.d(TAG, "Preparing MediaRecorder...")
                prepare()
                Log.d(TAG, "Starting MediaRecorder...")
                start()
            }
            Log.d(TAG, "Recording started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}", e)
            mediaRecorder?.release()
            mediaRecorder = null
            currentRecordingFile = null
        }
    }

    /**
     * Verify microphone access by creating a temporary AudioRecord instance.
     * This ensures the AppOps for RECORD_AUDIO is properly registered.
     */
    private fun verifyMicrophoneAccess() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Unable to determine minimum buffer size for AudioRecord")
            return
        }
        
        var audioRecord: AudioRecord? = null
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize
            )
            
            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                Log.d(TAG, "AudioRecord initialized successfully - microphone access verified")
                audioRecord.startRecording()
                // Just record a tiny bit to ensure the app op is registered
                val buffer = ByteArray(minBufferSize)
                audioRecord.read(buffer, 0, minBufferSize)
                audioRecord.stop()
            } else {
                Log.e(TAG, "AudioRecord initialization failed - microphone access may be restricted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying microphone access: ${e.message}", e)
        } finally {
            audioRecord?.release()
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            try {
                Log.d(TAG, "Stopping recording...")
                stop()
                release()
                Log.d(TAG, "Recording stopped successfully")
                
                // Start transcription
                currentRecordingFile?.let { file ->
                    Log.d(TAG, "Starting transcription for file: ${file.absolutePath}")
                    transcribeRecording(file)
                } ?: run {
                    Log.e(TAG, "Cannot start transcription - recording file is null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording: ${e.message}", e)
            }
        }
        mediaRecorder = null
    }

    private fun transcribeRecording(audioFile: File) {
        isTranscribing = true
        transcriptionText = "Transcribing..."
        updateFloatingButton()
        
        Logger.recording("Starting transcription process for file: ${audioFile.absolutePath}")
        Logger.recording("File exists: ${audioFile.exists()}, File size: ${audioFile.length()} bytes")
        
        if (!audioFile.exists() || audioFile.length() == 0L) {
            val errorMsg = "Audio file is missing or empty"
            Logger.recording(errorMsg)
            transcriptionText = errorMsg
            isTranscribing = false
            updateFloatingButton()
            return
        }
        
        serviceScope.launch {
            try {
                Logger.api("Making transcription API request...")
                val result = transcriptionRepository.transcribeAudioFile(audioFile)
                
                result.fold(
                    onSuccess = { response ->
                        Logger.transcript("Transcription successful: ${response.text}")
                        transcriptionText = response.text
                        
                        // Update UI
                        withContext(Dispatchers.Main) {
                            updateFloatingButton()
                        }
                        
                        // Save transcript to a file with the same base name as the audio file
                        try {
                            val transcriptFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.txt")
                            transcriptFile.writeText(response.text)
                            Logger.storage("Saved transcript to file: ${transcriptFile.absolutePath}")
                            
                            // Note: Notification removed as requested
                            
                            // Auto-copy to clipboard if enabled in settings
                            val prefs = PreferenceManager.getDefaultSharedPreferences(this@FloatingButtonService)
                            val isAutoCopyEnabled = prefs.getBoolean(SettingsFragment.PREF_AUTO_COPY_ENABLED, false)
                            if (isAutoCopyEnabled) {
                                Logger.ui("Auto-copy enabled, copying transcript to clipboard")
                                withContext(Dispatchers.Main) {
                                    ClipboardUtils.copyToClipboard(
                                        context = this@FloatingButtonService,
                                        text = response.text,
                                        label = "Transcript: ${audioFile.nameWithoutExtension}",
                                        showToast = true
                                    )
                                }
                            }
                            
                            // Auto-insert to current input field if enabled in settings
                            val isAutoInsertEnabled = prefs.getBoolean(SettingsFragment.PREF_AUTO_INSERT_ENABLED, false)
                            if (isAutoInsertEnabled) {
                                Logger.ui("Auto-insert enabled, inserting transcript into current input field")
                                withContext(Dispatchers.Main) {
                                    val success = AccessibilityUtils.insertTextIntoCurrentField(response.text)
                                    if (!success) {
                                        Logger.error("Failed to auto-insert text into input field")
                                        Toast.makeText(
                                            this@FloatingButtonService,
                                            "Failed to insert text. Make sure an input field is focused.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Logger.error("Failed to save transcript to file", e)
                        }
                    },
                    onFailure = { error ->
                        val errorMsg = "Transcription failed: ${error.message}"
                        Logger.error(errorMsg, error)
                        transcriptionText = errorMsg
                        
                        withContext(Dispatchers.Main) {
                            updateFloatingButton()
                        }
                    }
                )
            } catch (e: Exception) {
                val errorMsg = "Error during transcription: ${e.message}"
                Logger.error(errorMsg, e)
                transcriptionText = errorMsg
                
                withContext(Dispatchers.Main) {
                    updateFloatingButton()
                }
            } finally {
                Logger.recording("Transcription process completed")
                isTranscribing = false
                updateFloatingButton()
            }
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun checkAudioPermission(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasPermission) {
            Log.d(TAG, "Audio permission check passed - RECORD_AUDIO permission granted")
        } else {
            Log.e(TAG, "Audio permission check failed - RECORD_AUDIO permission not granted")
        }
        return hasPermission
    }

    override fun onDestroy() {
        Log.d(TAG, "FloatingButtonService onDestroy - Starting cleanup")
        try {
            super.onDestroy()
            if (isRecording) {
                Log.d(TAG, "Stopping ongoing recording")
                stopRecording()
            }
            serviceScope.cancel()
            
            // Remove the floating button
            floatingButton?.remove()
            floatingButton = null
            isButtonActive = false
            
            Log.d(TAG, "Floating button removed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during service cleanup", e)
        }
        Log.d(TAG, "Service cleanup completed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio Recorder")
            .setContentText("Recording service is active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createSilentNotification(): Notification {
        // Create a silent version of the notification for when notification permission is not granted
        val notificationIntent = Intent(this, VoiceMemoActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Recorder")
            .setContentText("Recording service is running")
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN) // Minimum priority
            .setSilent(true) // Make it silent
            .build()
    }

    companion object {
        private const val TAG = "AudioRecorder"
        private const val NOTIFICATION_ID = 1
        private const val TRANSCRIPT_NOTIFICATION_ID = 2
        
        // Static flag to track if a button is already active
        @Volatile
        private var isButtonActive = false
    }
} 