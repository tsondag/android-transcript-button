package com.example.audiorecorder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.audiorecorder.service.FloatingButtonService

@Composable
fun MainScreen(
    onStartService: () -> Unit,
    hasOverlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!hasOverlayPermission) {
            Text(
                text = "Overlay permission is required for the floating button.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(onClick = onRequestOverlayPermission) {
                Text("Grant Overlay Permission")
            }
        } else {
            Button(onClick = onStartService) {
                Text("Start Recording Button")
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "📱 Audio Recorder v1.0.0",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.d("AudioRecorder", "Audio permission granted")
            // Only check overlay permission if we're not coming from the floating button
            if (intent?.getBooleanExtra("REQUEST_AUDIO_PERMISSION", false) != true) {
                checkAndRequestOverlayPermission()
            }
        } else {
            android.util.Log.e("AudioRecorder", "Audio permission denied")
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if we need to request audio permission
        if (intent?.getBooleanExtra("REQUEST_AUDIO_PERMISSION", false) == true) {
            android.util.Log.d("AudioRecorder", "Requesting audio permission from floating button callback")
            requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onStartService = { startFloatingButtonService() },
                        hasOverlayPermission = Settings.canDrawOverlays(this),
                        onRequestOverlayPermission = { checkAndRequestOverlayPermission() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        
        // Handle permission request from floating button
        if (intent?.getBooleanExtra("REQUEST_AUDIO_PERMISSION", false) == true) {
            android.util.Log.d("AudioRecorder", "Requesting audio permission from floating button callback (onNewIntent)")
            requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
        
        // Handle settings open action from notification
        if (intent?.action == FloatingButtonService.ACTION_OPEN_SETTINGS) {
            android.util.Log.d("AudioRecorder", "Opening settings from notification")
            val settingsIntent = Intent(this, SettingsActivity::class.java)
            startActivity(settingsIntent)
        }
    }

    private fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            android.util.Log.d("AudioRecorder", "Requesting overlay permission")
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            try {
                startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("AudioRecorder", "Error launching overlay permission settings", e)
                Toast.makeText(
                    this,
                    "Please enable overlay permission manually in Settings",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            android.util.Log.d("AudioRecorder", "Overlay permission already granted")
            startFloatingButtonService()
        }
    }

    private fun startFloatingButtonService() {
        android.util.Log.d("AudioRecorder", "Starting floating button service")
        if (!Settings.canDrawOverlays(this)) {
            android.util.Log.e("AudioRecorder", "Cannot start service - overlay permission not granted")
            checkAndRequestOverlayPermission()
            return
        }

        // Check for audio permission before starting service
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            android.util.Log.d("AudioRecorder", "Requesting audio permission")
            requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            return
        }
        
        val serviceIntent = Intent(this, FloatingButtonService::class.java)
        android.util.Log.d("AudioRecorder", "Created service intent")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        android.util.Log.d("AudioRecorder", "Service started")
    }

    private fun showPermissionDeniedDialog() {
        // Show a dialog explaining why we need the permission
        Toast.makeText(
            this,
            "Audio recording permission is required for this app to work",
            Toast.LENGTH_LONG
        ).show()
    }
} 