package com.example.audiorecorder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiorecorder.service.TranscriptionService
import com.example.audiorecorder.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class TranscriptViewModel(application: Application) : AndroidViewModel(application) {
    private val transcriptionService = TranscriptionService(application)
    private val _transcripts = MutableStateFlow<List<TranscriptItem>>(emptyList())
    val transcripts: StateFlow<List<TranscriptItem>> = _transcripts.asStateFlow()
    
    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _filteredTranscripts = MutableStateFlow<List<TranscriptItem>>(emptyList())
    val filteredTranscripts: StateFlow<List<TranscriptItem>> = _filteredTranscripts.asStateFlow()

    init {
        loadRecordings()
    }

    fun refreshTranscripts() {
        Logger.ui("Refreshing transcripts list")
        loadRecordings()
    }
    
    fun setSearchQuery(query: String) {
        Logger.ui("Setting search query: '$query'")
        _searchQuery.value = query
        updateFilteredTranscripts()
    }
    
    private fun updateFilteredTranscripts() {
        val query = _searchQuery.value
        val allTranscripts = _transcripts.value
        
        _filteredTranscripts.value = if (query.isBlank()) {
            allTranscripts
        } else {
            allTranscripts.filter { item ->
                item.transcript?.contains(query, ignoreCase = true) == true ||
                item.file.nameWithoutExtension.contains(query, ignoreCase = true)
            }
        }
        
        Logger.ui("Filtered transcripts: ${_filteredTranscripts.value.size} of ${allTranscripts.size}")
    }

    private fun loadRecordings() {
        Logger.ui("Loading recordings from storage")
        viewModelScope.launch {
            try {
                val recordingsDir = getApplication<Application>().getExternalFilesDir(null)
                Logger.ui("Searching for recordings in directory: ${recordingsDir?.absolutePath}")
                
                if (recordingsDir == null || !recordingsDir.exists()) {
                    Logger.ui("Recordings directory does not exist")
                    _transcripts.value = emptyList()
                    updateFilteredTranscripts()
                    return@launch
                }
                
                val files = recordingsDir.listFiles()
                Logger.ui("Found ${files?.size ?: 0} total files in directory")
                
                val audioAndTranscriptFiles = files?.filter { file ->
                    file.extension == "m4a" || file.extension == "txt"
                } ?: emptyList()
                
                Logger.ui("Found ${audioAndTranscriptFiles.count { it.extension == "m4a" }} audio files and ${audioAndTranscriptFiles.count { it.extension == "txt" }} transcript files")
                
                // Group files by base name
                val groupedFiles = audioAndTranscriptFiles.groupBy { it.nameWithoutExtension }
                Logger.ui("Grouped files into ${groupedFiles.size} sets by base name")
                
                val recordings = groupedFiles
                    .mapNotNull { (name, files) ->
                        val audioFile = files.find { it.extension == "m4a" }
                        val transcriptFile = files.find { it.extension == "txt" }
                        
                        if (audioFile != null) {
                            Logger.ui("Processing audio file: ${audioFile.name}")
                            
                            val transcript = if (transcriptFile != null) {
                                try {
                                    Logger.ui("Reading transcript from file: ${transcriptFile.name}")
                                    val content = transcriptFile.readText()
                                    Logger.ui("Transcript content length: ${content.length} characters")
                                    content
                                } catch (e: Exception) {
                                    Logger.ui("Error reading transcript file: ${transcriptFile.name}", e)
                                    null
                                }
                            } else {
                                Logger.ui("No transcript file found for audio: ${audioFile.name}")
                                null
                            }
                            
                            TranscriptItem(
                                file = audioFile,
                                transcript = transcript,
                                timestamp = audioFile.lastModified()
                            )
                        } else {
                            Logger.ui("Skipping group '$name' - no audio file found")
                            null
                        }
                    }
                    .sortedByDescending { it.timestamp }
                
                Logger.ui("Found ${recordings.size} recordings with audio files")
                Logger.ui("Found ${recordings.count { it.transcript != null }} recordings with transcripts")
                
                // Sort by timestamp ascending (oldest first) so newest items appear at the bottom
                _transcripts.value = recordings.sortedBy { it.timestamp }
                updateFilteredTranscripts()
            } catch (e: Exception) {
                Logger.ui("Error loading recordings", e)
                _transcripts.value = emptyList()
                updateFilteredTranscripts()
            }
        }
    }

    fun transcribeAudio(item: TranscriptItem) {
        if (item.transcript != null) {
            Logger.ui("Transcript already exists for ${item.file.name}")
            return
        }

        viewModelScope.launch {
            Logger.ui("Starting transcription for ${item.file.name}")
            transcriptionService.transcribeAudio(item.file)
                .onSuccess { transcript ->
                    Logger.ui("Transcription successful for ${item.file.name}")
                    updateTranscript(item, transcript)
                }
                .onFailure { error ->
                    Logger.ui("Transcription failed for ${item.file.name}", error)
                }
        }
    }

    private fun updateTranscript(item: TranscriptItem, transcript: String) {
        val currentList = _transcripts.value.toMutableList()
        val index = currentList.indexOfFirst { it.file == item.file }
        if (index != -1) {
            currentList[index] = item.copy(transcript = transcript)
            _transcripts.value = currentList
            updateFilteredTranscripts()
            Logger.ui("Updated transcript for ${item.file.name}")
        }
    }

    fun addTranscript(file: File, transcript: String) {
        Logger.ui("Adding new transcript for file: ${file.name}")
        
        // Save transcript to a file
        viewModelScope.launch {
            try {
                val transcriptFile = File(file.parentFile, "${file.nameWithoutExtension}.txt")
                transcriptFile.writeText(transcript)
                Logger.ui("Saved transcript to file: ${transcriptFile.name}")
                
                val newTranscript = TranscriptItem(
                    file = file,
                    transcript = transcript,
                    timestamp = System.currentTimeMillis()
                )
                
                val currentList = _transcripts.value.toMutableList()
                // Remove existing item with the same file path if it exists
                val existingIndex = currentList.indexOfFirst { it.file.absolutePath == file.absolutePath }
                if (existingIndex != -1) {
                    currentList.removeAt(existingIndex)
                }
                
                // Add the new transcript at the end (since we're showing newest at bottom)
                currentList.add(newTranscript)
                _transcripts.value = currentList
                updateFilteredTranscripts()
                Logger.ui("Added new transcript to list: ${transcript.take(50)}...")
            } catch (e: Exception) {
                Logger.ui("Failed to save transcript", e)
            }
        }
    }
} 