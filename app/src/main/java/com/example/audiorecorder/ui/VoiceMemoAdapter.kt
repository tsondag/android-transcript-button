package com.example.audiorecorder.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.audiorecorder.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VoiceMemoAdapter(
    private val onItemClick: (TranscriptItem) -> Unit,
    private val onCopyClick: (TranscriptItem) -> Unit,
    private val onMenuClick: (TranscriptItem, View) -> Unit,
    private val onPlayClick: (TranscriptItem) -> Unit
) : ListAdapter<TranscriptItem, VoiceMemoAdapter.VoiceMemoViewHolder>(DIFF_CALLBACK) {

    // Keep track of currently playing file
    private var playingFile: File? = null

    fun updatePlayingState(file: File?, isPlaying: Boolean) {
        val oldPlayingFile = playingFile
        playingFile = if (isPlaying) file else null
        
        // Update the previous playing item
        oldPlayingFile?.let { oldFile ->
            val oldPosition = currentList.indexOfFirst { it.file.absolutePath == oldFile.absolutePath }
            if (oldPosition >= 0) {
                notifyItemChanged(oldPosition, PAYLOAD_PLAY_STATE_CHANGED)
            }
        }
        
        // Update the new playing item if different
        if (file != null && (oldPlayingFile == null || oldPlayingFile.absolutePath != file.absolutePath)) {
            val newPosition = currentList.indexOfFirst { it.file.absolutePath == file.absolutePath }
            if (newPosition >= 0) {
                notifyItemChanged(newPosition, PAYLOAD_PLAY_STATE_CHANGED)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoiceMemoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_voice_memo, parent, false)
        return VoiceMemoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VoiceMemoViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }
    
    override fun onBindViewHolder(holder: VoiceMemoViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty() || payloads.none { it == PAYLOAD_PLAY_STATE_CHANGED }) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        
        // Only update the play button icon if payload is for play state change
        val item = getItem(position)
        holder.updatePlayButton(item)
    }

    inner class VoiceMemoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateTimeText: TextView = itemView.findViewById(R.id.dateTimeText)
        private val durationText: TextView = itemView.findViewById(R.id.durationText)
        private val playButton: ImageButton = itemView.findViewById(R.id.playButton)
        private val transcriptText: TextView = itemView.findViewById(R.id.transcriptText)
        private val copyButton: ImageButton = itemView.findViewById(R.id.copyButton)
        private val menuButton: ImageButton = itemView.findViewById(R.id.menuButton)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }

            copyButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onCopyClick(getItem(position))
                }
            }

            menuButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    // Show the popup menu with all options
                    showPopupMenu(menuButton, getItem(position))
                }
            }

            playButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPlayClick(getItem(position))
                }
            }
        }

        fun updatePlayButton(item: TranscriptItem) {
            val isPlaying = playingFile?.absolutePath == item.file.absolutePath
            playButton.setImageResource(
                if (isPlaying) R.drawable.ic_stop else R.drawable.ic_play
            )
            playButton.contentDescription = 
                if (isPlaying) "Stop" else "Play"
        }

        private fun showPopupMenu(view: View, item: TranscriptItem) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.menu_voice_memo_options, popup.menu)
            
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_copy -> {
                        onCopyClick(item)
                        true
                    }
                    R.id.action_share, R.id.action_print, R.id.action_delete -> {
                        // Create a temporary view with the menu item ID as tag
                        val actionView = View(view.context)
                        actionView.tag = menuItem.itemId
                        
                        // Pass to activity to handle these operations
                        onMenuClick(item, actionView)
                        true
                    }
                    else -> false
                }
            }
            
            popup.show()
        }

        fun bind(item: TranscriptItem) {
            // Format the date/time
            val timestamp = item.timestamp
            val date = Date(timestamp)
            val calendar = Calendar.getInstance()
            val today = Calendar.getInstance()
            calendar.time = date
            
            // Format date as "Today" or "Yesterday" or "MMM dd - HH:mm"
            val dateStr = when {
                calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> {
                    "Today"
                }
                calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) - 1 -> {
                    "Yesterday"
                }
                else -> {
                    // Format as "Feb 01 - "
                    SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
                }
            }
            
            // Format time as 24h format
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            
            // Combine date and time with proper formatting
            dateTimeText.text = if (dateStr == "Today" || dateStr == "Yesterday") {
                "$dateStr $timeStr"
            } else {
                "$dateStr - $timeStr"
            }
            
            // Set the transcript text
            transcriptText.text = item.transcript ?: "No transcript available"
            
            // Format duration as MM:SS
            durationText.text = formatDuration(calculateAudioDuration(item.file))
            
            // Update play button state
            updatePlayButton(item)
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
    }

    companion object {
        private const val PAYLOAD_PLAY_STATE_CHANGED = "play_state_changed"
        
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TranscriptItem>() {
            override fun areItemsTheSame(oldItem: TranscriptItem, newItem: TranscriptItem): Boolean {
                return oldItem.file.absolutePath == newItem.file.absolutePath
            }

            override fun areContentsTheSame(oldItem: TranscriptItem, newItem: TranscriptItem): Boolean {
                return oldItem.file.absolutePath == newItem.file.absolutePath &&
                       oldItem.transcript == newItem.transcript &&
                       oldItem.timestamp == newItem.timestamp
            }
        }
    }
} 