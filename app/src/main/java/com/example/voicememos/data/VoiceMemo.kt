package com.example.voicememos.data

data class VoiceMemo(
    val id: String,
    val time: String,
    val transcript: String,
    val audioUrl: String,
    val duration: String
) 