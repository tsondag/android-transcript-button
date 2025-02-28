package com.example.audiorecorder.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.audiorecorder.utils.Logger

/**
 * Accessibility service for inserting text into input fields
 */
class TranscriptAccessibilityService : AccessibilityService() {
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Logger.ui("TranscriptAccessibilityService connected")
        instance = this
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // We don't need to handle events for this service
        // The service is only used to insert text on demand
    }
    
    override fun onInterrupt() {
        Logger.ui("TranscriptAccessibilityService interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Logger.ui("TranscriptAccessibilityService destroyed")
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
    }
} 