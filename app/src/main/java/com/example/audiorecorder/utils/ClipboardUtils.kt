package com.example.audiorecorder.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.example.audiorecorder.utils.Logger

/**
 * Utility class for clipboard operations
 */
object ClipboardUtils {
    
    /**
     * Copy text to clipboard
     * 
     * @param context The context
     * @param text The text to copy
     * @param label A user-visible label for the clip data
     * @param showToast Whether to show a toast notification
     * @return True if the operation was successful
     */
    fun copyToClipboard(
        context: Context, 
        text: String, 
        label: String = "Transcription", 
        showToast: Boolean = true
    ): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            
            // Show toast if requested
            if (showToast) {
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            
            Logger.ui("Text copied to clipboard: ${text.take(50)}${if (text.length > 50) "..." else ""}")
            true
        } catch (e: Exception) {
            Logger.error("Failed to copy text to clipboard", e)
            if (showToast) {
                Toast.makeText(context, "Failed to copy to clipboard", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }
    
    /**
     * Get text from clipboard
     * 
     * @param context The context
     * @return The text from clipboard, or null if clipboard is empty or not text
     */
    fun getTextFromClipboard(context: Context): String? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip() && clipboard.primaryClip?.itemCount ?: 0 > 0) {
                val item = clipboard.primaryClip?.getItemAt(0)
                val text = item?.text?.toString()
                Logger.ui("Text retrieved from clipboard: ${text?.take(50)}${if (text?.length ?: 0 > 50) "..." else ""}")
                text
            } else {
                Logger.ui("No text available in clipboard")
                null
            }
        } catch (e: Exception) {
            Logger.error("Failed to get text from clipboard", e)
            null
        }
    }
} 