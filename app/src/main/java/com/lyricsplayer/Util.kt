package com.lyricsplayer

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

fun getSongs(
    context: Context
): List<Song> {
    return listOf(
        Song(
            id = 1,
            mediaItem = MediaItem.Builder()
                .setUri("android.resource://com.lyricsplayer/${R.raw.through_glass}".toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Through Glass")
                        .setArtist("Stonesour")
                        .setArtworkUri("android.resource://com.lyricsplayer/${R.drawable.stone_sour}".toUri())
                        .build()
                )
                .build(),
            lyrics = loadLyricsFromAssets(context, "through_glass")
        ),
        Song(
            id = 2,
            mediaItem = MediaItem.Builder()
                .setUri("android.resource://com.lyricsplayer/${R.raw.atlantic}".toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Atlantic")
                        .setArtist("Sleep Token")
                        .setArtworkUri("android.resource://com.lyricsplayer/${R.drawable.sleep_token}".toUri())
                        .build()
                )
                .build(),
            lyrics = loadLyricsFromAssets(context, "atlantic")
        ),
        Song(
            id = 3,
            mediaItem = MediaItem.Builder()
                .setUri("android.resource://com.lyricsplayer/${R.raw.my_gift_of_silence}".toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("My Gift of Silence")
                        .setArtist("Blackfield")
                        .setArtworkUri("android.resource://com.lyricsplayer/${R.drawable.blackfield}".toUri())
                        .build()
                )
                .build(),
            lyrics = loadLyricsFromAssets(context, "my_gift_of_silence")
        )
    )
}

private fun loadLyricsFromAssets(
    context: Context,
    lrcFileName: String
): List<LyricLine> {
    return try {
        val inputStream = context.assets.open("lyrics/$lrcFileName.lrc")
        val reader = BufferedReader(InputStreamReader(inputStream))

        val lyricsString = reader.use { it.readText() }

        parseLrc(lyricsString)
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

private fun parseLrc(
    lyricsString: String
): List<LyricLine> {
    return lyricsString.lines().mapNotNull { line ->
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2})](.*)")
        val matchResult = regex.find(line)
        matchResult?.let {
            val minutes = it.groupValues[1].toLong()
            val seconds = it.groupValues[2].toLong()
            val hundredths = it.groupValues[3].toLong()
            val text = it.groupValues[4].trim()

            val timestamp = (minutes * 60 * 1000) + (seconds * 1000) + (hundredths * 10)

            LyricLine(timestamp, text)
        }
    }.sortedBy { it.timestamp }
}

fun formatTime(ms: Long): String {
    val safeMs = if (ms == C.TIME_UNSET) 0L else ms
    val totalSeconds = safeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}