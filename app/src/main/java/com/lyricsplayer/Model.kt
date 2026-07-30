package com.lyricsplayer

import androidx.media3.common.MediaItem

data class Song(
    val id: Int,
    val mediaItem: MediaItem,
    val lyrics: List<LyricLine>
)

data class LyricLine(
    val timestamp: Long,
    val text: String
)