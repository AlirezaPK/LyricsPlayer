package com.lyricsplayer

import android.app.PendingIntent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

// A foreground service that owns the actual ExoPlayer instance.
// This service hosts a [MediaSession], which wraps the ExoPlayer.
// The session is what the system (notification, lock screen, Bluetooth, Android Auto) talks to.
// The [PlayerScreen] connects to this session via a [MediaController] to send commands and observe state.

// Created when the first controller connects (or when startForegroundService is called).
// Destroyed when the user swipes away the notification OR the app is force-stopped.
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // Build an ExoPlayer configured for music playback.
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                // `true` handle audio focus automatically.
                // This means the player will pause when another app (e.g., a phone call or YouTube) requests audio focus, and resume when it's done.
                true
            )
            // Pause playback when headphones are unplugged.
            .setHandleAudioBecomingNoisy(true)
            .build()

        // When the user taps the media notification, this PendingIntent opens the app's main activity. This is what makes the notification "clickable".
        val sessionActivityPendingIntent = packageManager?.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        // Build the MediaSession. This is the bridge between the ExoPlayer (which actually plays audio) and the outside world (notification, lock screen, controllers)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent!!)
            .build()
    }

    // Called by the system to ask "do you have an active session?"
    // Returning the session here is what enables the notification to appear.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // Clean up when the service is destroyed.
    // IMPORTANT: We release BOTH the session AND the player.
    // Forgetting to release the player causes memory leaks and audio ghosts.
    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}