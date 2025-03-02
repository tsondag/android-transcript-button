package com.example.audiorecorder.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.example.audiorecorder.utils.Logger

/**
 * Accessibility service for inserting text into input fields
 * Also monitors keyboard visibility and input field focus
 */
class TranscriptAccessibilityService : AccessibilityService() {
    
    // Keyboard visibility tracking
    private var isKeyboardVisible = false
    private var isInputFieldFocused = false
    private var isAppMinimized = false
    
    // Debouncing for stability
    private val handler = Handler(Looper.getMainLooper())
    private var pendingKeyboardNotification: Runnable? = null
    private val KEYBOARD_DEBOUNCE_DELAY = 300L // ms
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Logger.ui("TranscriptAccessibilityService connected")
        instance = this
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Now we handle events to detect keyboard and input field focus
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                checkForInputFieldFocus(event)
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                checkForKeyboardVisibility()
                checkAppMinimized()
            }
        }
    }
    
    /**
     * Check if the current app is minimized (sent to background)
     */
    private fun checkAppMinimized() {
        try {
            val wasAppMinimized = isAppMinimized
            
            // Get all windows
            val windows = windows
            var windowCount = 0
            var hasActiveWindows = false
            var hasVisibleWindows = false
            
            for (window in windows) {
                windowCount++
                if (window.isActive) {
                    hasActiveWindows = true
                }
                
                // Some windows might not be marked as active but are still visible
                val windowBounds = Rect()
                window.getBoundsInScreen(windowBounds)
                if (windowBounds.width() > 0 && windowBounds.height() > 0) {
                    hasVisibleWindows = true
                }
            }
            
            // An app is truly minimized when no windows are active or visible
            isAppMinimized = windowCount == 0 || (!hasActiveWindows && !hasVisibleWindows)
            
            // Add more detailed logging
            Logger.ui("App minimization check: windowCount=$windowCount, hasActiveWindows=$hasActiveWindows, " +
                     "hasVisibleWindows=$hasVisibleWindows, isAppMinimized=$isAppMinimized")
            
            // If app minimized state changed, notify FloatingButtonService
            if (wasAppMinimized != isAppMinimized) {
                Logger.ui("App minimized state changed: $isAppMinimized")
                
                // Immediate notification when app is minimized to improve responsiveness
                pendingKeyboardNotification?.let { handler.removeCallbacks(it) }
                pendingKeyboardNotification = null
                
                val intent = Intent(this, FloatingButtonService::class.java).apply {
                    putExtra("KEYBOARD_INPUT_STATE_CHANGED", true)
                    putExtra("SHOW_FLOATING_BUTTON", isKeyboardVisible || isInputFieldFocused)
                    putExtra("IS_APP_MINIMIZED", isAppMinimized)
                }
                startService(intent)
            }
        } catch (e: Exception) {
            Logger.error("Error checking app minimized state", e)
        }
    }
    
    /**
     * Check if the focused view is an input field
     */
    private fun checkForInputFieldFocus(event: AccessibilityEvent) {
        try {
            val source = event.source ?: return
            val wasFocused = isInputFieldFocused
            
            // Check if this is an editable field
            isInputFieldFocused = source.isEditable
            source.recycle()
            
            // If focus state changed, notify FloatingButtonService
            if (wasFocused != isInputFieldFocused) {
                notifyFloatingButtonService()
            }
        } catch (e: Exception) {
            Logger.error("Error checking input field focus", e)
        }
    }
    
    /**
     * Check if the keyboard is currently visible
     */
    private fun checkForKeyboardVisibility() {
        try {
            // Get all windows
            val wasKeyboardVisible = isKeyboardVisible
            isKeyboardVisible = false
            
            // Get the display metrics for calculation
            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            
            // Check for IME window
            val windows = windows
            for (window in windows) {
                if (window.isFocused && window.isActive) {
                    // Some keyboard detection heuristics
                    val windowBounds = Rect()
                    window.getBoundsInScreen(windowBounds)
                    
                    // More robust keyboard detection with multiple conditions:
                    // 1. Check if window height is reasonable for a keyboard (15-60% of screen height)
                    // 2. Check if window is at the bottom of the screen
                    // 3. Check if window is wider than it is tall (typical for keyboards)
                    val heightPercentage = (windowBounds.height().toFloat() / screenHeight) * 100
                    val isAtBottom = windowBounds.bottom >= screenHeight - 20 // Allow small margin
                    val isWiderThanTall = windowBounds.width() > windowBounds.height()
                    
                    if (windowBounds.height() > 100 && // Must have some minimum height
                        heightPercentage in 15.0..60.0 && // Typical keyboard heights
                        isAtBottom && 
                        isWiderThanTall) {
                        
                        Logger.ui("Detected possible keyboard: height=${windowBounds.height()}px, " +
                                     "heightPercentage=${heightPercentage}%, " +
                                     "isAtBottom=$isAtBottom, isWiderThanTall=$isWiderThanTall")
                        isKeyboardVisible = true
                        break
                    }
                }
            }
            
            // If keyboard visibility changed, notify FloatingButtonService
            if (wasKeyboardVisible != isKeyboardVisible) {
                Logger.ui("Keyboard visibility changed: $isKeyboardVisible")
                notifyFloatingButtonService()
            }
        } catch (e: Exception) {
            Logger.error("Error checking keyboard visibility", e)
        }
    }
    
    /**
     * Notify FloatingButtonService about keyboard/input field state
     */
    private fun notifyFloatingButtonService() {
        // Cancel any pending notification
        pendingKeyboardNotification?.let { handler.removeCallbacks(it) }
        
        // Create a new notification with a delay to debounce rapid changes
        pendingKeyboardNotification = Runnable {
            val shouldShowButton = isKeyboardVisible || isInputFieldFocused
            val intent = Intent(this, FloatingButtonService::class.java).apply {
                putExtra("KEYBOARD_INPUT_STATE_CHANGED", true)
                putExtra("SHOW_FLOATING_BUTTON", shouldShowButton)
                putExtra("IS_APP_MINIMIZED", isAppMinimized)
            }
            startService(intent)
            
            Logger.ui("Keyboard visibility: $isKeyboardVisible, Input focused: $isInputFieldFocused, App minimized: $isAppMinimized")
            pendingKeyboardNotification = null
        }.also {
            handler.postDelayed(it, KEYBOARD_DEBOUNCE_DELAY)
        }
    }
    
    override fun onInterrupt() {
        Logger.ui("TranscriptAccessibilityService interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Logger.ui("TranscriptAccessibilityService destroyed")
        // Clean up handler callbacks
        pendingKeyboardNotification?.let { handler.removeCallbacks(it) }
        if (instance == this) {
            instance = null
        }
    }
    
    /**
     * Insert text into the currently focused input field
     * 
     * @param text The text to insert
     * @return True if the operation was successful
     */
    fun insertTextIntoFocusedField(text: String): Boolean {
        Logger.ui("Attempting to insert text into focused field")
        
        if (text.isBlank()) {
            Logger.error("Cannot insert empty text")
            return false
        }
        
        try {
            // Get the root node
            val rootNode = rootInActiveWindow ?: return false
            
            // Find the focused node
            val focusedNode = findFocusedNode(rootNode)
            if (focusedNode == null) {
                Logger.error("No focused input field found")
                return false
            }
            
            // Check if the node is editable
            if (!focusedNode.isEditable) {
                Logger.error("Focused node is not editable")
                focusedNode.recycle()
                rootNode.recycle()
                return false
            }
            
            // Insert text using clipboard and paste action
            return insertTextUsingClipboard(focusedNode, text).also {
                focusedNode.recycle()
                rootNode.recycle()
            }
        } catch (e: Exception) {
            Logger.error("Error inserting text into focused field", e)
            return false
        }
    }
    
    /**
     * Find the focused node in the accessibility tree
     * 
     * @param rootNode The root node
     * @return The focused node, or null if not found
     */
    private fun findFocusedNode(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // First try to find the input focus
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && focusedNode.isEditable) {
            return focusedNode
        }
        
        // If no input focus, try to find focused nodes recursively
        return findFocusedNodeRecursive(rootNode)
    }
    
    /**
     * Recursively search for a focused editable node
     * 
     * @param node The current node to check
     * @return The focused editable node, or null if not found
     */
    private fun findFocusedNodeRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Check if this node is focused and editable
        if (node.isFocused && node.isEditable) {
            return AccessibilityNodeInfo.obtain(node)
        }
        
        // Check children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val focusedChild = findFocusedNodeRecursive(child)
            if (focusedChild != null) {
                child.recycle()
                return focusedChild
            }
            child.recycle()
        }
        
        return null
    }
    
    /**
     * Insert text using clipboard and paste action
     * 
     * @param node The node to insert text into
     * @param text The text to insert
     * @return True if the operation was successful
     */
    private fun insertTextUsingClipboard(node: AccessibilityNodeInfo, text: String): Boolean {
        // First try using the setText action
        val arguments = Bundle()
        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text
        )
        
        val setTextResult = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        if (setTextResult) {
            Logger.ui("Text inserted using ACTION_SET_TEXT")
            return true
        }
        
        // If setText fails, try using paste
        // First, save the current clipboard content
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val originalClip = clipboardManager.primaryClip
        
        // Set the new content to clipboard
        val clip = ClipData.newPlainText("transcript", text)
        clipboardManager.setPrimaryClip(clip)
        
        // Perform paste action
        val pasteResult = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        
        // Show a toast if successful
        if (pasteResult) {
            Logger.ui("Text inserted using ACTION_PASTE")
            Toast.makeText(this, "Text inserted", Toast.LENGTH_SHORT).show()
        } else {
            Logger.error("Failed to insert text")
        }
        
        return pasteResult
    }
    
    companion object {
        /**
         * Static instance of the service
         */
        @Volatile
        var instance: TranscriptAccessibilityService? = null
            private set
            
        /**
         * Get display height (screen height)
         */
        private val displayHeight: Int
            get() {
                val metrics = instance?.resources?.displayMetrics
                return metrics?.heightPixels ?: 0
            }
    }
} 