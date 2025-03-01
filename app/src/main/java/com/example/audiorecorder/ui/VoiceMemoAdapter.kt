package com.example.audiorecorder.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoiceMemoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_voice_memo, parent, false)
        return VoiceMemoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VoiceMemoViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
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
                    onMenuClick(getItem(position), menuButton)
                }
            }

            playButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPlayClick(getItem(position))
                }
            }
        }

        fun bind(item: TranscriptItem) {
            // Format the date/time
            val timestamp = item.timestamp
            val date = Date(timestamp)
            val calendar = Calendar.getInstance()
            val today = Calendar.getInstance()
            calendar.time = date
            
            // Format date as "Today" or "Yesterday" or MM/dd/yyyy
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
                    SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(date)
                }
            }
            
            // Format time as 24h format
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            dateTimeText.text = "$dateStr $timeStr"
            
            // Set the transcript text
            transcriptText.text = item.transcript ?: "No transcript available"
            
            // Set the duration text (placeholder)
            durationText.text = calculateAudioDuration(item.file)
        }
        
        private fun calculateAudioDuration(file: File): String {
            // This is a placeholder. In a real app, you would want to get the actual audio duration
            // For demo purposes, we'll return a fake duration based on file size
            val fileSizeKB = file.length() / 1024
            val seconds = (fileSizeKB / 10).coerceAtMost(300) // Max 5 minutes for demo
            
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            
            return String.format("%02d:%02d", minutes, remainingSeconds)
        }
    }

    companion object {
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