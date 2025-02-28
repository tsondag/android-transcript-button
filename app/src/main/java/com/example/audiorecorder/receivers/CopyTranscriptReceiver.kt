package com.example.audiorecorder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.audiorecorder.SettingsFragment
import com.example.audiorecorder.utils.AccessibilityUtils
import com.example.audiorecorder.utils.ClipboardUtils
import com.example.audiorecorder.utils.Logger

/**
 * BroadcastReceiver to handle copying transcript text to clipboard from notifications
 */
class CopyTranscriptReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        Logger.ui("CopyTranscriptReceiver received action: ${intent.action}")
        
        if (intent.action == ACTION_COPY_TRANSCRIPT) {
            val transcriptText = intent.getStringExtra(EXTRA_TRANSCRIPT_TEXT)
            
            if (transcriptText.isNullOrEmpty()) {
                Logger.error("Cannot copy empty transcript text")
                return
            }
            
            Logger.ui("Copying transcript text to clipboard: ${transcriptText.take(50)}${if (transcriptText.length > 50) "..." else ""}")
            ClipboardUtils.copyToClipboard(context, transcriptText, "Transcript", true)
        } else if (intent.action == ACTION_INSERT_TRANSCRIPT) {
            val transcriptText = intent.getStringExtra(EXTRA_TRANSCRIPT_TEXT)
            
            if (transcriptText.isNullOrEmpty()) {
                Logger.error("Cannot insert empty transcript text")
                return
            }
            
            Logger.ui("Inserting transcript text into input field: ${transcriptText.take(50)}${if (transcriptText.length > 50) "..." else ""}")
            val success = AccessibilityUtils.insertTextIntoCurrentField(transcriptText)
            
            if (!success) {
                Logger.error("Failed to insert text into input field")
                Toast.makeText(
                    context,
                    "Failed to insert text. Make sure an input field is focused.",
                    Toast.LENGTH_LONG
                ).show()
                
                // Fall back to copying to clipboard
                ClipboardUtils.copyToClipboard(context, transcriptText, "Transcript", true)
            }
        }
    }
    
    companion object {
        const val ACTION_COPY_TRANSCRIPT = "com.example.audiorecorder.action.COPY_TRANSCRIPT"
        const val ACTION_INSERT_TRANSCRIPT = "com.example.audiorecorder.action.INSERT_TRANSCRIPT"
        const val EXTRA_TRANSCRIPT_TEXT = "com.example.audiorecorder.extra.TRANSCRIPT_TEXT"
    }
} 