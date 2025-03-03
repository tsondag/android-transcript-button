package com.example.audiorecorder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.example.audiorecorder.databinding.FragmentSettingsBinding
import com.example.audiorecorder.service.FloatingButtonService
import com.example.audiorecorder.utils.AccessibilityUtils
import com.example.audiorecorder.utils.Logger

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: SharedPreferences
    private var isFloatingButtonVisible = false
    private var isAutoCopyEnabled = false
    private var isAutoInsertEnabled = false
    private var isAutoTranslateEnabled = false
    private var isAutoDeleteEnabled = false
    private var isTagAudioEventsEnabled = false
    private var autoDeletePeriod = "1 day" // Default value
    private var selectedLanguage = "German" // Default value

    companion object {
        const val PREF_FLOATING_BUTTON_VISIBLE = "floating_button_visible"
        const val PREF_AUTO_COPY_ENABLED = "auto_copy_enabled"
        const val PREF_AUTO_INSERT_ENABLED = "auto_insert_enabled"
        const val PREF_AUTO_TRANSLATE_ENABLED = "auto_translate_enabled"
        const val PREF_AUTO_DELETE_ENABLED = "auto_delete_enabled"
        const val PREF_AUTO_DELETE_PERIOD = "auto_delete_period"
        const val PREF_SELECTED_LANGUAGE = "selected_language"
        const val PREF_AUTO_DETECT_LANGUAGE_ENABLED = "auto_detect_language_enabled"
        const val PREF_SMART_BUTTON_BEHAVIOR = "smart_button_behavior"
        const val PREF_TAG_AUDIO_EVENTS_ENABLED = "tag_audio_events_enabled"
        const val PREF_NOTIFICATION_TOGGLE_ENABLED = "notification_toggle_enabled"
        
        /**
         * Check if auto-detect language is enabled in settings
         * @param context The context to access shared preferences
         * @return true if auto-detect language is enabled, false otherwise
         */
        fun isAutoDetectLanguageEnabled(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean(PREF_AUTO_DETECT_LANGUAGE_ENABLED, true) // Default to true
        }
        
        /**
         * Check if tagging audio events is enabled in settings
         * @param context The context to access shared preferences
         * @return true if tagging audio events is enabled, false otherwise
         */
        fun isTagAudioEventsEnabled(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean(PREF_TAG_AUDIO_EVENTS_ENABLED, false) // Default to false
        }
        
        /**
         * Check if the notification toggle is enabled (default: true which means button is visible)
         */
        fun isNotificationToggleEnabled(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean(PREF_NOTIFICATION_TOGGLE_ENABLED, true) // Default to true = button visible
        }
        
        /**
         * Save the notification toggle state
         */
        fun setNotificationToggleEnabled(context: Context, enabled: Boolean) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit().putBoolean(PREF_NOTIFICATION_TOGGLE_ENABLED, enabled).apply()
        }
        
        /**
         * Check if keyboard-only mode is enabled
         * @param context The context to access shared preferences
         * @return Always returns true as this behavior is now integrated into Smart Button Behavior
         */
        fun isKeyboardOnlyModeEnabled(context: Context): Boolean {
            return true // Always return true as we're handling this via Smart Button Behavior now
        }
        
        /**
         * Check if smart button behavior is enabled
         * @param context The context to access shared preferences
         * @return true if smart button behavior is enabled (hide when minimized, show when recording)
         */
        fun isSmartButtonBehaviorEnabled(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean(PREF_SMART_BUTTON_BEHAVIOR, true) // Default to true
        }
        
        /**
         * Check if the app is currently minimized
         * @param context The context to access service state
         * @return true if app is minimized, false if it's in foreground
         * 
         * Note: This is a stub implementation that always returns false, as the true state is managed by
         * TranscriptAccessibilityService and passed via intents. This method is only used for testing
         * and fallback purposes when the state is not available from the intent.
         */
        fun isAppMinimized(context: Context): Boolean {
            // For keyboard visibility changes, we cannot determine app minimization state directly
            // The real state is managed by TranscriptAccessibilityService and passed via intents
            // This is just a fallback that assumes the app is not minimized
            return false
        }
    }

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Logger.ui("Audio permission result: $isGranted")
        updateAudioPermissionButton()
        if (!isGranted) {
            Toast.makeText(requireContext(), "Audio permission is required for recording", Toast.LENGTH_LONG).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Logger.ui("Notification permission result: $isGranted")
        updateNotificationPermissionButton()
        if (isGranted && Settings.canDrawOverlays(requireContext())) {
            startFloatingButtonService()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        isFloatingButtonVisible = prefs.getBoolean(PREF_FLOATING_BUTTON_VISIBLE, false)
        isAutoCopyEnabled = prefs.getBoolean(PREF_AUTO_COPY_ENABLED, false)
        isAutoInsertEnabled = prefs.getBoolean(PREF_AUTO_INSERT_ENABLED, false)
        isAutoTranslateEnabled = prefs.getBoolean(PREF_AUTO_TRANSLATE_ENABLED, false)
        isAutoDeleteEnabled = prefs.getBoolean(PREF_AUTO_DELETE_ENABLED, false)
        isTagAudioEventsEnabled = prefs.getBoolean(PREF_TAG_AUDIO_EVENTS_ENABLED, false)
        autoDeletePeriod = prefs.getString(PREF_AUTO_DELETE_PERIOD, "1 day") ?: "1 day"
        selectedLanguage = prefs.getString(PREF_SELECTED_LANGUAGE, "German") ?: "German"
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Logger.ui("Setting up SettingsFragment")

        setupAudioPermissionButton()
        setupOverlayPermissionButton()
        setupNotificationPermissionButton()
        setupFloatingButtonToggle()
        setupAutoCopySwitch()
        setupAutoInsertSwitch()
        setupAutoTranslateSwitch()
        setupLanguageSpinner()
        setupAutoDetectLanguageSwitch()
        setupAutoDeleteSwitch()
        setupAutoDeletePeriodSpinner()
        setupTagAudioEventsSwitch()

        // Set up smart button behavior switch
        binding.switchSmartButtonBehavior.isChecked = prefs.getBoolean(PREF_SMART_BUTTON_BEHAVIOR, true)
        binding.switchSmartButtonBehavior.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_SMART_BUTTON_BEHAVIOR, isChecked).apply()
            Logger.ui("Smart button behavior toggled: $isChecked")
            
            // Update the floating button service if it's running
            if (isFloatingButtonVisible) {
                val intent = Intent(requireContext(), FloatingButtonService::class.java)
                requireContext().startService(intent)
            }
        }

        // Restore floating button state if it was visible
        if (isFloatingButtonVisible && hasRequiredPermissions()) {
            startFloatingButtonService()
        }
    }

    private fun setupAudioPermissionButton() {
        binding.audioPermissionButton.setOnClickListener {
            Logger.ui("Audio permission button clicked")
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        updateAudioPermissionButton()
    }

    private fun updateAudioPermissionButton() {
        val hasPermission = requireContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        // Show/hide the appropriate views
        binding.audioPermissionStatus.visibility = if (hasPermission) View.VISIBLE else View.GONE
        binding.audioPermissionButton.visibility = if (hasPermission) View.GONE else View.VISIBLE
        
        Logger.ui("Audio permission status updated: $hasPermission")
    }

    private fun setupOverlayPermissionButton() {
        binding.overlayPermissionButton.setOnClickListener {
            Logger.ui("Overlay permission button clicked")
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            )
            startActivity(intent)
        }
        updateOverlayPermissionButton()
    }

    private fun updateOverlayPermissionButton() {
        val hasPermission = Settings.canDrawOverlays(requireContext())
        
        // Show/hide the appropriate views
        binding.overlayPermissionStatus.visibility = if (hasPermission) View.VISIBLE else View.GONE
        binding.overlayPermissionButton.visibility = if (hasPermission) View.GONE else View.VISIBLE
        
        Logger.ui("Overlay permission status updated: $hasPermission")
    }

    private fun setupNotificationPermissionButton() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            binding.notificationPermissionCard.visibility = View.VISIBLE
            binding.notificationPermissionButton.setOnClickListener {
                Logger.ui("Notification permission button clicked")
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            updateNotificationPermissionButton()
            
            // Update the description to indicate it's optional
            binding.notificationPermissionDescription.text = "Optional for recording service notification"
        } else {
            binding.notificationPermissionCard.visibility = View.GONE
        }
    }

    private fun updateNotificationPermissionButton() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            // Show/hide the appropriate views
            binding.notificationPermissionStatus.visibility = if (hasPermission) View.VISIBLE else View.GONE
            binding.notificationPermissionButton.visibility = if (hasPermission) View.GONE else View.VISIBLE
            
            Logger.ui("Notification permission status updated: $hasPermission")
        }
    }

    private fun setupFloatingButtonToggle() {
        binding.toggleFloatingButton.setOnClickListener {
            Logger.ui("Toggle floating button clicked")
            toggleFloatingButton()
        }
        updateFloatingButtonToggle()
    }

    private fun setupAutoCopySwitch() {
        binding.autoCopySwitch.isChecked = isAutoCopyEnabled
        binding.autoCopySwitch.setOnCheckedChangeListener { _, isChecked ->
            Logger.ui("Auto-copy switch toggled: $isChecked")
            isAutoCopyEnabled = isChecked
            prefs.edit().putBoolean(PREF_AUTO_COPY_ENABLED, isChecked).apply()
            
            // Show confirmation toast
            val message = if (isChecked) {
                "Transcripts will be automatically copied to clipboard"
            } else {
                "Auto-copy disabled"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAutoInsertSwitch() {
        binding.autoInsertSwitch.isChecked = isAutoInsertEnabled
        
        // Update accessibility service status text
        updateAccessibilityServiceStatus()
        
        // Update the description to indicate it's optional
        binding.accessibilityPermissionDescription.text = "Optional for auto-inserting text into input fields"
        
        // Setup accessibility settings button
        binding.accessibilitySettingsButton.setOnClickListener {
            Logger.ui("Accessibility settings button clicked")
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
        
        // Setup auto-insert switch
        binding.autoInsertSwitch.setOnCheckedChangeListener { _, isChecked ->
            Logger.ui("Auto-insert switch toggled: $isChecked")
            
            if (isChecked && !AccessibilityUtils.isAccessibilityServiceEnabled(requireContext())) {
                // If trying to enable but accessibility service is not enabled
                Toast.makeText(
                    requireContext(),
                    "Please enable the accessibility service first",
                    Toast.LENGTH_LONG
                ).show()
                
                // Reset the switch
                binding.autoInsertSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
            
            isAutoInsertEnabled = isChecked
            prefs.edit().putBoolean(PREF_AUTO_INSERT_ENABLED, isChecked).apply()
            
            // Show confirmation toast
            val message = if (isChecked) {
                "Transcripts will be automatically inserted into input fields"
            } else {
                "Auto-insert disabled"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAutoTranslateSwitch() {
        binding.autoTranslateSwitch.isChecked = isAutoTranslateEnabled
        binding.autoTranslateSwitch.setOnCheckedChangeListener { _, isChecked ->
            Logger.ui("Auto-translate switch toggled: $isChecked")
            isAutoTranslateEnabled = isChecked
            prefs.edit().putBoolean(PREF_AUTO_TRANSLATE_ENABLED, isChecked).apply()
            
            // Show confirmation toast
            val message = if (isChecked) {
                "Audio will be automatically translated to selected language"
            } else {
                "Auto-translate disabled"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            
            // Enable/disable language dropdown based on auto-translate setting
            binding.languageInputLayout.isEnabled = isChecked
        }
        
        // Set initial state of language dropdown
        binding.languageInputLayout.isEnabled = isAutoTranslateEnabled
    }
    
    private fun setupLanguageSpinner() {
        val languages = arrayOf("German", "French", "Spanish", "Italian", "Japanese", "Chinese", "Russian")
        
        // Create adapter for the AutoCompleteTextView
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner_dropdown,
            languages
        )
        
        // Set up the AutoCompleteTextView with the adapter
        binding.languageDropdown.setAdapter(adapter)
        
        // Set initial value
        binding.languageDropdown.setText(selectedLanguage, false)
        
        // Set listener for item selection
        binding.languageDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedLanguage = languages[position]
            prefs.edit().putString(PREF_SELECTED_LANGUAGE, selectedLanguage).apply()
            Logger.ui("Language selected: $selectedLanguage")
        }
        
        // Enable/disable based on auto-translate setting
        binding.languageInputLayout.isEnabled = isAutoTranslateEnabled
    }
    
    private fun setupAutoDetectLanguageSwitch() {
        // Get current value from preferences
        val isAutoDetectLanguageEnabled = prefs.getBoolean(PREF_AUTO_DETECT_LANGUAGE_ENABLED, true)
        
        // Set initial state of the switch
        binding.switchAutoDetectLanguage.isChecked = isAutoDetectLanguageEnabled
        
        // Show/hide the language dropdown based on auto-detect setting
        binding.languageInputLayoutWhenAutoDetectOff.visibility = 
            if (isAutoDetectLanguageEnabled) View.GONE else View.VISIBLE
        
        // Set up listener for switch changes
        binding.switchAutoDetectLanguage.setOnCheckedChangeListener { _, isChecked ->
            Logger.ui("Auto-detect language switch toggled: $isChecked")
            
            // Save to preferences
            prefs.edit().putBoolean(PREF_AUTO_DETECT_LANGUAGE_ENABLED, isChecked).apply()
            
            // Show/hide the language dropdown based on auto-detect setting
            binding.languageInputLayoutWhenAutoDetectOff.visibility = 
                if (isChecked) View.GONE else View.VISIBLE
            
            // Show confirmation toast
            val message = if (isChecked) {
                "Language will be automatically detected in recordings"
            } else {
                "Language must be manually selected for recordings"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupAutoDeleteSwitch() {
        binding.autoDeleteSwitch.isChecked = isAutoDeleteEnabled
        binding.autoDeleteSwitch.setOnCheckedChangeListener { _, isChecked ->
            Logger.ui("Auto-delete switch toggled: $isChecked")
            isAutoDeleteEnabled = isChecked
            prefs.edit().putBoolean(PREF_AUTO_DELETE_ENABLED, isChecked).apply()
            
            // Show confirmation toast
            val message = if (isChecked) {
                "Notes will be automatically deleted after $autoDeletePeriod"
            } else {
                "Auto-delete disabled"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            
            // Enable/disable period dropdown based on auto-delete setting
            binding.autoDeletePeriodInputLayout.isEnabled = isChecked
        }
        
        // Set initial state of period dropdown
        binding.autoDeletePeriodInputLayout.isEnabled = isAutoDeleteEnabled
    }
    
    private fun setupAutoDeletePeriodSpinner() {
        val periods = arrayOf("1 hour", "4 hours", "1 day", "1 week", "1 month", "3 months", "6 months", "1 year")
        
        // Create adapter for the AutoCompleteTextView
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner_dropdown,
            periods
        )
        
        // Set up the AutoCompleteTextView with the adapter
        binding.autoDeletePeriodDropdown.setAdapter(adapter)
        
        // Set initial value
        binding.autoDeletePeriodDropdown.setText(autoDeletePeriod, false)
        
        // Set listener for item selection
        binding.autoDeletePeriodDropdown.setOnItemClickListener { _, _, position, _ ->
            autoDeletePeriod = periods[position]
            prefs.edit().putString(PREF_AUTO_DELETE_PERIOD, autoDeletePeriod).apply()
            Logger.ui("Auto-delete period selected: $autoDeletePeriod")
            
            // Update toast message if auto-delete is enabled
            if (isAutoDeleteEnabled) {
                Toast.makeText(requireContext(), "Notes will be automatically deleted after $autoDeletePeriod", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Enable/disable based on auto-delete setting
        binding.autoDeletePeriodInputLayout.isEnabled = isAutoDeleteEnabled
    }

    private fun updateAccessibilityServiceStatus() {
        val isAccessibilityEnabled = AccessibilityUtils.isAccessibilityServiceEnabled(requireContext())
        
        // Show/hide the appropriate views
        binding.accessibilityPermissionStatus.visibility = if (isAccessibilityEnabled) View.VISIBLE else View.GONE
        binding.accessibilitySettingsButton.visibility = if (isAccessibilityEnabled) View.GONE else View.VISIBLE
        
        // Update the status text
        binding.accessibilityServiceStatus.text = if (isAccessibilityEnabled) {
            "Accessibility service enabled"
        } else {
            "Accessibility service required for auto-insert"
        }
        
        Logger.ui("Accessibility service status updated: $isAccessibilityEnabled")
    }

    private fun updateFloatingButtonToggle() {
        val hasOverlayPermission = Settings.canDrawOverlays(requireContext())
        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        
        binding.toggleFloatingButton.text = if (isFloatingButtonVisible) "Hide" else "Show"
        binding.toggleFloatingButton.isEnabled = hasOverlayPermission
        
        if (!hasOverlayPermission) {
            binding.toggleFloatingButton.text = "Overlay Permission Required"
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            // Make it clear that notification permission is optional
            binding.toggleFloatingButton.isEnabled = true
            binding.toggleFloatingButton.text = if (isFloatingButtonVisible) "Hide" else "Show (No Notification)"
        }
        
        Logger.ui("Floating button toggle updated: $isFloatingButtonVisible, enabled: ${binding.toggleFloatingButton.isEnabled}")
    }

    private fun toggleFloatingButton() {
        isFloatingButtonVisible = !isFloatingButtonVisible
        prefs.edit().putBoolean(PREF_FLOATING_BUTTON_VISIBLE, isFloatingButtonVisible).apply()
        
        if (isFloatingButtonVisible) {
            startFloatingButtonService()
        } else {
            stopFloatingButtonService()
        }
        
        updateFloatingButtonToggle()
    }

    private fun startFloatingButtonService() {
        val hasOverlayPermission = Settings.canDrawOverlays(requireContext())
        if (!hasOverlayPermission) {
            Toast.makeText(requireContext(), "Overlay permission is required", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Create intent for the service
        val intent = Intent(requireContext(), FloatingButtonService::class.java)
        
        // Start as foreground service for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent)
            Logger.ui("Started floating button as foreground service")
        } else {
            requireContext().startService(intent)
            Logger.ui("Started floating button service")
        }
        
        Toast.makeText(requireContext(), "Floating button enabled", Toast.LENGTH_SHORT).show()
        Logger.ui("Floating button service started")
    }

    private fun stopFloatingButtonService() {
        requireContext().stopService(Intent(requireContext(), FloatingButtonService::class.java))
        Toast.makeText(requireContext(), "Floating button disabled", Toast.LENGTH_SHORT).show()
        Logger.ui("Floating button service stopped")
    }

    private fun hasRequiredPermissions(): Boolean {
        val hasAudioPermission = requireContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasOverlayPermission = Settings.canDrawOverlays(requireContext())
        
        // Notification permission is now optional
        return hasAudioPermission && hasOverlayPermission
    }

    private fun setupTagAudioEventsSwitch() {
        binding.switchTagAudioEvents.isChecked = isTagAudioEventsEnabled
        binding.switchTagAudioEvents.setOnCheckedChangeListener { _, isChecked ->
            Logger.ui("Tag audio events switch toggled: $isChecked")
            isTagAudioEventsEnabled = isChecked
            prefs.edit().putBoolean(PREF_TAG_AUDIO_EVENTS_ENABLED, isChecked).apply()
            
            // Show confirmation toast
            val message = if (isChecked) {
                "Audio events (like laughter, footsteps) will be tagged in transcripts"
            } else {
                "Audio events will not be tagged in transcripts"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Update permission buttons when returning to the fragment
        updateAudioPermissionButton()
        updateOverlayPermissionButton()
        updateNotificationPermissionButton()
        updateAccessibilityServiceStatus()
        updateFloatingButtonToggle()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 