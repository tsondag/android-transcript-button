package com.example.audiorecorder.floaty

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.audiorecorder.R

/**
 * A custom floating button implementation that can be dragged around the screen.
 * Uses basic Android Views instead of Material Components to avoid theme issues in a Service context.
 */
class CustomFloaty(
    private val context: Context,
    private var buttonIconResId: Int,
    private var buttonBackgroundColor: Int = 0xFF6200EE.toInt(),
    private var buttonIconTint: Int = Color.WHITE,
    private var buttonSize: Int = 56,
    private val onButtonClick: () -> Unit
) {
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val params: WindowManager.LayoutParams = WindowManager.LayoutParams()
    private val rootView: FrameLayout = FrameLayout(context)
    private val floatingButton: ImageButton = ImageButton(context)
    
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    
    private var isShowing = false
    
    init {
        setupWindowParams()
        setupFloatingButton()
        setupTouchListener()
        
        // Restore saved position
        loadSavedPosition()
    }
    
    private fun setupWindowParams() {
        params.apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }
    }
    
    private fun setupFloatingButton() {
        // Create a circular background drawable
        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(buttonBackgroundColor)
        }
        
        floatingButton.apply {
            // Set size
            val sizePx = (buttonSize * context.resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            
            // Set icon
            setImageResource(buttonIconResId)
            
            // Set background
            background = backgroundDrawable
            
            // Set padding to make the icon look better
            val paddingPx = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            
            // Set icon tint
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                imageTintList = android.content.res.ColorStateList.valueOf(buttonIconTint)
            }
            
            // Disable click handling (we'll handle it in the touch listener)
            isClickable = false
            isFocusable = false
        }
        
        rootView.addView(floatingButton)
    }
    
    private fun setupTouchListener() {
        rootView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    
                    try {
                        windowManager.updateViewLayout(rootView, params)
                    } catch (e: Exception) {
                        Log.e("CustomFloaty", "Error updating view layout", e)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = Math.abs(event.rawX - initialTouchX) > 10 || 
                                Math.abs(event.rawY - initialTouchY) > 10
                    
                    if (moved) {
                        // Save the current position when the button is released after moving
                        saveCurrentPosition()
                    } else {
                        onButtonClick()
                    }
                    true
                }
                else -> false
            }
        }
    }
    
    fun show() {
        if (!isShowing && checkOverlayPermission()) {
            try {
                // Start with opacity 0 for fade-in animation
                rootView.alpha = 0f
                windowManager.addView(rootView, params)
                isShowing = true
                
                // Fade in animation
                rootView.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            } catch (e: Exception) {
                Log.e("CustomFloaty", "Error showing floating button", e)
            }
        }
    }
    
    /**
     * Fade in the button if it's already visible
     */
    fun fadeIn() {
        if (isShowing) {
            rootView.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }
    }
    
    /**
     * Fade out the button with optional completion callback
     */
    fun fadeOut(onComplete: (() -> Unit)? = null) {
        if (isShowing) {
            rootView.animate()
                .alpha(0f)
                .setDuration(200)
                .setListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        onComplete?.invoke()
                    }
                })
                .start()
        } else {
            onComplete?.invoke()
        }
    }
    
    fun remove() {
        if (isShowing) {
            try {
                windowManager.removeView(rootView)
                isShowing = false
            } catch (e: Exception) {
                Log.e("CustomFloaty", "Error removing floating button", e)
            }
        }
    }
    
    fun updateIcon(iconResId: Int) {
        this.buttonIconResId = iconResId
        floatingButton.setImageResource(iconResId)
    }
    
    fun updateBackgroundColor(color: Int) {
        this.buttonBackgroundColor = color
        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        floatingButton.background = backgroundDrawable
    }
    
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
    
    /**
     * Save the current position to SharedPreferences
     */
    private fun saveCurrentPosition() {
        try {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit()
                .putInt(PREF_BUTTON_X, params.x)
                .putInt(PREF_BUTTON_Y, params.y)
                .apply()
            Log.d("CustomFloaty", "Saved button position: x=${params.x}, y=${params.y}")
        } catch (e: Exception) {
            Log.e("CustomFloaty", "Error saving button position", e)
        }
    }
    
    /**
     * Load the saved position from SharedPreferences
     */
    private fun loadSavedPosition() {
        try {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            params.x = prefs.getInt(PREF_BUTTON_X, 0)
            params.y = prefs.getInt(PREF_BUTTON_Y, 100)
            Log.d("CustomFloaty", "Loaded button position: x=${params.x}, y=${params.y}")
        } catch (e: Exception) {
            Log.e("CustomFloaty", "Error loading button position", e)
        }
    }
    
    companion object {
        private const val PREF_BUTTON_X = "floating_button_x"
        private const val PREF_BUTTON_Y = "floating_button_y"
    }
} 