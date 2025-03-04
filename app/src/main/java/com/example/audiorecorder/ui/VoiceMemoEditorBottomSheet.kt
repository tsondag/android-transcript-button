package com.example.audiorecorder.ui

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import com.example.audiorecorder.R
import com.example.audiorecorder.utils.AudioPlaybackManager
import com.example.audiorecorder.utils.Logger
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A bottom sheet dialog fragment for editing voice memo transcripts.
 */
class VoiceMemoEditorBottomSheet : BottomSheetDialogFragment(), AudioPlaybackManager.PlaybackCallback {
    
    private lateinit var viewModel: TranscriptViewModel
    private lateinit var transcript: TranscriptItem
    private lateinit var audioPlaybackManager: AudioPlaybackManager
    
    // UI Components
    private lateinit var dateTimeText: TextView
    private lateinit var durationText: TextView
    private lateinit var playButton: ImageButton
    private lateinit var transcriptEditText: EditText
    private lateinit var shareButton: ImageButton
    private lateinit var printButton: ImageButton
    private lateinit var deleteButton: ImageButton
    private lateinit var saveButton: View
    private lateinit var cancelButton: View
    
    // Callback for handling actions
    interface EditCallback {
        fun onTranscriptSaved(transcript: TranscriptItem, newText: String)
        fun onTranscriptDeleted(transcript: TranscriptItem)
        fun onTranscriptShared(transcript: TranscriptItem)
        fun onTranscriptPrint(transcript: TranscriptItem)
    }
    
    private var callback: EditCallback? = null
    
    companion object {
        const val ARG_TRANSCRIPT = "transcript"
        
        fun newInstance(transcript: TranscriptItem): VoiceMemoEditorBottomSheet {
            val fragment = VoiceMemoEditorBottomSheet()
            val args = Bundle()
            args.putSerializable(ARG_TRANSCRIPT, transcript)
            fragment.arguments = args
            return fragment
        }
    }
    
    fun setCallback(callback: EditCallback) {
        this.callback = callback
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get transcript from arguments
        @Suppress("DEPRECATION")
        transcript = arguments?.getSerializable(ARG_TRANSCRIPT) as TranscriptItem
        
        // Initialize view model
        viewModel = ViewModelProvider(requireActivity())[TranscriptViewModel::class.java]
        
        // Initialize audio playback manager
        audioPlaybackManager = AudioPlaybackManager(requireContext()).apply {
            setPlaybackCallback(this@VoiceMemoEditorBottomSheet)
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        
        // Set expanded state when shown
        dialog.setOnShowListener {
            val bottomSheet = (dialog as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                
                // Allow drag to dismiss
                behavior.isDraggable = true
                
                // Set callback for state changes
                behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                            dismiss()
                        }
                    }
                    
                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        // Not used
                    }
                })
                
                // Apply entrance animation
                bottomSheet.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up))
            }
        }
        
        return dialog
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_voice_memo_editor, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize UI components
        dateTimeText = view.findViewById(R.id.dateTimeText)
        durationText = view.findViewById(R.id.durationText)
        playButton = view.findViewById(R.id.playButton)
        transcriptEditText = view.findViewById(R.id.transcriptEditText)
        shareButton = view.findViewById(R.id.shareButton)
        printButton = view.findViewById(R.id.printButton)
        deleteButton = view.findViewById(R.id.deleteButton)
        saveButton = view.findViewById(R.id.saveButton)
        cancelButton = view.findViewById(R.id.cancelButton)
        
        // Populate UI with transcript data
        populateUI()
        
        // Set up click listeners
        setupClickListeners()
    }
    
    private fun populateUI() {
        // Format date/time
        val timestamp = transcript.timestamp
        val date = Date(timestamp)
        val dateFormat = SimpleDateFormat("MMMM d, yyyy - HH:mm", Locale.getDefault())
        dateTimeText.text = dateFormat.format(date)
        
        // Format duration
        durationText.text = formatDuration(calculateAudioDuration(transcript.file))
        
        // Set transcript text
        transcriptEditText.setText(transcript.transcript ?: "")
        
        // Set play button icon
        updatePlayButton(false)
    }
    
    private fun setupClickListeners() {
        // Play button
        playButton.setOnClickListener {
            toggleAudioPlayback()
        }
        
        // Share button
        shareButton.setOnClickListener {
            callback?.onTranscriptShared(transcript)
            dismiss()
        }
        
        // Print button
        printButton.setOnClickListener {
            callback?.onTranscriptPrint(transcript)
            dismiss()
        }
        
        // Delete button
        deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
        
        // Save button
        saveButton.setOnClickListener {
            saveTranscript()
        }
        
        // Cancel button
        cancelButton.setOnClickListener {
            // Check if text was modified
            if (transcript.transcript != transcriptEditText.text.toString()) {
                showDiscardChangesDialog()
            } else {
                dismiss()
            }
        }
    }
    
    private fun toggleAudioPlayback() {
        audioPlaybackManager.playAudio(transcript.file)
    }
    
    private fun saveTranscript() {
        val newText = transcriptEditText.text.toString()
        if (newText != transcript.transcript) {
            // Call the callback to save changes
            callback?.onTranscriptSaved(transcript, newText)
            Toast.makeText(requireContext(), "Transcript saved", Toast.LENGTH_SHORT).show()
        }
        dismiss()
    }
    
    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Voice Memo")
            .setMessage("Are you sure you want to delete this voice memo? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                callback?.onTranscriptDeleted(transcript)
                dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showDiscardChangesDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Discard Changes")
            .setMessage("You have unsaved changes. Are you sure you want to discard them?")
            .setPositiveButton("Discard") { _, _ -> dismiss() }
            .setNegativeButton("Keep Editing", null)
            .show()
    }
    
    private fun updatePlayButton(isPlaying: Boolean) {
        playButton.setImageResource(
            if (isPlaying) R.drawable.ic_stop else R.drawable.ic_play
        )
        playButton.contentDescription = 
            if (isPlaying) "Stop" else "Play"
    }
    
    // AudioPlaybackManager.PlaybackCallback implementations
    override fun onPlaybackStarted(file: File) {
        requireActivity().runOnUiThread {
            Logger.ui("Editor: Playback started: ${file.name}")
            updatePlayButton(true)
        }
    }
    
    override fun onPlaybackStopped(file: File) {
        requireActivity().runOnUiThread {
            Logger.ui("Editor: Playback stopped: ${file.name}")
            updatePlayButton(false)
        }
    }
    
    override fun onPlaybackCompleted(file: File) {
        requireActivity().runOnUiThread {
            Logger.ui("Editor: Playback completed: ${file.name}")
            updatePlayButton(false)
        }
    }
    
    override fun onPlaybackError(file: File, error: String) {
        requireActivity().runOnUiThread {
            Logger.ui("Editor: Playback error for ${file.name}: $error")
            updatePlayButton(false)
            Toast.makeText(requireContext(), "Error playing audio", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }
    
    private fun calculateAudioDuration(file: File): Int {
        // This is a placeholder. In a real app, you would want to get the actual audio duration
        // For demo purposes, we'll return a fake duration based on file size
        val fileSizeKB = file.length() / 1024
        return (fileSizeKB / 10).coerceAtMost(300).toInt() // Max 5 minutes for demo
    }
    
    override fun onDismiss(dialog: DialogInterface) {
        audioPlaybackManager.stopPlayback()
        super.onDismiss(dialog)
    }
    
    override fun onDestroy() {
        audioPlaybackManager.release()
        super.onDestroy()
    }
} 