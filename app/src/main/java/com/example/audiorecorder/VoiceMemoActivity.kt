package com.example.audiorecorder

import android.content.Intent
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.audiorecorder.databinding.ActivityVoiceMemosBinding
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
                // Handle menu item click based on which item was clicked
                handleMenuItemClick(transcript, view)
            },
            onPlayClick = { transcript ->
                Logger.ui("Play button clicked for: ${transcript.file.name}")
                toggleAudioPlayback(transcript)
            }
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
        val searchEditText = findViewById<EditText>(R.id.searchEditText)
        val clearSearchButton = findViewById<ImageButton>(R.id.clearSearchButton)
        
        // Use a handler for debouncing search queries
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var searchRunnable: Runnable? = null
        
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = searchEditText.text.toString()
                viewModel.setSearchQuery(query)
                return@setOnEditorActionListener true
            }
            false
        }
        
        // Show clear button when text is entered and handle search
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Not used
            }
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Update clear button visibility
                val query = s?.toString() ?: ""
                clearSearchButton.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                
                // Remove any pending search queries
                searchRunnable?.let { handler.removeCallbacks(it) }
                
                // Schedule a new search with a short delay to avoid too frequent updates
                searchRunnable = Runnable {
                    Logger.ui("Performing search for query: '$query'")
                    viewModel.setSearchQuery(query)
                }.also {
                    // Only add a delay if it's not an empty query
                    if (query.isNotEmpty()) {
                        handler.postDelayed(it, 150) // 150ms delay for typing
                    } else {
                        handler.post(it) // No delay for empty query
                    }
                }
            }
            
            override fun afterTextChanged(s: android.text.Editable?) {
                // Not used
            }
        })
        
        clearSearchButton.setOnClickListener {
            searchEditText.text.clear()
            viewModel.setSearchQuery("")
            clearSearchButton.visibility = View.GONE
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
} 