package com.example.audiorecorder.floaty

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.example.audiorecorder.R
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Enhanced floating button with different states and animations
 * Matches the design from the React implementation
 */
class EnhancedFloatingButton(
    private val context: Context,
    private val onButtonClick: () -> Unit
) {
    enum class RecordingState {
        IDLE, RECORDING, STOPPING, SAVED
    }

    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val layoutParams: WindowManager.LayoutParams = setupWindowParams()
    private val buttonLayout: ViewGroup
    
    // UI components
    private val buttonOuter: FrameLayout
    private val buttonInner: FrameLayout
    private val dot: View
    private val checkmark: ImageView
    private val timerText: TextView
    
    // Animation
    private var pulseAnimation: Animation? = null
    private var currentState: RecordingState = RecordingState.IDLE
    
    // Timer formatter
    private val timerFormat = SimpleDateFormat("mm:ss", Locale.getDefault())
    
    init {
        // Inflate the button layout
        val inflater = LayoutInflater.from(context)
        buttonLayout = inflater.inflate(R.layout.enhanced_floating_button, null) as ViewGroup
        
        // Find views
        buttonOuter = buttonLayout.findViewById(R.id.button_outer)
        buttonInner = buttonLayout.findViewById(R.id.button_inner)
        dot = buttonLayout.findViewById(R.id.dot)
        checkmark = buttonLayout.findViewById(R.id.checkmark)
        timerText = buttonLayout.findViewById(R.id.timer)
        
        // Load pulse animation
        pulseAnimation = AnimationUtils.loadAnimation(context, R.anim.pulse_animation)
        
        // Set up click listener
        buttonLayout.setOnClickListener {
            onButtonClick()
        }
        
        // Set up touch listener for dragging
        setupTouchListener()
        
        // Load saved position
        loadPosition()
    }

    private fun setupWindowParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        var initialX: Int = 0
        var initialY: Int = 0
        var initialTouchX: Float = 0f
        var initialTouchY: Float = 0f
        var isMoved = false
        var lastClickTime = 0L

        buttonLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Record initial positions
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Calculate new position and update layout
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        isMoved = true
                        layoutParams.x = initialX + dx.toInt()
                        layoutParams.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(buttonLayout, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // If not moved, treat as click, otherwise save position
                    if (!isMoved) {
                        val clickTime = System.currentTimeMillis()
                        if (clickTime - lastClickTime > 300) {
                            lastClickTime = clickTime
                            buttonLayout.performClick()
                        }
                    } else {
                        savePosition()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Update the UI based on recording state
     */
    fun updateState(state: RecordingState) {
        if (currentState == state) return
        
        currentState = state
        
        when (state) {
            RecordingState.IDLE -> {
                // Reset to microphone state
                buttonOuter.background = ContextCompat.getDrawable(context, R.drawable.floating_button_idle_background)
                buttonInner.setBackgroundResource(R.drawable.floating_button_inner_circle)
                // Clear any background tints
                buttonInner.backgroundTintList = null
                dot.visibility = View.VISIBLE
                dot.backgroundTintList = ContextCompat.getColorStateList(context, R.color.record_button_destructive)
                checkmark.visibility = View.GONE
                timerText.visibility = View.GONE
                timerText.text = "00:00"
                
                // Stop any animations
                dot.clearAnimation()
            }
            RecordingState.RECORDING -> {
                // Change to recording state with pulsing animation
                buttonOuter.background = ContextCompat.getDrawable(context, R.drawable.floating_button_recording_background)
                buttonInner.setBackgroundResource(R.drawable.floating_button_inner_circle)
                dot.visibility = View.VISIBLE
                dot.backgroundTintList = ContextCompat.getColorStateList(context, R.color.record_button_destructive)
                checkmark.visibility = View.GONE
                timerText.visibility = View.VISIBLE
                
                // Start pulsing animation
                dot.startAnimation(pulseAnimation)
            }
            RecordingState.STOPPING -> {
                // Change to stopping state with checkmark
                buttonOuter.background = ContextCompat.getDrawable(context, R.drawable.floating_button_success_background)
                buttonInner.setBackgroundResource(R.drawable.floating_button_inner_circle)
                buttonInner.backgroundTintList = ContextCompat.getColorStateList(context, R.color.record_button_success)
                dot.visibility = View.GONE
                checkmark.visibility = View.VISIBLE
                timerText.visibility = View.VISIBLE
                timerText.text = "Saved!"
                
                // Stop any animations
                dot.clearAnimation()
                
                // Transition to SAVED state after a delay (like in React)
                Handler(Looper.getMainLooper()).postDelayed({
                    if (currentState == RecordingState.STOPPING) {
                        updateState(RecordingState.SAVED)
                    }
                }, 1000)
            }
            RecordingState.SAVED -> {
                // Change to saved state (similar to stopping)
                buttonOuter.background = ContextCompat.getDrawable(context, R.drawable.floating_button_success_background)
                buttonInner.setBackgroundResource(R.drawable.floating_button_inner_circle)
                buttonInner.backgroundTintList = ContextCompat.getColorStateList(context, R.color.record_button_success)
                dot.visibility = View.GONE
                checkmark.visibility = View.VISIBLE
                timerText.visibility = View.VISIBLE
                timerText.text = "Saved!"
                
                // Return to idle state after a delay (like in React)
                Handler(Looper.getMainLooper()).postDelayed({
                    if (currentState == RecordingState.SAVED) {
                        updateState(RecordingState.IDLE)
                    }
                }, 2000)
            }
        }
    }

    /**
     * Update the timer display with elapsed recording time
     */
    fun updateTimer(elapsedMillis: Long) {
        // Format time as mm:ss
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis) % 60
        timerText.text = String.format("%02d:%02d", minutes, seconds)
        
        // Ensure timer is visible during recording
        if (currentState == RecordingState.RECORDING && timerText.visibility != View.VISIBLE) {
            timerText.visibility = View.VISIBLE
        }
    }

    private fun savePosition() {
        val prefs = context.getSharedPreferences("FloatingButtonPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("X_POS", layoutParams.x)
            putInt("Y_POS", layoutParams.y)
            apply()
        }
    }

    private fun loadPosition() {
        val prefs = context.getSharedPreferences("FloatingButtonPrefs", Context.MODE_PRIVATE)
        layoutParams.x = prefs.getInt("X_POS", 0)
        layoutParams.y = prefs.getInt("Y_POS", 100)
    }

    /**
     * Show the floating button
     */
    fun show() {
        if (!checkOverlayPermission()) {
            Log.e(TAG, "Cannot show floating button - no overlay permission")
            return
        }
        try {
            windowManager.addView(buttonLayout, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing floating button", e)
        }
    }

    /**
     * Remove the floating button from the window
     */
    fun remove() {
        try {
            windowManager.removeView(buttonLayout)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing floating button", e)
        }
    }

    /**
     * Check if the app has permission to draw overlays
     */
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    companion object {
        private const val TAG = "EnhancedFloatingButton"
    }
} 