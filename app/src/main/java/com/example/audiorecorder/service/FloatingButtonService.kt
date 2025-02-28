package com.example.audiorecorder.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.example.audiorecorder.AudioRecorderApp
import com.example.audiorecorder.AudioRecorderApp.Companion.CHANNEL_ID
import com.example.audiorecorder.MainActivity
import com.example.audiorecorder.R
import com.example.audiorecorder.VoiceMemoActivity
import com.example.audiorecorder.repository.TranscriptionRepository
import com.example.audiorecorder.utils.Logger
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
import kotlin.math.abs

class FloatingButtonService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var rootLayout: FrameLayout
    private lateinit var buttonView: ImageButton
    private lateinit var transcriptionTextView: TextView
    
    private var isRecording = false
    private var isTranscribing = false
    private var transcriptionText: String? = null
    private var currentRecordingFile: File? = null
    private var mediaRecorder: MediaRecorder? = null
    
    // Touch handling variables
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var lastAction: Int = MotionEvent.ACTION_UP
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var buttonSize: Int = 0
    private var isDragging = false
    private var moveThreshold = 0
    
    // Constants for drag behavior
    private val CLICK_DRAG_TOLERANCE = 10f
    private val EDGE_PADDING = 16
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val transcriptionRepository = TranscriptionRepository()
    private val handler = Handler(Looper.getMainLooper())
    private var hideTextRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating FloatingButtonService")
        
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            Log.d(TAG, "Window manager initialized successfully")
            
            if (!checkOverlayPermission()) {
                Log.e(TAG, "Overlay permission not granted, stopping service")
                stopSelf()
                return
            }
            Log.d(TAG, "Overlay permission check passed")
            
            // Get screen dimensions for positioning
            val displayMetrics = resources.displayMetrics
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
            buttonSize = (72 * displayMetrics.density).toInt() // 72dp converted to pixels
            moveThreshold = (CLICK_DRAG_TOLERANCE * displayMetrics.density).toInt()
            
            // Load saved position
            loadButtonPosition()
            
            setupFloatingButton()
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
            Toast.makeText(
                this,
                "Overlay permission is required. Please grant it in Settings.",
                Toast.LENGTH_LONG
            ).show()
            
            // Open overlay settings
            val overlayIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            overlayIntent.data = Uri.parse("package:$packageName")
            overlayIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(overlayIntent)
            
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            setupFloatingButton()
            startForeground(NOTIFICATION_ID, createNotification())
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
            
            // Inflate the floating view layout
            rootLayout = FrameLayout(this)
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, rootLayout)
            
            // Get saved position or use default
            val prefs = getSharedPreferences("floating_button_prefs", Context.MODE_PRIVATE)
            initialX = prefs.getInt("button_x", 0)
            initialY = prefs.getInt("button_y", 100)
            
            // Set up window parameters
            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                // Add FLAG_NOT_TOUCH_MODAL to prevent gesture stealing
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.START
                x = initialX
                y = initialY
            }
            
            // Find views
            buttonView = floatingView.findViewById(R.id.floating_button)
            transcriptionTextView = floatingView.findViewById(R.id.transcription_text)
            transcriptionTextView.visibility = View.GONE
            
            // Set up button click listener
            buttonView.setOnClickListener {
                if (!isDragging) {
                    Log.d(TAG, "Floating button clicked")
                    toggleRecording()
                }
            }
            
            // Set up touch listener for dragging
            rootLayout.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Record initial touch position
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        lastAction = event.action
                        isDragging = false
                        Log.d(TAG, "Touch DOWN at x=${event.rawX}, y=${event.rawY}")
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Calculate new position
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        
                        // Only move if drag distance exceeds threshold
                        if (abs(dx) > moveThreshold || abs(dy) > moveThreshold || isDragging) {
                            isDragging = true
                            params.x = (initialX + dx).toInt()
                            params.y = (initialY + dy).toInt()
                            
                            // Update the view position
                            windowManager.updateViewLayout(rootLayout, params)
                            lastAction = event.action
                            Log.d(TAG, "DRAGGING: dx=$dx, dy=$dy, new position: x=${params.x}, y=${params.y}")
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        // Check if this was a click or a drag
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        
                        if (abs(dx) < moveThreshold && abs(dy) < moveThreshold) {
                            // This was a click, pass to the click listener
                            Log.d(TAG, "ACTION_UP detected as CLICK")
                            view.performClick()
                        } else if (isDragging) {
                            // This was a drag, snap to edge if close enough
                            Log.d(TAG, "ACTION_UP detected as DRAG END")
                            snapToEdge(params)
                            windowManager.updateViewLayout(rootLayout, params)
                            
                            // Save the final position
                            saveButtonPosition(params.x, params.y)
                            Log.d(TAG, "Drag ended, final position: x=${params.x}, y=${params.y}")
                        }
                        isDragging = false
                        lastAction = event.action
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        Log.d(TAG, "Touch CANCELED")
                        isDragging = false
                        true
                    }
                    MotionEvent.ACTION_OUTSIDE -> {
                        Log.d(TAG, "Touch OUTSIDE")
                        true
                    }
                    else -> false
                }
            }
            
            // Add the view to the window manager
            windowManager.addView(rootLayout, params)
            Log.d(TAG, "Floating button successfully added to window manager")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating floating button", e)
            stopSelf()
        }
    }
    
    private fun snapToEdge(params: WindowManager.LayoutParams) {
        // Snap to left or right edge if close enough
        if (params.x < screenWidth / 2) {
            params.x = EDGE_PADDING
        } else {
            params.x = screenWidth - buttonSize - EDGE_PADDING
        }
    }

    private fun toggleRecording() {
        if (!checkAudioPermission()) {
            Log.e(TAG, "Audio permission not granted")
            
            // Create an intent to open the main activity
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
        
        // Update button appearance
        updateButtonAppearance()
    }
    
    private fun updateButtonAppearance() {
        buttonView.setImageResource(
            if (isRecording) R.drawable.ic_stop 
            else R.drawable.ic_mic
        )
        
        // Change background color based on state
        val background = buttonView.background as? GradientDrawable
        background?.setColor(
            if (isRecording) Color.RED
            else ContextCompat.getColor(this, R.color.colorPrimary)
        )
    }

    private fun startRecording() {
        if (!checkAudioPermission()) {
            Log.e(TAG, "Cannot start recording - no permission")
            Toast.makeText(
                this,
                "Audio recording permission is required. Please grant it in the app.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Hide any previous transcription text
        hideTranscriptionText()

        // Use the same timestamp format as VoiceRecordingService
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
            Toast.makeText(
                this,
                "Failed to start recording: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
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
                Toast.makeText(
                    this@FloatingButtonService,
                    "Failed to stop recording: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        mediaRecorder = null
    }

    private fun transcribeRecording(audioFile: File) {
        isTranscribing = true
        transcriptionText = null
        
        // Show "Transcribing..." message
        transcriptionTextView.text = "Transcribing..."
        transcriptionTextView.visibility = View.VISIBLE
        
        // Cancel any pending hide operations
        cancelHideTextRunnable()
        
        Logger.recording("Starting transcription process for file: ${audioFile.absolutePath}")
        Logger.recording("File exists: ${audioFile.exists()}, File size: ${audioFile.length()} bytes")
        
        if (!audioFile.exists() || audioFile.length() == 0L) {
            val errorMsg = "Audio file is missing or empty: ${audioFile.absolutePath}"
            Logger.recording(errorMsg)
            transcriptionText = errorMsg
            transcriptionTextView.text = errorMsg
            isTranscribing = false
            scheduleHideTranscriptionText(5000) // Hide error after 5 seconds
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
                        
                        // Update UI on main thread
                        withContext(Dispatchers.Main) {
                            transcriptionTextView.text = response.text
                            transcriptionTextView.visibility = View.VISIBLE
                            
                            // Auto-hide transcript after 10 seconds
                            scheduleHideTranscriptionText(10000)
                        }
                        
                        // Save transcript to a file with the same base name as the audio file
                        try {
                            val transcriptFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.txt")
                            transcriptFile.writeText(response.text)
                            Logger.storage("Saved transcript to file: ${transcriptFile.absolutePath}")
                            
                            // Show notification that transcript was saved
                            showTranscriptSavedNotification(audioFile.nameWithoutExtension, response.text)
                        } catch (e: Exception) {
                            Logger.error("Failed to save transcript to file", e)
                        }
                    },
                    onFailure = { error ->
                        val errorMsg = "Transcription failed: ${error.message}"
                        Logger.error(errorMsg, error)
                        transcriptionText = errorMsg
                        
                        withContext(Dispatchers.Main) {
                            transcriptionTextView.text = errorMsg
                            scheduleHideTranscriptionText(5000) // Hide error after 5 seconds
                        }
                        
                        Toast.makeText(
                            this@FloatingButtonService,
                            errorMsg,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            } catch (e: Exception) {
                val errorMsg = "Error during transcription: ${e.message}"
                Logger.error(errorMsg, e)
                transcriptionText = errorMsg
                
                withContext(Dispatchers.Main) {
                    transcriptionTextView.text = errorMsg
                    scheduleHideTranscriptionText(5000) // Hide error after 5 seconds
                }
                
                Toast.makeText(
                    this@FloatingButtonService,
                    errorMsg,
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                Logger.recording("Transcription process completed")
                isTranscribing = false
            }
        }
    }
    
    private fun scheduleHideTranscriptionText(delayMillis: Long) {
        // Cancel any existing hide operations
        cancelHideTextRunnable()
        
        // Create and schedule a new hide operation
        hideTextRunnable = Runnable {
            Log.d(TAG, "Auto-hiding transcription text")
            hideTranscriptionText()
        }
        
        handler.postDelayed(hideTextRunnable!!, delayMillis)
        Log.d(TAG, "Scheduled to hide transcription text in $delayMillis ms")
    }
    
    private fun cancelHideTextRunnable() {
        hideTextRunnable?.let {
            handler.removeCallbacks(it)
            Log.d(TAG, "Cancelled pending hide text operation")
        }
        hideTextRunnable = null
    }
    
    private fun hideTranscriptionText() {
        transcriptionTextView.visibility = View.GONE
        Log.d(TAG, "Transcription text hidden")
    }

    private fun showTranscriptSavedNotification(fileName: String, transcript: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        Logger.ui("Showing transcript saved notification for $fileName")
        
        // Create an intent to open the VoiceMemoActivity
        val intent = Intent(this, VoiceMemoActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create the notification
        val notification = NotificationCompat.Builder(this, AudioRecorderApp.TRANSCRIPT_CHANNEL_ID)
            .setContentTitle("Transcription Saved")
            .setContentText("Transcript for $fileName has been saved")
            .setStyle(NotificationCompat.BigTextStyle().bigText(transcript.take(100) + if (transcript.length > 100) "..." else ""))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        // Show the notification
        notificationManager.notify(TRANSCRIPT_NOTIFICATION_ID, notification)
        Logger.ui("Transcript saved notification shown with ID $TRANSCRIPT_NOTIFICATION_ID")
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
        
        if (!hasPermission) {
            Log.e(TAG, "Audio permission check failed - RECORD_AUDIO permission not granted")
            Toast.makeText(
                this,
                "Audio recording permission is required. Please grant it in Settings.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Log.d(TAG, "Audio permission check passed - RECORD_AUDIO permission granted")
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
            handler.removeCallbacksAndMessages(null)
            Log.d(TAG, "About to remove floating button from window")
            if (::rootLayout.isInitialized) {
                windowManager.removeView(rootLayout)
                Log.d(TAG, "Floating button removed successfully")
            } else {
                Log.w(TAG, "Floating button was not initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during service cleanup", e)
        }
        Log.d(TAG, "Service cleanup completed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Save and load button position
    private fun saveButtonPosition(x: Int, y: Int) {
        val prefs = getSharedPreferences("floating_button_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("button_x", x)
            .putInt("button_y", y)
            .apply()
        Log.d(TAG, "Saved button position: x=$x, y=$y")
    }

    private fun loadButtonPosition() {
        val prefs = getSharedPreferences("floating_button_prefs", Context.MODE_PRIVATE)
        initialX = prefs.getInt("button_x", 0)
        initialY = prefs.getInt("button_y", 100)
        Log.d(TAG, "Loaded button position: x=$initialX, y=$initialY")
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio Recorder")
            .setContentText("Recording service is active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "AudioRecorder"
        private const val NOTIFICATION_ID = 1
        private const val TRANSCRIPT_NOTIFICATION_ID = 2
    }
} 