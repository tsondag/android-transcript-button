package com.example.audiorecorder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.audiorecorder.utils.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class TranscriptItem(
    val file: File,
    val transcript: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun TranscriptList(
    transcripts: List<TranscriptItem>,
    onTranscriptClick: (TranscriptItem) -> Unit
) {
    Logger.ui("Displaying TranscriptList with ${transcripts.size} items")
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(transcripts) { item ->
            TranscriptCard(item)
        }
    }
}

@Composable
private fun TranscriptCard(item: TranscriptItem) {
    val dateFormat = remember { SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Date and time
            Text(
                text = dateFormat.format(Date(item.timestamp)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Transcript text
            Text(
                text = item.transcript ?: "Transcribing...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
} 