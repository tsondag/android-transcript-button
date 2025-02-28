package com.example.audiorecorder.utils

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.example.audiorecorder.service.TranscriptAccessibilityService

/**
 * Utility class for accessibility service operations
 */
object AccessibilityUtils {
    
    /**
     * Check if the accessibility service is enabled
     * 
     * @param context The context
     * @return True if the service is enabled
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            0
        }
        
        if (accessibilityEnabled != 1) return false
        
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        val serviceName = "${context.packageName}/${TranscriptAccessibilityService::class.java.canonicalName}"
        return enabledServices.split(':').any { it.equals(serviceName, ignoreCase = true) }
    }
    
    /**
     * Insert text into the currently focused input field
     * 
     * @param text The text to insert
     * @return True if the operation was successful
     */
    fun insertTextIntoCurrentField(text: String): Boolean {
        return TranscriptAccessibilityService.instance?.insertTextIntoFocusedField(text) ?: false
    }
} 