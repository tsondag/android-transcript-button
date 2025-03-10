package com.example.audiorecorder

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintJob
import android.print.PrintManager
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.audiorecorder.databinding.ActivityVoiceMemosBinding
import com.example.audiorecorder.service.FloatingButtonService
import com.example.audiorecorder.service.TranscriptionService
import com.example.audiorecorder.service.VoiceRecordingService
import com.example.audiorecorder.ui.TranscriptItem
import com.example.audiorecorder.ui.TranscriptViewModel
import com.example.audiorecorder.ui.VoiceMemoAdapter
import com.example.audiorecorder.ui.VoiceMemoEditorBottomSheet
import com.example.audiorecorder.utils.AudioPlaybackManager
import com.example.audiorecorder.utils.ClipboardUtils
import com.example.audiorecorder.utils.Logger
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceMemoActivity : AppCompatActivity(), AudioPlaybackManager.PlaybackCallback {
    private lateinit var binding: ActivityVoiceMemosBinding
    private lateinit var voiceRecordingService: VoiceRecordingService
    private lateinit var transcriptionService: TranscriptionService
    private lateinit var adapter: VoiceMemoAdapter
    private lateinit var audioPlaybackManager: AudioPlaybackManager
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
        audioPlaybackManager = AudioPlaybackManager(this).apply {
            setPlaybackCallback(this@VoiceMemoActivity)
        }

        setupToolbar()
        setupRecyclerView()
        setupSearchBar()
        
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
                
                // Scroll to the bottom of the list (the latest item)
                if (transcripts.isNotEmpty()) {
                    findViewById<RecyclerView>(R.id.recyclerView).smoothScrollToPosition(transcripts.size - 1)
                }
            }
        }
        
        Logger.ui("VoiceMemoActivity setup completed")
    }

    private fun setupToolbar() {
        // We're directly using the view from the binding
        setSupportActionBar(null) // We're handling our toolbar manually
        
        val settingsButton = findViewById<MaterialButton>(R.id.settingsButton)
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
            onItemClick = this::handleTranscriptClick,
            onCopyClick = this::copyTranscriptToClipboard,
            onMenuClick = this::handleMenuItemClick,
            onPlayClick = this::toggleAudioPlayback,
            onRetryClick = this::retryTranscription
        )
        
        recyclerView.adapter = adapter
    }
    
    private fun toggleAudioPlayback(transcript: TranscriptItem) {
        Logger.ui("Toggle audio playback for: ${transcript.file.name}")
        audioPlaybackManager.playAudio(transcript.file)
    }
    
    // AudioPlaybackManager.PlaybackCallback implementations
    override fun onPlaybackStarted(file: File) {
        runOnUiThread {
            Logger.ui("Playback started: ${file.name}")
            adapter.updatePlayingState(file, true)
            Toast.makeText(this, "Playing audio...", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onPlaybackStopped(file: File) {
        runOnUiThread {
            Logger.ui("Playback stopped: ${file.name}")
            adapter.updatePlayingState(file, false)
        }
    }
    
    override fun onPlaybackCompleted(file: File) {
        runOnUiThread {
            Logger.ui("Playback completed: ${file.name}")
            adapter.updatePlayingState(null, false)
        }
    }
    
    override fun onPlaybackError(file: File, error: String) {
        runOnUiThread {
            Logger.ui("Playback error for ${file.name}: $error")
            adapter.updatePlayingState(null, false)
            Toast.makeText(this, "Error playing audio", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun handleMenuItemClick(transcript: TranscriptItem, view: View) {
        // Get the menu item that was clicked from the tag that was set
        when (view.tag) {
            R.id.action_share -> shareTranscript(transcript)
            R.id.action_print -> printTranscript(transcript)
            R.id.action_delete -> showDeleteConfirmationDialog(transcript)
            else -> {
                // If no specific tag, just determine by clicked ID
                when (view.id) {
                    R.id.action_share -> shareTranscript(transcript)
                    R.id.action_print -> printTranscript(transcript)
                    R.id.action_delete -> showDeleteConfirmationDialog(transcript)
                }
            }
        }
    }
    
    private fun shareTranscript(transcript: TranscriptItem) {
        transcript.transcript?.let { text ->
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Voice Memo Transcript")
            
            // Format a nice header with the date if available
            val dateStr = SimpleDateFormat("MMMM d, yyyy - HH:mm", Locale.getDefault())
                .format(Date(transcript.timestamp))
            
            val shareText = "Voice Memo ($dateStr)\n\n$text"
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText)
            
            startActivity(Intent.createChooser(shareIntent, "Share Transcript"))
        } ?: Toast.makeText(this, "No transcript available to share", Toast.LENGTH_SHORT).show()
    }
    
    private fun printTranscript(transcript: TranscriptItem) {
        transcript.transcript?.let { text ->
            // Get the print manager
            val printManager = getSystemService(PRINT_SERVICE) as PrintManager
            
            // Create a WebView to hold the formatted content
            val webView = WebView(this)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // Create a print document adapter from the WebView
                    val printAdapter = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        // For API 21+
                        webView.createPrintDocumentAdapter("Voice Memo")
                    } else {
                        // For API < 21
                        @Suppress("DEPRECATION")
                        webView.createPrintDocumentAdapter()
                    }
                    
                    // Create a print job with name and adapter instance
                    val jobName = "Voice Memo - ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(Date(transcript.timestamp))}"
                    
                    // Start the print job
                    printManager.print(
                        jobName,
                        printAdapter,
                        PrintAttributes.Builder().build()
                    )
                }
            }
            
            // Format the transcript with HTML for better printing
            val dateStr = SimpleDateFormat("MMMM d, yyyy - HH:mm", Locale.getDefault())
                .format(Date(transcript.timestamp))
            
            val htmlDocument = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Voice Memo Transcript</title>
                    <style>
                        body { font-family: sans-serif; margin: 20px; }
                        h1 { font-size: 18px; color: #333; }
                        .date { color: #666; margin-bottom: 20px; }
                        .transcript { line-height: 1.5; }
                    </style>
                </head>
                <body>
                    <h1>Voice Memo Transcript</h1>
                    <div class="date">$dateStr</div>
                    <div class="transcript">$text</div>
                </body>
                </html>
            """.trimIndent()
            
            webView.loadDataWithBaseURL(null, htmlDocument, "text/HTML", "UTF-8", null)
        } ?: Toast.makeText(this, "No transcript available to print", Toast.LENGTH_SHORT).show()
    }
    
    private fun showDeleteConfirmationDialog(transcript: TranscriptItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Voice Memo")
            .setMessage("Are you sure you want to delete this voice memo? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteVoiceMemo(transcript)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteVoiceMemo(transcript: TranscriptItem) {
        // If this file is currently playing, stop playback first
        if (audioPlaybackManager.getCurrentFile()?.absolutePath == transcript.file.absolutePath) {
            audioPlaybackManager.stopPlayback()
        }
        
        // Delete the audio file
        val fileDeleted = transcript.file.delete()
        
        if (fileDeleted) {
            // Refresh the list after deletion
            viewModel.refreshTranscripts()
            Toast.makeText(this, "Voice memo deleted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to delete voice memo", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupSearchBar() {
        Logger.ui("Setting up search bar")
        val searchEditText = findViewById<EditText>(R.id.searchEditText)
        val clearSearchButton = findViewById<ImageButton>(R.id.clearSearchButton)
        val micToggleButton = findViewById<MaterialButton>(R.id.micToggleButton)
        
        // Set initial mic button state based on saved preference
        val isMicEnabled = SettingsFragment.isNotificationToggleEnabled(this)
        updateMicButtonState(isMicEnabled)
        
        // Setup mic toggle button
        micToggleButton.setOnClickListener {
            val newState = !SettingsFragment.isNotificationToggleEnabled(this)
            updateMicButtonState(newState)
        }
        
        // Setup search functionality
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                clearSearchButton.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                viewModel.setSearchQuery(query)
            }
            
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        clearSearchButton.setOnClickListener {
            searchEditText.text.clear()
            clearSearchButton.visibility = View.GONE
        }
    }
    
    private fun updateMicButtonState(isEnabled: Boolean) {
        val micToggleButton = findViewById<MaterialButton>(R.id.micToggleButton)
        
        if (isEnabled) {
            // Mic is enabled
            micToggleButton.icon = ContextCompat.getDrawable(this, R.drawable.ic_mic)
            micToggleButton.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.colorSuccess)
            )
        } else {
            // Mic is disabled
            micToggleButton.icon = ContextCompat.getDrawable(this, R.drawable.ic_mic_off_with_line)
            micToggleButton.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.destructive)
            )
        }
        
        // Save the state to preferences
        SettingsFragment.setNotificationToggleEnabled(this, isEnabled)
        
        // Update the floating button service state
        val intent = Intent(this, FloatingButtonService::class.java)
        intent.action = if (isEnabled) {
            FloatingButtonService.ACTION_ENABLE
        } else {
            FloatingButtonService.ACTION_DISABLE
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
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
        Logger.ui("Opening editor for transcript: ${transcript.file.name}")
        
        // Create and show the bottom sheet
        val bottomSheet = VoiceMemoEditorBottomSheet.newInstance(transcript)
        bottomSheet.setCallback(object : VoiceMemoEditorBottomSheet.EditCallback {
            override fun onTranscriptSaved(transcript: TranscriptItem, newText: String) {
                Logger.ui("Saving edited transcript: ${transcript.file.name}")
                viewModel.updateTranscriptText(transcript, newText)
            }
            
            override fun onTranscriptDeleted(transcript: TranscriptItem) {
                Logger.ui("Deleting transcript from editor: ${transcript.file.name}")
                deleteVoiceMemo(transcript)
            }
            
            override fun onTranscriptShared(transcript: TranscriptItem) {
                Logger.ui("Sharing transcript from editor: ${transcript.file.name}")
                shareTranscript(transcript)
            }
            
            override fun onTranscriptPrint(transcript: TranscriptItem) {
                Logger.ui("Printing transcript from editor: ${transcript.file.name}")
                printTranscript(transcript)
            }
        })
        
        bottomSheet.show(supportFragmentManager, "VoiceMemoEditor")
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
        
        // Stop any ongoing playback
        audioPlaybackManager.stopPlayback()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Release the media player resources
        audioPlaybackManager.release()
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

    /**
     * Retry transcription for a failed item
     */
    private fun retryTranscription(transcript: TranscriptItem) {
        Logger.ui("Retrying transcription for: ${transcript.file.name}")
        viewModel.retryTranscription(transcript)
        Toast.makeText(this, "Retrying transcription...", Toast.LENGTH_SHORT).show()
    }

    /**
     * Copy transcript text to clipboard
     */
    private fun copyTranscriptToClipboard(transcript: TranscriptItem) {
        Logger.ui("Copy button clicked for: ${transcript.file.name}")
        transcript.transcript?.let { text ->
            ClipboardUtils.copyToClipboard(this, text, "Transcript", true)
        }
    }
} 