package com.example.audiorecorder

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.example.audiorecorder.databinding.ActivityVoiceMemosBinding
import com.example.audiorecorder.service.TranscriptionService
import com.example.audiorecorder.service.VoiceRecordingService
import com.example.audiorecorder.ui.TranscriptList
import com.example.audiorecorder.ui.TranscriptViewModel
import com.example.audiorecorder.utils.ClipboardUtils
import com.example.audiorecorder.utils.Logger
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import java.io.File

class VoiceMemoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVoiceMemosBinding
    private lateinit var voiceRecordingService: VoiceRecordingService
    private lateinit var transcriptionService: TranscriptionService
    private var isRecording = false
    private var currentRecordingFile: File? = null
    private val viewModel: TranscriptViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.ui("Creating VoiceMemoActivity")
        
        binding = ActivityVoiceMemosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        voiceRecordingService = VoiceRecordingService(this)
        transcriptionService = TranscriptionService(this)

        setupToolbar()
        setupTranscriptList()
        setupRecordButton()
        
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
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Voice Memos"
        
        binding.settingsButton.setOnClickListener {
            Logger.ui("Settings button clicked")
            val settingsFragment = supportFragmentManager.findFragmentById(R.id.mainContent)
            if (settingsFragment is SettingsFragment) {
                Logger.ui("Closing settings and showing transcripts")
                supportFragmentManager.popBackStack()
                showTranscriptList()
            } else {
                Logger.ui("Opening settings and hiding transcripts")
                supportFragmentManager.beginTransaction()
                    .replace(R.id.mainContent, SettingsFragment())
                    .addToBackStack(null)
                    .commit()
                binding.transcriptsContainer.visibility = View.GONE
            }
        }
    }
    
    private fun setupRecordButton() {
        val recordButton = binding.recordButton
        
        recordButton.setOnClickListener {
            if (isRecording) {
                Logger.ui("Record button clicked: stopping recording")
                recordButton.setImageResource(R.drawable.ic_mic)
                stopRecordingAndTranscribe()
            } else {
                Logger.ui("Record button clicked: starting recording")
                recordButton.setImageResource(R.drawable.ic_stop)
                startRecording()
            }
        }
    }

    private fun showTranscriptList() {
        Logger.ui("Showing transcript list")
        binding.transcriptsContainer.visibility = View.VISIBLE
        
        // Refresh the list when showing it
        viewModel.refreshTranscripts()
    }

    private fun startRecording() {
        Logger.ui("Starting new recording")
        
        lifecycleScope.launch {
            voiceRecordingService.startRecording()
                .onSuccess { file ->
                    currentRecordingFile = file
                    isRecording = true
                    Logger.ui("Recording started successfully")
                    
                    // Update UI to show recording state
                    runOnUiThread {
                        binding.recordButton.setImageResource(R.drawable.ic_stop)
                        Toast.makeText(this@VoiceMemoActivity, "Recording started", Toast.LENGTH_SHORT).show()
                    }
                }
                .onFailure { error ->
                    Logger.ui("Failed to start recording", error)
                    Toast.makeText(this@VoiceMemoActivity, "Failed to start recording", Toast.LENGTH_SHORT).show()
                    
                    // Reset UI
                    runOnUiThread {
                        binding.recordButton.setImageResource(R.drawable.ic_mic)
                    }
                }
        }
    }

    private fun stopRecordingAndTranscribe() {
        Logger.ui("Stopping recording and starting transcription")
        
        lifecycleScope.launch {
            voiceRecordingService.stopRecording()
                .onSuccess { file ->
                    isRecording = false
                    Logger.ui("Recording stopped successfully, starting transcription")
                    
                    // Update UI to show stopped state
                    runOnUiThread {
                        binding.recordButton.setImageResource(R.drawable.ic_mic)
                    }
                    
                    // Show the transcript list and add a placeholder
                    runOnUiThread {
                        showTranscriptList()
                        viewModel.addTranscript(file, "Transcribing...") // Add placeholder
                    }
                    
                    // Only show "Transcribing..." toast for longer recordings (> 30 seconds)
                    val recordingDuration = (System.currentTimeMillis() - file.lastModified()) / 1000
                    if (recordingDuration > 30) {
                        Toast.makeText(this@VoiceMemoActivity, "Transcribing...", Toast.LENGTH_SHORT).show()
                    }
                    
                    transcriptionService.transcribeAudio(file)
                        .onSuccess { transcript ->
                            Logger.ui("Transcription completed successfully, updating list")
                            runOnUiThread {
                                viewModel.addTranscript(file, transcript)
                                showTranscriptList() // Refresh the list
                            }
                        }
                        .onFailure { error ->
                            Logger.ui("Transcription failed", error)
                            runOnUiThread {
                                Toast.makeText(this@VoiceMemoActivity, "Failed to transcribe audio", Toast.LENGTH_SHORT).show()
                                viewModel.addTranscript(file, "Transcription failed") // Update with error state
                            }
                        }
                }
                .onFailure { error ->
                    Logger.ui("Failed to stop recording", error)
                    Toast.makeText(this@VoiceMemoActivity, "Failed to stop recording", Toast.LENGTH_SHORT).show()
                }
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
        
        // If recording is in progress, stop it
        if (isRecording) {
            Logger.ui("Activity paused while recording, stopping recording")
            stopRecordingAndTranscribe()
        }
    }
} 