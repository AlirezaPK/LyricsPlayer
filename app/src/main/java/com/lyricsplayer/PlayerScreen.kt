package com.lyricsplayer

import android.content.ComponentName
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lyricsplayer.ui.theme.LyricsPlayerTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val songs = remember(context) { getSongs(context) }

    var showLyrics by remember { mutableStateOf(false) }

    // They are updated by the MediaController listener (below)
    // so the UI always reflects what the service is actually doing.
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentSongIndex by remember { mutableIntStateOf(0) }

    val song = songs.getOrNull(currentSongIndex) ?: songs.first()

    // We track dragging separately so the slider feels smooth:
    // - While dragging, we use `dragPosition` (local, instant feedback).
    // - Only on release do we tell the controller to `seekTo()`.
    // This prevents audio stuttering from dozens of seeks per second.
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    // Derived values for the Slider composable
    val safePosition = if (currentPosition == C.TIME_UNSET) 0f else currentPosition.toFloat()
    val safeDuration = if (duration == C.TIME_UNSET) 0f else duration.toFloat()
    val sliderRange = 0f..maxOf(1f, safeDuration)
    val displayPositionMs = if (isDragging) dragPosition.toLong() else currentPosition

    // A MediaController is the client-side handle that lets us talk to the MediaSession running inside MusicService.
    // Think of it like a remote control:
    // we press buttons here, and the service actually does the work.
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    // The SessionToken identifies WHICH service we want to connect to.
    // It's like an address: "find me the MusicService in this app".
    val sessionToken = remember {
        SessionToken(
            context,
            ComponentName(
                context,
                PlaybackService::class.java
            )
        )
    }

    // Connection is async. `buildAsync()` returns a ListenableFuture that completes once the controller is connected to the service.
    // If we tried to connect synchronously, it would freeze the main UI thread for a fraction of a second.
    // buildAsync() lets the UI stay smooth while the connection happens in the background.
    val controllerFuture: ListenableFuture<MediaController> = remember {
        MediaController.Builder(context, sessionToken).buildAsync()
    }

    DisposableEffect(controllerFuture) {
        controllerFuture.addListener(
            {
                val controller = controllerFuture.get()
                mediaController = controller

                // Only load the playlist if the service is empty.
                // If the user reopened the app while music was playing, we don't want to reset their current song.
                if (controller.mediaItemCount == 0) {
                    controller.setMediaItems(songs.map { it.mediaItem })
                    controller.prepare()
                }

                // Sync UI state with whatever the service is currently doing.
                currentSongIndex = controller.currentMediaItemIndex
                currentPosition = controller.currentPosition
                duration = controller.duration
                isPlaying = controller.playWhenReady &&
                        controller.playbackState != Player.STATE_ENDED &&
                        controller.playbackState != Player.STATE_IDLE
            },
            MoreExecutors.directExecutor()
        )

        onDispose {
            MediaController.releaseFuture(controllerFuture)
        }
    }

    // Subscribes to player events so the UI updates reactively.
    DisposableEffect(mediaController) {
        val controller = mediaController

        if (controller != null) {
            val listener = object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    isPlaying = playWhenReady &&
                            controller.playbackState != Player.STATE_ENDED &&
                            controller.playbackState != Player.STATE_IDLE
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        duration = controller.duration
                    }
                    isPlaying = controller.playWhenReady &&
                            playbackState != Player.STATE_ENDED &&
                            playbackState != Player.STATE_IDLE
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int
                ) {
                    currentSongIndex = controller.currentMediaItemIndex
                }
            }

            controller.addListener(listener)

            onDispose {
                controller.removeListener(listener)
            }
        } else {
            onDispose { }
        }
    }

    // ExoPlayer doesn't emit events on every millisecond of playback (that would be too noisy), so we poll the position every 250ms to keep the slider and time label moving smoothly.
    LaunchedEffect(isDragging) {
        if (!isDragging) {
            while (true) {
                delay(250.milliseconds)
                currentPosition = mediaController?.currentPosition ?: 0L
                duration = mediaController?.duration ?: 0L
            }
        }
    }

    // Reset drag state when the song changes
    LaunchedEffect(currentSongIndex) {
        isDragging = false
        dragPosition = 0f
    }

    Box(
        modifier = modifier
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(
                            onClick = { }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                                contentDescription = "back"
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.SpaceAround
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AsyncImage(
                        model = song.mediaItem.mediaMetadata.artworkUri,
                        contentDescription = "image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(24.dp))
                    )

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 20.dp)
                    ) {
                        Column {
                            Text(
                                text = song.mediaItem.mediaMetadata.title?.toString()
                                    ?: "Unknown Title",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = song.mediaItem.mediaMetadata.artist?.toString()
                                    ?: "Unknown Artist",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        IconButton(
                            onClick = { },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_favorite_border),
                                contentDescription = "favorite",
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column {
                        Slider(
                            value = if (isDragging) dragPosition else safePosition,
                            onValueChange = { newPos ->
                                isDragging = true
                                dragPosition = newPos
                            },
                            onValueChangeFinished = {
                                isDragging = false
                                val seekPosition = dragPosition.toLong()
                                currentPosition = seekPosition
                                mediaController?.seekTo(seekPosition)
                            },
                            valueRange = sliderRange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        ) {
                            Text(
                                text = formatTime(displayPositionMs),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = formatTime(duration),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_shuffle),
                                contentDescription = "shuffle",
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                mediaController?.seekToPrevious()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_skip_previous),
                                contentDescription = "previous",
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        FilledIconButton(
                            onClick = {
                                if (isPlaying) {
                                    mediaController?.pause()
                                } else {
                                    mediaController?.play()
                                }
                            },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                                ),
                                contentDescription = if (isPlaying) "pause" else "play",
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                mediaController?.seekToNext()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_skip_next),
                                contentDescription = "next",
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(
                            onClick = { },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_repeat),
                                contentDescription = "repeat",
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick = {
                                showLyrics = true
                            },
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_keyboard_arrow_up),
                        contentDescription = "lyrics"
                    )
                    Text(text = "Show Lyrics")
                }
            }
        }

        AnimatedVisibility(
            visible = showLyrics,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            LyricsScreen(
                lyrics = song.lyrics,
                currentPosition = currentPosition,
                onSeek = { position ->
                    mediaController?.seekTo(position)
                },
                onBackClick = {
                    showLyrics = false
                }
            )
        }
    }
}

@Preview
@Composable
private fun PlayerScreenPreview() {
    LyricsPlayerTheme {
        PlayerScreen()
    }
}