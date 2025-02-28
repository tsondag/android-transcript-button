package com.example.audiorecorder

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.audiorecorder.databinding.ActivityVoiceMemosBinding
import com.example.audiorecorder.service.TranscriptionService
import com.example.audiorecorder.service.VoiceRecordingService
import com.example.audiorecorder.ui.TranscriptList
import com.example.audiorecorder.ui.TranscriptViewModel
import com.example.audiorecorder.utils.ClipboardUtils
import com.example.audiorecorder.utils.Logger
import kotlinx.coroutines.launch
import java.io.File

class VoiceMemoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVoiceMemosBinding
    private lateinit var voiceRecordingService: VoiceRecordingService
    private lateinit var transcriptionService: TranscriptionService
    private var isRecording = false
    private var currentRecordingFile: File? = null
    private val viewModel: TranscriptViewModel by viewModels()
    
    companion object {
        private const val TAG = "VoiceMemoActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        private const val REQUEST_CODE_PERMISSIONS = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.ui("Creating VoiceMemoActivity")
        
        binding = ActivityVoiceMemosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        voiceRecordingService = VoiceRecordingService(this)
        transcriptionService = TranscriptionService(this)

        setupToolbar()
        setupTranscriptList()
        
        // Request necessary permissions
        if (!hasRequiredPermissions()) {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
        
        // Observe transcripts state flow
        lifecycleScope.launch {
            viewModel.transcripts.collect { transcripts ->
                Logger.ui("Transcripts state updated: ${transcripts.size} items")
                if (transcripts.isNotEmpty()) {
                    Logger.ui("First transcript: ${transcripts[0].file.name}, has transcript: ${transcripts[0].transcript != null}")
                }
            }
        }
        
        Logger.ui("VoiceMemoActivity setup completed")
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        val settingsButton = findViewById<ImageButton>(R.id.settingsButton)
        settingsButton.setOnClickListener {
            // Open settings or show settings dialog
            Toast.makeText(this, "Settings clicked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTranscriptList() {
        Logger.ui("Setting up transcript list view")
        val transcriptListView = ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    val transcripts by viewModel.transcripts.collectAsState()
                    Logger.ui("Composing TranscriptList with ${transcripts.size} items")
                    
                    TranscriptList(
                        transcripts = transcripts,
                        onTranscriptClick = { transcript ->
                            Logger.ui("Transcript clicked: ${transcript.file.name}")
                            handleTranscriptClick(transcript)
                        },
                        viewModel = viewModel
                    )
                }
            }
        }
        binding.transcriptsContainer.addView(transcriptListView)
        Logger.ui("Transcript list view added to container")
    }
    
    private fun handleTranscriptClick(transcript: com.example.audiorecorder.ui.TranscriptItem) {
        // For now, just copy the transcript to clipboard
        transcript.transcript?.let { text ->
            if (text != "Transcribing..." && text != "Transcription failed") {
                ClipboardUtils.copyToClipboard(this, text, "Transcript", true)
            } else {
                Toast.makeText(this, "Transcript not available yet", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "No transcript available", Toast.LENGTH_SHORT).show()
        }
        
        // TODO: In the future, implement play functionality
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            Logger.ui("Back pressed: returning to transcript list")
            supportFragmentManager.popBackStack()
            showTranscriptList()
        } else {
            Logger.ui("Back pressed: exiting activity")
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        Logger.ui("Activity resumed, refreshing transcripts")
        
        // Always refresh the transcripts list when resuming
        viewModel.refreshTranscripts()
        
        // Only show the transcript list if we're not in the settings fragment
        if (supportFragmentManager.backStackEntryCount == 0) {
            showTranscriptList()
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (isRecording) {
            stopRecording()
        }
    }

    private fun stopRecording() {
        // Simple method to stop recording if needed
        isRecording = false
        Logger.ui("Recording stopped due to activity pause")
    }

    private fun showTranscriptList() {
        Logger.ui("Showing transcript list")
        binding.transcriptsContainer.visibility = View.VISIBLE
        
        // Refresh the list when showing it
        viewModel.refreshTranscripts()
    }

    private fun hasRequiredPermissions(): Boolean {
        // Implement the logic to check if the necessary permissions are granted
        return true // Placeholder, actual implementation needed
    }
} 