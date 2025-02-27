package com.example.audiorecorder.service

import android.Manifest
import android.app.Notification
import android.app.Service
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
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.audiorecorder.AudioRecorderApp.Companion.CHANNEL_ID
import com.example.audiorecorder.repository.TranscriptionRepository
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.view.ViewConfiguration
import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.example.audiorecorder.MainActivity
import android.net.Uri

class FloatingButtonService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: View
    private var isRecording by mutableStateOf(false)
    private var transcriptionText by mutableStateOf<String?>(null)
    private var isTranscribing by mutableStateOf(false)
    private var currentRecordingFile: File? = null
    private var mediaRecorder: MediaRecorder? = null
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var lastAction: Int = MotionEvent.ACTION_UP
    private var velocityX: Float = 0f
    private var velocityY: Float = 0f
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var lastTouchTime: Long = 0
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var buttonWidth: Int = 0
    private var buttonHeight: Int = 0
    private val CLICK_THRESHOLD = 10f
    private val SNAP_THRESHOLD = 32
    private val VELOCITY_UNITS = 1000000000f // Convert to pixels per second
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val transcriptionRepository = TranscriptionRepository()
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    // Extension property to convert Int to pixels
    private val Int.px: Int
        get() = (this * resources.displayMetrics.density).toInt()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val animationSpec = SpringSpec<Offset>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

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
            android.util.Log.e("AudioRecorder", "Audio permission check failed - RECORD_AUDIO permission not granted")
            Toast.makeText(
                this,
                "Audio recording permission is required. Please grant it in Settings.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            android.util.Log.d("AudioRecorder", "Audio permission check passed - RECORD_AUDIO permission granted")
        }
        return hasPermission
    }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("AudioRecorder", "FloatingButtonService onCreate - Starting service initialization")
        
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            android.util.Log.d("AudioRecorder", "Window manager initialized successfully")
            
            if (!checkOverlayPermission()) {
                android.util.Log.e("AudioRecorder", "Overlay permission not granted, stopping service")
                stopSelf()
                return
            }
            android.util.Log.d("AudioRecorder", "Overlay permission check passed")
            
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            android.util.Log.d("AudioRecorder", "About to create floating button")
            createFloatingButton()
            android.util.Log.d("AudioRecorder", "Service initialization completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("AudioRecorder", "Error during service initialization", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("AudioRecorder", "FloatingButtonService onStartCommand")
        
        if (!checkOverlayPermission()) {
            android.util.Log.e("AudioRecorder", "Overlay permission not granted")
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
            createFloatingButton()
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            android.util.Log.e("AudioRecorder", "Error creating floating button", e)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun createFloatingButton() {
        try {
            android.util.Log.d("AudioRecorder", "Creating floating button")
            
            // Get screen dimensions
            val displayMetrics = resources.displayMetrics
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
            // Set button dimensions
            buttonWidth = 72.px
            buttonHeight = 72.px
            
            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 100
            }

            val layout = FrameLayout(this).apply {
                setViewTreeLifecycleOwner(this@FloatingButtonService)
                setViewTreeViewModelStoreOwner(this@FloatingButtonService)
                setViewTreeSavedStateRegistryOwner(this@FloatingButtonService)
            }
            
            val composeView = ComposeView(this).apply {
                setContent {
                    var offset by remember { mutableStateOf(Offset.Zero) }
                    val velocityTracker = remember { VelocityTracker() }
                    
                    // Derived state for clamped offset
                    val clampedOffset = remember(offset) {
                        derivedStateOf {
                            Offset(
                                offset.x.coerceIn(0f, screenWidth - buttonWidth.toFloat()),
                                offset.y.coerceIn(0f, screenHeight - buttonHeight.toFloat())
                            )
                        }
                    }
                    
                    // Animation state
                    val animatedOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
                    
                    LaunchedEffect(clampedOffset.value) {
                        animatedOffset.animateTo(
                            clampedOffset.value,
                            animationSpec = animationSpec
                        )
                    }
                    
                    Column(
                        modifier = Modifier
                            .offset { IntOffset(
                                animatedOffset.value.x.roundToInt(),
                                animatedOffset.value.y.roundToInt()
                            ) }
                            .pointerInput(Unit) {
                                val touchSlop = ViewConfiguration.get(context).scaledTouchSlop * 0.8f
                                
                                detectDragGestures(
                                    onDragStart = { velocityTracker.resetTracking() },
                                    onDragEnd = {
                                        val velocity = velocityTracker.calculateVelocity()
                                        val targetOffset = Offset(
                                            if (offset.x < screenWidth / 2) 0f else screenWidth - buttonWidth.toFloat(),
                                            offset.y.coerceIn(0f, screenHeight - buttonHeight.toFloat())
                                        )
                                        offset = targetOffset
                                    },
                                    onDragCancel = { velocityTracker.resetTracking() }
                                ) { change, dragAmount ->
                                    change.consume()
                                    velocityTracker.addPosition(
                                        change.uptimeMillis,
                                        change.position
                                    )
                                    offset += dragAmount
                                }
                            }
                            .padding(16.dp)
                            .background(Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .padding(8.dp)
                        ) {
                            FloatingActionButton(
                                onClick = { 
                                    android.util.Log.d("AudioRecorder", "Floating button clicked")
                                    toggleRecording()
                                },
                                modifier = Modifier.matchParentSize(),
                                shape = CircleShape,
                                containerColor = if (isRecording) 
                                    MaterialTheme.colorScheme.error 
                                else MaterialTheme.colorScheme.primary
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                                    tint = Color.White
                                )
                            }
                        }
                        
                        if (isTranscribing) {
                            Text(
                                text = "Transcribing...",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        transcriptionText?.let { text ->
                            Text(
                                text = text,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                        shape = MaterialTheme.shapes.small
                                    )
                                    .padding(8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            layout.addView(composeView)
            floatingButton = layout
            windowManager.addView(floatingButton, params)
            android.util.Log.d("AudioRecorder", "Floating button successfully added to window manager")
        } catch (e: Exception) {
            android.util.Log.e("AudioRecorder", "Error creating floating button", e)
            stopSelf()
        }
    }

    private fun toggleRecording() {
        if (!checkAudioPermission()) {
            android.util.Log.e("AudioRecorder", "Audio permission not granted, current state: ${
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            }")
            
            // Create an intent to open the main activity instead of settings
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
    }

    private fun startRecording() {
        if (!checkAudioPermission()) {
            android.util.Log.e("AudioRecorder", "Cannot start recording - no permission. Current permissions state: RECORD_AUDIO=${
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            }")
            Toast.makeText(
                this,
                "Audio recording permission is required. Please grant it in the app.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val fileName = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
            .format(Date()) + ".mp3"
        currentRecordingFile = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), fileName)
        android.util.Log.d("AudioRecorder", "Starting recording to file: ${currentRecordingFile?.absolutePath}")

        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.util.Log.d("AudioRecorder", "Using Android 12+ MediaRecorder constructor")
                MediaRecorder(this)
            } else {
                android.util.Log.d("AudioRecorder", "Using legacy MediaRecorder constructor")
                MediaRecorder()
            }.apply {
                android.util.Log.d("AudioRecorder", "Configuring MediaRecorder...")
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentRecordingFile?.absolutePath)
                android.util.Log.d("AudioRecorder", "Preparing MediaRecorder...")
                prepare()
                android.util.Log.d("AudioRecorder", "Starting MediaRecorder...")
                start()
            }
            android.util.Log.d("AudioRecorder", "Recording started successfully")
        } catch (e: Exception) {
            android.util.Log.e("AudioRecorder", "Error starting recording: ${e.message}", e)
            android.util.Log.e("AudioRecorder", "Stack trace: ${e.stackTraceToString()}")
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
                android.util.Log.d("AudioRecorder", "Stopping recording...")
                stop()
                release()
                android.util.Log.d("AudioRecorder", "Recording stopped successfully")
                
                // Start transcription
                currentRecordingFile?.let { file ->
                    android.util.Log.d("AudioRecorder", "Starting transcription for file: ${file.absolutePath}")
                    transcribeRecording(file)
                } ?: run {
                    android.util.Log.e("AudioRecorder", "Cannot start transcription - recording file is null")
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioRecorder", "Error stopping recording: ${e.message}", e)
                android.util.Log.e("AudioRecorder", "Stack trace: ${e.stackTraceToString()}")
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
        
        android.util.Log.d("AudioRecorder", "Starting transcription process for file: ${audioFile.absolutePath}")
        android.util.Log.d("AudioRecorder", "File exists: ${audioFile.exists()}, File size: ${audioFile.length()} bytes")
        
        if (!audioFile.exists() || audioFile.length() == 0L) {
            val errorMsg = "Audio file is missing or empty: ${audioFile.absolutePath}"
            android.util.Log.d("AudioRecorder", errorMsg)
            transcriptionText = errorMsg
            isTranscribing = false
            return
        }
        
        serviceScope.launch {
            try {
                android.util.Log.d("AudioRecorder", "Making transcription API request...")
                val result = transcriptionRepository.transcribeAudioFile(audioFile)
                
                result.fold(
                    onSuccess = { response ->
                        android.util.Log.d("AudioRecorder", "Transcription successful: ${response.text}")
                        transcriptionText = response.text
                    },
                    onFailure = { error ->
                        val errorMsg = "Transcription failed: ${error.message}"
                        android.util.Log.e("AudioRecorder", errorMsg, error)
                        transcriptionText = errorMsg
                        Toast.makeText(
                            this@FloatingButtonService,
                            errorMsg,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            } catch (e: Exception) {
                val errorMsg = "Error during transcription: ${e.message}"
                android.util.Log.e("AudioRecorder", errorMsg, e)
                transcriptionText = errorMsg
                Toast.makeText(
                    this@FloatingButtonService,
                    errorMsg,
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                android.util.Log.d("AudioRecorder", "Transcription process completed")
                isTranscribing = false
            }
        }
    }

    override fun onDestroy() {
        android.util.Log.d("AudioRecorder", "FloatingButtonService onDestroy - Starting cleanup")
        try {
            super.onDestroy()
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            if (isRecording) {
                android.util.Log.d("AudioRecorder", "Stopping ongoing recording")
                stopRecording()
            }
            serviceScope.cancel()
            android.util.Log.d("AudioRecorder", "About to remove floating button from window")
            if (::floatingButton.isInitialized) {
                windowManager.removeView(floatingButton)
                android.util.Log.d("AudioRecorder", "Floating button removed successfully")
            } else {
                android.util.Log.w("AudioRecorder", "Floating button was not initialized")
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioRecorder", "Error during service cleanup", e)
        }
        android.util.Log.d("AudioRecorder", "Service cleanup completed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1
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
} 