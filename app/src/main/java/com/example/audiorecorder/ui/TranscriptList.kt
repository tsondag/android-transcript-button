package com.example.audiorecorder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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
    onTranscriptClick: (TranscriptItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TranscriptViewModel? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredTranscripts = remember(searchQuery, transcripts) {
        if (searchQuery.isBlank()) {
            transcripts
        } else {
            transcripts.filter { item ->
                item.transcript?.contains(searchQuery, ignoreCase = true) == true ||
                item.file.nameWithoutExtension.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    Logger.ui("Displaying TranscriptList with ${filteredTranscripts.size} items (filtered from ${transcripts.size})")
    
    Column(modifier = modifier.fillMaxSize()) {
        // Search bar
        SearchBar(
            query = searchQuery,
            onQueryChange = { 
                searchQuery = it
                viewModel?.setSearchQuery(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        // Transcript list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = filteredTranscripts,
                key = { it.file.absolutePath }
            ) { item ->
                TranscriptCard(
                    item = item,
                    onClick = { onTranscriptClick(item) }
                )
            }
            
            // Show empty state if no results
            if (filteredTranscripts.isEmpty()) {
                item {
                    EmptyState(
                        message = if (searchQuery.isBlank()) 
                            "No recordings found" 
                        else 
                            "No results for \"$searchQuery\""
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search transcripts") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            containerColor = Color(0xFFF5F5F5),
            unfocusedBorderColor = Color.Transparent
        )
    )
}

@Composable
private fun TranscriptCard(
    item: TranscriptItem,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
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
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
} 