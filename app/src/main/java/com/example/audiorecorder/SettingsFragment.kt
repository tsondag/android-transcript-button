package com.example.audiorecorder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
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
        const val PREF_ONLY_SHOW_ON_INPUT = "only_show_on_input"
        
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

        // Set up permissions section
        setupPermissionsSection()
        
        // Set up account section
        setupAccountSection()
        
        // Set up features section
        setupAutoCopySwitch()
        setupAutoInsertSwitch()
        setupOnlyShowOnInputSwitch()
        setupAutoDeleteSwitch()
        setupTagAudioEventsSwitch()
        
        // Set up about section
        setupAboutSection()
    }

    private fun setupPermissionsSection() {
        // Check if all required permissions are granted
        val hasAudioPermission = requireContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasOverlayPermission = Settings.canDrawOverlays(requireContext())
        
        // Update the permission status
        if (hasAudioPermission && hasOverlayPermission) {
            binding.permissionStatusIcon.setImageResource(R.drawable.ic_check)
            binding.permissionStatusText.text = "All required permissions granted"
            binding.permissionStatusText.setTextColor(resources.getColor(R.color.text_success, null))
        } else {
            binding.permissionStatusIcon.setImageResource(R.drawable.ic_alert_circle)
            binding.permissionStatusText.text = "Not all required permissions granted"
            binding.permissionStatusText.setTextColor(resources.getColor(R.color.colorDanger, null))
        }
        
        // Set up click listener for the permissions row
        binding.permissionsRow.setOnClickListener {
            // Navigate to the permissions fragment
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, PermissionsFragment())
                .addToBackStack(null)
                .commit()
        }
    }
    
    private fun setupAccountSection() {
        // For demo purposes, we'll just set some placeholder values
        binding.nameValue.text = "John Doe"
        binding.emailValue.text = "john.doe@example.com"
        binding.planValue.text = "Premium"
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
                
                // Navigate to the permissions fragment
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, PermissionsFragment())
                    .addToBackStack(null)
                    .commit()
                
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
    
    private fun setupOnlyShowOnInputSwitch() {
        val isOnlyShowOnInput = prefs.getBoolean(PREF_ONLY_SHOW_ON_INPUT, false)
        binding.onlyShowOnInputSwitch.isChecked = isOnlyShowOnInput
        
        binding.onlyShowOnInputSwitch.setOnCheckedChangeListener { _, isChecked ->
            Logger.ui("Only show on input switch toggled: $isChecked")
            prefs.edit().putBoolean(PREF_ONLY_SHOW_ON_INPUT, isChecked).apply()
            
            // Show confirmation toast
            val message = if (isChecked) {
                "Floating button will only show when an input field is selected"
            } else {
                "Floating button will always be visible"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            
            // Update the floating button service if it's running
            if (isFloatingButtonVisible) {
                val intent = Intent(requireContext(), FloatingButtonService::class.java)
                requireContext().startService(intent)
            }
        }
    }
    
    private fun setupAutoDeleteSwitch() {
        // Set up the spinner for auto-delete period selection
        val spinner = binding.autoDeletePeriodSpinner
        val periods = resources.getStringArray(R.array.auto_delete_periods)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, periods)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        
        // Set the initial selection based on saved preference
        val periodIndex = periods.indexOf(autoDeletePeriod)
        if (periodIndex >= 0) {
            spinner.setSelection(periodIndex)
        }
        
        // Update spinner visibility based on auto-delete state
        spinner.visibility = if (isAutoDeleteEnabled) View.VISIBLE else View.GONE
        
        // Set up the switch
        binding.autoDeleteSwitch.isChecked = isAutoDeleteEnabled
        binding.autoDeleteSwitch.setOnCheckedChangeListener { _, isChecked ->
            Logger.ui("Auto-delete switch toggled: $isChecked")
            isAutoDeleteEnabled = isChecked
            prefs.edit().putBoolean(PREF_AUTO_DELETE_ENABLED, isChecked).apply()
            
            // Show/hide the period dropdown
            spinner.visibility = if (isChecked) View.VISIBLE else View.GONE
            
            // Update the description text
            updateAutoDeleteDescription()
            
            // Show confirmation toast
            val message = if (isChecked) {
                "Notes will be automatically deleted after $autoDeletePeriod"
            } else {
                "Auto-delete disabled"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
        
        // Set up spinner listener
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedPeriod = periods[position]
                if (selectedPeriod != autoDeletePeriod) {
                    Logger.ui("Auto-delete period changed: $selectedPeriod")
                    autoDeletePeriod = selectedPeriod
                    prefs.edit().putString(PREF_AUTO_DELETE_PERIOD, selectedPeriod).apply()
                    
                    // Update the description text
                    updateAutoDeleteDescription()
                    
                    // Show confirmation toast if auto-delete is enabled
                    if (isAutoDeleteEnabled) {
                        val message = "Notes will be automatically deleted after $autoDeletePeriod"
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Nothing to do
            }
        }
        
        // Initialize the description text
        updateAutoDeleteDescription()
    }
    
    private fun updateAutoDeleteDescription() {
        binding.autoDeleteDescription.text = if (isAutoDeleteEnabled) {
            "Automatically delete notes after $autoDeletePeriod"
        } else {
            "Automatically delete notes after selected time"
        }
    }

    private fun setupTagAudioEventsSwitch() {
        binding.tagAudioEventsSwitch.isChecked = isTagAudioEventsEnabled
        binding.tagAudioEventsSwitch.setOnCheckedChangeListener { _, isChecked ->
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
    
    private fun setupAboutSection() {
        // Set up FAQ row
        binding.faqRow.setOnClickListener {
            // Open FAQ page (for demo, we'll just show a toast)
            Toast.makeText(requireContext(), "Opening FAQ page", Toast.LENGTH_SHORT).show()
        }
        
        // Set up Request a change row
        binding.requestChangeRow.setOnClickListener {
            // Open request change page (for demo, we'll just show a toast)
            Toast.makeText(requireContext(), "Opening request change page", Toast.LENGTH_SHORT).show()
        }
        
        // Set up Logout row
        binding.logoutRow.setOnClickListener {
            // Logout (for demo, we'll just show a toast)
            Toast.makeText(requireContext(), "Logging out", Toast.LENGTH_SHORT).show()
        }
        
        // Set up Delete account row
        binding.deleteAccountRow.setOnClickListener {
            // Delete account (for demo, we'll just show a toast)
            Toast.makeText(requireContext(), "Deleting account", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Update permission status when returning to the fragment
        setupPermissionsSection()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 