package com.example.audiorecorder

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.audiorecorder.databinding.ActivityVoiceMemosBinding
import com.example.audiorecorder.service.TranscriptionService
import com.example.audiorecorder.service.VoiceRecordingService
import com.example.audiorecorder.ui.TranscriptItem
import com.example.audiorecorder.ui.TranscriptViewModel
import com.example.audiorecorder.ui.VoiceMemoAdapter
import com.example.audiorecorder.utils.ClipboardUtils
import com.example.audiorecorder.utils.Logger
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import java.io.File

class VoiceMemoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVoiceMemosBinding
    private lateinit var voiceRecordingService: VoiceRecordingService
    private lateinit var transcriptionService: TranscriptionService
    private lateinit var adapter: VoiceMemoAdapter
    private var isRecording = false
    private var currentRecordingFile: File? = null
    private var showFloatingButton = true
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
        setupRecyclerView()
        setupSearchBar()
        setupFloatingButtonToggle()
        
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
                adapter.submitList(transcripts)
                updateEmptyState(transcripts.isEmpty())
            }
        }
        
        Logger.ui("VoiceMemoActivity setup completed")
    }

    private fun setupToolbar() {
        // We're directly using the view from the binding
        setSupportActionBar(null) // We're handling our toolbar manually
        
        val settingsButton = findViewById<ImageButton>(R.id.settingsButton)
        settingsButton.setOnClickListener {
            // Open SettingsActivity
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        Logger.ui("Setting up RecyclerView")
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = VoiceMemoAdapter(
            onItemClick = { transcript ->
                Logger.ui("Transcript clicked: ${transcript.file.name}")
                handleTranscriptClick(transcript)
            },
            onCopyClick = { transcript ->
                Logger.ui("Copy button clicked for: ${transcript.file.name}")
                transcript.transcript?.let { text ->
                    ClipboardUtils.copyToClipboard(this, text, "Transcript", true)
                }
            },
            onMenuClick = { transcript, view ->
                Logger.ui("Menu button clicked for: ${transcript.file.name}")
                // TODO: Show popup menu
                Toast.makeText(this, "Menu options coming soon", Toast.LENGTH_SHORT).show()
            },
            onPlayClick = { transcript ->
                Logger.ui("Play button clicked for: ${transcript.file.name}")
                // TODO: Implement playback
                Toast.makeText(this, "Playback coming soon", Toast.LENGTH_SHORT).show()
            }
        )
        
        recyclerView.adapter = adapter
    }
    
    private fun setupSearchBar() {
        val searchEditText = findViewById<EditText>(R.id.searchEditText)
        val clearSearchButton = findViewById<ImageButton>(R.id.clearSearchButton)
        
        searchEditText.setOnEditorActionListener { _, _, _ ->
            val query = searchEditText.text.toString()
            viewModel.setSearchQuery(query)
            true
        }
        
        clearSearchButton.setOnClickListener {
            searchEditText.text.clear()
            viewModel.setSearchQuery("")
            clearSearchButton.visibility = View.GONE
        }
        
        searchEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && searchEditText.text.isNotEmpty()) {
                clearSearchButton.visibility = View.VISIBLE
            } else {
                clearSearchButton.visibility = View.GONE
            }
        }
    }
    
    private fun setupFloatingButtonToggle() {
        val toggleButton = findViewById<Button>(R.id.toggleFloatingButton)
        
        toggleButton.setOnClickListener {
            showFloatingButton = !showFloatingButton
            toggleButton.text = if (showFloatingButton) 
                getString(R.string.hide_floating_button) 
            else 
                getString(R.string.show_floating_button)
            
            // TODO: Implement showing/hiding the floating button service
            Toast.makeText(this, 
                if (showFloatingButton) "Floating button enabled" else "Floating button disabled", 
                Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        val emptyState = findViewById<View>(R.id.emptyState)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        
        if (isEmpty) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun handleTranscriptClick(transcript: TranscriptItem) {
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
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            Logger.ui("Back pressed: returning to transcript list")
            supportFragmentManager.popBackStack()
            refreshTranscriptList()
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

    private fun refreshTranscriptList() {
        Logger.ui("Refreshing transcript list")
        viewModel.refreshTranscripts()
    }

    private fun hasRequiredPermissions(): Boolean {
        // Implement the logic to check if the necessary permissions are granted
        return true // Placeholder, actual implementation needed
    }
} 