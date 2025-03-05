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
import android.widget.RemoteViews
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
import com.example.audiorecorder.floaty.EnhancedFloatingButton
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
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager

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
    
    // Enhanced floating button
    private var floatingButton: EnhancedFloatingButton? = null
    
    // UI state tracking
    private var isButtonActive = false
    private var isKeyboardVisible = false
    private var isAppMinimized = false
    
    // Coroutine scope for background operations
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val transcriptionRepository = TranscriptionRepository(this)

    // Class-level variable to track if we've shown the transcribing toast
    private var hasShownTranscribingToast = false
    private var recordingStartTime = 0L

    // Timer update handler
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    private var notificationUpdateHandler: Handler? = null
    private val notificationUpdateRunnable = object : Runnable {
        override fun run() {
            // Update the notification to ensure it stays fresh and visible
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createNotification())
            
            // Schedule the next update
            notificationUpdateHandler?.postDelayed(this, NOTIFICATION_UPDATE_INTERVAL)
        }
    }

    private var stopRecordingConfirmDialogShown = false
    private var windowManager: WindowManager? = null
    private var floatingButtonParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingButtonService onCreate")
        
        try {
            // Set initial values for UI components
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            // Only create the button if it doesn't exist yet
            if (isButtonActive) {
                Log.d(TAG, "Floating button already exists, not creating a new one")
            } else {
                setupFloatingButton()
            }
            
            // Initialize notification update handler
            notificationUpdateHandler = Handler(Looper.getMainLooper())
            
            // Create and display the persistent notification
            startForeground(NOTIFICATION_ID, createNotification())
            
            // Force an immediate notification update to ensure custom view is displayed
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createNotification())
            
            Log.d(TAG, "Service initialization completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during service initialization", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FloatingButtonService onStartCommand")
        
        try {
            // Handle enable/disable actions
            if (intent?.action == ACTION_ENABLE) {
                Log.d(TAG, "Enabling floating button service")
                SettingsFragment.setNotificationToggleEnabled(this, true)
                
                // Update notification to reflect the new state
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                val updatedNotification = createNotification()
                notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                
                // Apply the visibility change immediately
                updateButtonVisibilityBasedOnSettings()
                
                return START_STICKY
            } else if (intent?.action == ACTION_DISABLE) {
                Log.d(TAG, "Disabling floating button service")
                SettingsFragment.setNotificationToggleEnabled(this, false)
                
                // Update notification to reflect the new state
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                val updatedNotification = createNotification()
                notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                
                // Apply the visibility change immediately
                updateButtonVisibilityBasedOnSettings()
                
                return START_STICKY
            }
            
            // Check if this is a toggle button visibility command
            if (intent?.action == ACTION_TOGGLE_BUTTON_VISIBILITY) {
                Log.d(TAG, "Toggling button visibility from notification")
                val currentState = SettingsFragment.isNotificationToggleEnabled(this)
                val newState = !currentState
                SettingsFragment.setNotificationToggleEnabled(this, newState)
                
                // Update notification to reflect the new state
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                val updatedNotification = createNotification()
                notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                
                // Apply the visibility change immediately if appropriate
                updateButtonVisibilityBasedOnSettings()
                
                return START_STICKY
            }
            
            // Handle toggle recording action from notification
            if (intent?.action == ACTION_TOGGLE_RECORDING) {
                Log.d(TAG, "Toggling recording from notification")
                
                // Check for audio permission first
                if (!checkAudioPermission()) {
                    Log.e(TAG, "Audio permission not granted")
                    
                    // Create an intent to request audio permission
                    val permissionIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("REQUEST_AUDIO_PERMISSION", true)
                    }
                    startActivity(permissionIntent)
                    return START_STICKY
                }
                
                // Toggle recording state
                if (isRecording) {
                    stopRecording()
                } else {
                    startRecording()
                }
                isRecording = !isRecording
                
                // Update UI based on recording state
                updateFloatingButton()
                
                // Update notification to reflect the new state
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                val updatedNotification = createNotification()
                notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                
                return START_STICKY
            }
            
            // Start periodic notification updates if not already started
            startNotificationUpdates()

            // Handle keyboard/input field state updates
            if (intent?.getBooleanExtra("KEYBOARD_INPUT_STATE_CHANGED", false) == true) {
                try {
                    val shouldShow = intent.getBooleanExtra("SHOW_FLOATING_BUTTON", false)
                    val isAppMinimized = intent.getBooleanExtra("IS_APP_MINIMIZED", false)
                    Log.d(TAG, "Received keyboard/input field state update: shouldShow=$shouldShow, isRecording=$isRecording, isAppMinimized=$isAppMinimized")
                    
                    // Determine if we should show the button based on state and preferences
                    var shouldActuallyShow = shouldShow
                    
                    // First check if the notification toggle is enabled, if not, we never show the button
                    // unless we're recording (for UI clarity)
                    if (!SettingsFragment.isNotificationToggleEnabled(this) && !isRecording) {
                        shouldActuallyShow = false
                        Log.d(TAG, "Button visibility disabled via notification toggle")
                    } 
                    // Apply smart button behavior if enabled (always show when recording)
                    else if (SettingsFragment.isSmartButtonBehaviorEnabled(this)) {
                        // Key logic: Button should show if (keyboard is active AND app is not minimized) OR we are recording
                        shouldActuallyShow = (shouldShow && !isAppMinimized) || isRecording
                        Log.d(TAG, "Smart button behavior enabled, adjusted shouldActuallyShow to: $shouldActuallyShow")
                    }
                    
                    // Since keyboard-only mode is now always enabled and integrated into Smart Button Behavior
                    Log.d(TAG, "Smart button mode active: $isButtonActive, should show: $shouldActuallyShow")
                    
                    // Force immediate hiding on minimization for better responsiveness
                    val forceImmediateHide = isAppMinimized && 
                                           SettingsFragment.isSmartButtonBehaviorEnabled(this) && 
                                           !isRecording
                    
                    if (shouldActuallyShow) {
                        if (!isButtonActive) {
                            // Setup the button - this will restore its last position from SharedPreferences
                            setupFloatingButton()
                            // Button is shown during setup
                            Log.d(TAG, "Showing floating button")
                        }
                    } else {
                        if (isButtonActive) {
                            // Handle immediate removal
                            if (forceImmediateHide) {
                                removeFloatingButton()
                                Log.d(TAG, "Immediately removing floating button due to minimization")
                            } else {
                                // Just remove the button
                                removeFloatingButton()
                                Log.d(TAG, "Hiding floating button")
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Catch and log any exceptions to prevent service crashes
                    Log.e(TAG, "Error handling keyboard visibility change", e)
                }
                
                return START_STICKY
            }
            
            // Regular service start handling continues
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
                
                // Create a notification first before trying to start foreground
                try {
                    // Use full custom notification right away instead of simple one
                    val notification = createNotification()
                    startForeground(NOTIFICATION_ID, notification)
                    Log.d(TAG, "Started service with custom notification")
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting foreground service", e)
                    stopSelf()
                    return START_NOT_STICKY
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating floating button or notification", e)
                stopSelf()
                return START_NOT_STICKY
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in onStartCommand", e)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun setupFloatingButton() {
        try {
            // Create a new floating button
            floatingButton = EnhancedFloatingButton(
                context = this,
                onButtonClick = {
                    if (!isRecording) {
                        startRecording()
                    } else {
                        stopRecording()
                    }
                }
            )
            
            floatingButton?.show()
            
            // Update our tracking flag
            isButtonActive = true
            Log.d(TAG, "Floating button created and shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating floating button", e)
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
        
        // Update UI based on recording state
        updateFloatingButton()
    }

    private fun updateFloatingButton() {
        // Update the floating button state based on recording state
        if (isRecording) {
            floatingButton?.updateState(EnhancedFloatingButton.RecordingState.RECORDING)
        } else if (isTranscribing) {
            // Don't update button state here, let the transcription completion handle it
            
            // Only show "Transcribing..." toast for recordings longer than 30 seconds
            // and only show it once per transcription
            val recordingDurationSeconds = (System.currentTimeMillis() - recordingStartTime) / 1000
            if (recordingDurationSeconds > 30 && !hasShownTranscribingToast) {
                Toast.makeText(this, "Transcribing...", Toast.LENGTH_SHORT).show()
                hasShownTranscribingToast = true
            }
        } else if (transcriptionText != null) {
            // Show "Saving" state when transcription is done
            floatingButton?.updateState(EnhancedFloatingButton.RecordingState.STOPPING)
        } else {
            // Return to idle state
            floatingButton?.updateState(EnhancedFloatingButton.RecordingState.IDLE)
        }
    }

    private fun startRecording() {
        try {
            Log.d(TAG, "Starting recording...")

            // Reset state for new recording
            transcriptionText = null
            hasShownTranscribingToast = false
            currentRecordingFile = createAudioFile()
            recordingStartTime = System.currentTimeMillis()
            
            // Set up timer update
            setupTimerUpdates()

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
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
            isRecording = false
            mediaRecorder?.release()
            mediaRecorder = null
            
            // Show error state on button
            floatingButton?.updateState(EnhancedFloatingButton.RecordingState.IDLE)
        }
    }

    private fun stopRecording() {
            try {
            // Update button state immediately to show stopping state
            floatingButton?.updateState(EnhancedFloatingButton.RecordingState.STOPPING)
            
            // Cancel the timer update
            timerRunnable?.let { handler.removeCallbacks(it) }
            timerRunnable = null
            
            // Stop the MediaRecorder
            mediaRecorder?.apply {
                try {
                    stop()
                    reset()
                    release()
                } catch (e: Exception) {
                    // Ignore runtime exceptions when stopping recorder
                    Log.e(TAG, "Error stopping media recorder", e)
                }
            }
            mediaRecorder = null
            
            Logger.recording("Recording stopped")
            
            // Process the recording file if it exists
            currentRecordingFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    Logger.recording("Valid recording file found: ${file.absolutePath}")
                    transcribeRecording(file)
                } else {
                    Logger.recording("Invalid or empty recording file")
                    transcriptionText = null // Make sure we reset this
                    floatingButton?.updateState(EnhancedFloatingButton.RecordingState.IDLE)
                }
            } ?: run {
                Logger.recording("No recording file to process")
                transcriptionText = null // Make sure we reset this
                floatingButton?.updateState(EnhancedFloatingButton.RecordingState.IDLE)
            }
            
        } catch (e: Exception) {
            Logger.error("Error stopping recording", e)
            transcriptionText = null // Make sure we reset this
            floatingButton?.updateState(EnhancedFloatingButton.RecordingState.IDLE)
        }
    }
    
    private fun setupTimerUpdates() {
        // Cancel any existing timer updates
        timerRunnable?.let { handler.removeCallbacks(it) }
        
        timerRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    // Calculate elapsed time in milliseconds
                    val elapsedTime = System.currentTimeMillis() - recordingStartTime
                    
                    // Update timer display on the enhanced floating button
                    floatingButton?.updateTimer(elapsedTime)
                    
                    // Schedule next update in 1 second
                    handler.postDelayed(this, 1000)
                }
            }
        }
        
        // Start timer updates immediately
        handler.post(timerRunnable!!)
    }

    private fun transcribeRecording(audioFile: File) {
        isTranscribing = true
        transcriptionText = "Transcribing..."
        // Update button to stopping state to show checkmark during transcription
        floatingButton?.updateState(EnhancedFloatingButton.RecordingState.STOPPING)
        
        Logger.recording("Starting transcription process for file: ${audioFile.absolutePath}")
        Logger.recording("File exists: ${audioFile.exists()}, File size: ${audioFile.length()} bytes")
        
        if (!audioFile.exists() || audioFile.length() == 0L) {
            val errorMsg = "Audio file is missing or empty"
            Logger.recording(errorMsg)
            transcriptionText = null // Reset this instead of setting error text
            isTranscribing = false
            floatingButton?.updateState(EnhancedFloatingButton.RecordingState.IDLE)
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
                        isTranscribing = false
                        
                        // Update UI to show saved state
                        withContext(Dispatchers.Main) {
                            floatingButton?.updateState(EnhancedFloatingButton.RecordingState.SAVED)
                            
                            // Schedule reset of transcriptionText after animation completes (3s total)
                            handler.postDelayed({
                                transcriptionText = null
                                Log.d(TAG, "Reset transcriptionText to null after saved animation")
                            }, 3000)
                        }
                        
                        // Save transcript to a file with the same base name as the audio file
                        try {
                            val transcriptFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.txt")
                            transcriptFile.writeText(response.text)
                            Logger.storage("Saved transcript to file: ${transcriptFile.absolutePath}")
                            
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
                        transcriptionText = null // Reset this instead of setting error text
                        
                        withContext(Dispatchers.Main) {
                            // Return to idle state on error
                            floatingButton?.updateState(EnhancedFloatingButton.RecordingState.IDLE)
                        }
                    }
                )
            } catch (e: Exception) {
                val errorMsg = "Error during transcription: ${e.message}"
                Logger.error(errorMsg, e)
                transcriptionText = null // Reset this instead of setting error text
                
                withContext(Dispatchers.Main) {
                    // Return to idle state on error
                    floatingButton?.updateState(EnhancedFloatingButton.RecordingState.IDLE)
                }
            } finally {
                Logger.recording("Transcription process completed")
                isTranscribing = false
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
        super.onDestroy()
        Log.d(TAG, "FloatingButtonService onDestroy")
        
        // Clean up timer if service is destroyed
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
        
        // Release mediaRecorder if still active
        if (isRecording) {
            try {
                mediaRecorder?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping media recorder during service destroy", e)
            }
            mediaRecorder?.release()
            mediaRecorder = null
        }
        
        // Cancel any coroutines
        serviceScope.cancel()
        
        // Remove the floating button
        removeFloatingButton()

        // Remove notification update callbacks
        notificationUpdateHandler?.removeCallbacks(notificationUpdateRunnable)
        notificationUpdateHandler = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        // Create an intent for the notification to open the main activity when clicked
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create toggle intent
        val toggleIntent = Intent(this, FloatingButtonService::class.java).apply {
            action = ACTION_TOGGLE_BUTTON_VISIBILITY
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            0,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create intent to view memos
        val memosIntent = Intent(this, VoiceMemoActivity::class.java)
        val memosPendingIntent = PendingIntent.getActivity(
            this, 0, memosIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Determine button states and labels based on the microphone state
        val isMicEnabled = SettingsFragment.isNotificationToggleEnabled(this)
        val toggleActionTitle = if (isMicEnabled) "Hide Button" else "Show Button"
        
        // Build the notification with standard actions
        return NotificationCompat.Builder(this, AudioRecorderApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Audio Recorder")
            .setContentText(if (isRecording) "Recording in progress..." else "Service is running")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_view,
                toggleActionTitle,
                togglePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_recent_history,
                "View Memos",
                memosPendingIntent
            )
            .build()
    }

    private fun createSilentNotification(): Notification {
        // Create a basic low-priority silent notification
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, AudioRecorderApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Audio Recorder")
            .setContentText("Service is running")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun createSimpleNotification(): Notification {
        // Create a basic notification with minimal features
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, AudioRecorderApp.CHANNEL_ID)
            .setContentTitle("Audio Recorder")
            .setContentText("Service is running")
            .setSmallIcon(R.drawable.ic_mic)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun removeFloatingButton() {
        try {
            floatingButton?.remove()
            isButtonActive = false
            Log.d(TAG, "Floating button removed")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing floating button", e)
        }
    }

    // Handle keyboard visibility changes to show/hide the floating button
    private fun handleKeyboardVisibility(keyboardVisible: Boolean) {
        try {
            Log.d(TAG, "handleKeyboardVisibility - Keyboard visible: $keyboardVisible, Recording: $isRecording")
            
            // Get app minimization state
            val isAppMinimized = SettingsFragment.isAppMinimized(this)
            Log.d(TAG, "Received keyboard visibility change: keyboardVisible=$keyboardVisible, isRecording=$isRecording, isAppMinimized=$isAppMinimized")
            
            // Determine if we should show the button based on state and preferences
            var shouldActuallyShow = keyboardVisible
            
            // Apply smart button behavior if enabled (always show when recording)
            if (SettingsFragment.isSmartButtonBehaviorEnabled(this)) {
                // Key logic: Button should show if (keyboard is active AND app is not minimized) OR we are recording
                shouldActuallyShow = (keyboardVisible && !isAppMinimized) || isRecording
                Log.d(TAG, "Smart button behavior enabled, adjusted shouldActuallyShow to: $shouldActuallyShow")
            }
            
            Log.d(TAG, "Button currently active: $isButtonActive, should show: $shouldActuallyShow")
            
            // Force immediate hiding on minimization for better responsiveness
            val forceImmediateHide = isAppMinimized && 
                                   SettingsFragment.isSmartButtonBehaviorEnabled(this) && 
                                   !isRecording
            
            if (shouldActuallyShow) {
                if (!isButtonActive) {
                    // Setup the button - this will restore its last position from SharedPreferences
                    setupFloatingButton()
                    // Button is shown during setup
                    Log.d(TAG, "Showing floating button")
                }
            } else {
                if (isButtonActive) {
                    // Handle immediate removal
                    if (forceImmediateHide) {
                        removeFloatingButton()
                        Log.d(TAG, "Immediately removing floating button due to minimization")
                    } else {
                        // Just remove the button
                        removeFloatingButton()
                        Log.d(TAG, "Hiding floating button")
                    }
                }
            }
        } catch (e: Exception) {
            // Catch and log any exceptions to prevent service crashes
            Log.e(TAG, "Error handling keyboard visibility change", e)
        }
    }

    private fun createAudioFile(): File {
        // Use the same timestamp format as before
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "voice_memo_$timestamp.m4a"
        // Save to the app's external files directory
        val file = File(getExternalFilesDir(null), fileName)
        Log.d(TAG, "Creating recording file: ${file.absolutePath}")
        return file
    }

    /**
     * Verify microphone access by creating a temporary AudioRecord instance.
     * This ensures the AppOps for RECORD_AUDIO is properly registered.
     */
    private fun verifyMicrophoneAccess() {
        // ... existing code ...
    }

    private fun updateFloatingButtonVisibility() {
        // Simple implementation that just shows/hides based on keyboard visibility
        if (isKeyboardVisible && !isRecording) {
            // Hide floating button when keyboard is visible and not recording
            floatingButton?.remove()
        } else {
            // Show floating button when keyboard is hidden or recording
            if (isButtonActive && floatingButton == null) {
                setupFloatingButton()
            } else {
                floatingButton?.show()
            }
        }
    }

    /**
     * Temporarily hides the floating button during IME operations
     */
    private fun hideFloatingButton() {
        Log.d(TAG, "Hiding floating button due to IME operation")
        floatingButton?.remove()
    }

    /**
     * Restores the floating button after IME operations
     */
    private fun restoreFloatingButton() {
        if (isButtonActive && !isKeyboardVisible) {
            Log.d(TAG, "Restoring floating button after IME operation")
            setupFloatingButton()
        }
    }

    /**
     * Updates button visibility based on notification toggle and other settings
     */
    private fun updateButtonVisibilityBasedOnSettings() {
        val isEnabled = SettingsFragment.isNotificationToggleEnabled(this)
        
        if (isEnabled && !isButtonActive) {
            // If button isn't active but should be (toggle on)
            setupFloatingButton()
            Log.d(TAG, "Setting up floating button because toggle is enabled")
        } else if (!isEnabled && isButtonActive) {
            // If button is active but shouldn't be (toggle off)
            removeFloatingButton()
            Log.d(TAG, "Removing floating button because toggle is disabled")
        } else if (isEnabled && isRecording && !isButtonActive) {
            // If button isn't active but should be (toggle on + recording)
            setupFloatingButton()
            Log.d(TAG, "Setting up floating button because toggle is enabled and recording is active")
        }
        
        // Update notification to reflect the current state
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun startNotificationUpdates() {
        // Cancel any existing updates
        notificationUpdateHandler?.removeCallbacks(notificationUpdateRunnable)
        
        // Start periodic updates
        notificationUpdateHandler?.postDelayed(notificationUpdateRunnable, NOTIFICATION_UPDATE_INTERVAL)
    }

    companion object {
        private const val TAG = "FloatingButtonService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "AudioRecorderChannel"
        
        // Action constants
        const val ACTION_TOGGLE_BUTTON_VISIBILITY = "com.example.audiorecorder.TOGGLE_BUTTON_VISIBILITY"
        const val ACTION_TOGGLE_RECORDING = "com.example.audiorecorder.TOGGLE_RECORDING"
        const val ACTION_OPEN_SETTINGS = "com.example.audiorecorder.OPEN_SETTINGS"
        const val ACTION_ENABLE = "com.example.audiorecorder.ENABLE"
        const val ACTION_DISABLE = "com.example.audiorecorder.DISABLE"
        
        // Static flag to track if a button is already active
        @Volatile
        private var isButtonActive = false

        // Update notification more frequently (every 5 seconds during recording, otherwise every 15 minutes)
        private const val NOTIFICATION_UPDATE_INTERVAL = 15 * 60 * 1000L // 15 minutes in milliseconds
    }
} 