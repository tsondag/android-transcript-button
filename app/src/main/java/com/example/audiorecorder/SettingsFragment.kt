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
    private var isAutoDetectLanguageEnabled = false

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
        isAutoDetectLanguageEnabled = prefs.getBoolean(PREF_AUTO_DETECT_LANGUAGE, true) // Default to true
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
        setupAutoDetectLanguageSwitch()

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
        binding.audioPermissionButton.text = if (hasPermission) "Permission Granted" else "Grant Permission"
        binding.audioPermissionButton.isEnabled = !hasPermission
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
        binding.overlayPermissionButton.text = if (hasPermission) "Permission Granted" else "Grant Permission"
        binding.overlayPermissionButton.isEnabled = !hasPermission
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
        } else {
            binding.notificationPermissionCard.visibility = View.GONE
        }
    }

    private fun updateNotificationPermissionButton() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            binding.notificationPermissionButton.text = if (hasPermission) "Permission Granted" else "Grant Permission"
            binding.notificationPermissionButton.isEnabled = !hasPermission
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

    private fun setupAutoDetectLanguageSwitch() {
        binding.autoDetectLanguageSwitch.isChecked = isAutoDetectLanguageEnabled
        binding.autoDetectLanguageSwitch.setOnCheckedChangeListener { _, isChecked ->
            Logger.ui("Auto-detect language switch toggled: $isChecked")
            isAutoDetectLanguageEnabled = isChecked
            prefs.edit().putBoolean(PREF_AUTO_DETECT_LANGUAGE, isChecked).apply()
            
            // Show confirmation toast
            val message = if (isChecked) {
                "Language will be auto-detected without translation"
            } else {
                "English transcription enabled"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAccessibilityServiceStatus() {
        val isServiceEnabled = AccessibilityUtils.isAccessibilityServiceEnabled(requireContext())
        
        binding.accessibilityServiceStatus.text = if (isServiceEnabled) {
            "Accessibility service enabled"
        } else {
            "Accessibility service required"
        }
        
        binding.accessibilityServiceStatus.setTextColor(
            requireContext().getColor(
                if (isServiceEnabled) R.color.text_success else R.color.text_secondary
            )
        )
        
        binding.accessibilitySettingsButton.text = if (isServiceEnabled) {
            "Accessibility Settings"
        } else {
            "Enable Accessibility Service"
        }
        
        // Disable auto-insert if accessibility service is not enabled
        if (!isServiceEnabled && binding.autoInsertSwitch.isChecked) {
            binding.autoInsertSwitch.isChecked = false
            isAutoInsertEnabled = false
            prefs.edit().putBoolean(PREF_AUTO_INSERT_ENABLED, false).apply()
        }
    }

    private fun toggleFloatingButton() {
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions()
            return
        }

        isFloatingButtonVisible = !isFloatingButtonVisible
        prefs.edit().putBoolean(PREF_FLOATING_BUTTON_VISIBLE, isFloatingButtonVisible).apply()

        if (isFloatingButtonVisible) {
            startFloatingButtonService()
        } else {
            stopFloatingButtonService()
        }
        updateFloatingButtonToggle()
    }

    private fun hasRequiredPermissions(): Boolean {
        val hasOverlay = Settings.canDrawOverlays(requireContext())
        val hasAudio = requireContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return hasOverlay && hasAudio && hasNotification
    }

    private fun requestRequiredPermissions() {
        when {
            !Settings.canDrawOverlays(requireContext()) -> {
                Toast.makeText(requireContext(), "Overlay permission is required for the floating button", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")
                )
                startActivity(intent)
            }
            requireContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startFloatingButtonService() {
        Logger.ui("Starting floating button service")
        requireContext().startService(Intent(requireContext(), FloatingButtonService::class.java))
    }

    private fun stopFloatingButtonService() {
        Logger.ui("Stopping floating button service")
        requireContext().stopService(Intent(requireContext(), FloatingButtonService::class.java))
    }

    private fun updateFloatingButtonToggle() {
        val hasRequiredPermissions = hasRequiredPermissions()
        binding.toggleFloatingButton.isEnabled = hasRequiredPermissions
        binding.toggleFloatingButton.text = when {
            !hasRequiredPermissions -> "Permissions Required"
            isFloatingButtonVisible -> "Hide Button"
            else -> "Show Button"
        }
        Logger.ui("Floating button toggle updated: visible=$isFloatingButtonVisible, enabled=$hasRequiredPermissions")
    }

    override fun onResume() {
        super.onResume()
        updateAudioPermissionButton()
        updateOverlayPermissionButton()
        updateNotificationPermissionButton()
        updateFloatingButtonToggle()
        updateAccessibilityServiceStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val PREF_FLOATING_BUTTON_VISIBLE = "floating_button_visible"
        private const val PREF_AUTO_COPY_ENABLED = "auto_copy_enabled"
        private const val PREF_AUTO_INSERT_ENABLED = "auto_insert_enabled"
        private const val PREF_AUTO_DETECT_LANGUAGE = "auto_detect_language"
        
        /**
         * Check if auto-copy is enabled in preferences
         */
        fun isAutoCopyEnabled(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean(PREF_AUTO_COPY_ENABLED, false)
        }
        
        /**
         * Check if auto-insert is enabled in preferences
         */
        fun isAutoInsertEnabled(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean(PREF_AUTO_INSERT_ENABLED, false)
        }
        
        /**
         * Check if auto-detect language is enabled in preferences
         */
        fun isAutoDetectLanguageEnabled(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean(PREF_AUTO_DETECT_LANGUAGE, true) // Default to true
        }
    }
} 