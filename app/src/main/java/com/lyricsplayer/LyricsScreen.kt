package com.lyricsplayer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lyricsplayer.ui.theme.LyricsPlayerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    modifier: Modifier = Modifier,
    lyrics: List<LyricLine>,
    currentPosition: Long,
    onSeek: (Long) -> Unit,
    onBackClick: () -> Unit
) {
    val listState = rememberLazyListState()

    // Find the index of the lyric that should be active right now.
    // `indexOfLast` gives us the most recent lyric whose timestamp has passed.
    val activeLyricIndex = remember(currentPosition, lyrics) {
        lyrics.indexOfLast { it.timestamp <= currentPosition }
    }

    // Smoothly scroll to the active lyric whenever it changes.
    LaunchedEffect(activeLyricIndex) {
        if (activeLyricIndex != -1) {
            listState.animateScrollToItem(
                index = activeLyricIndex,
                // The negative offset keeps the active line slightly above center.
                scrollOffset = -150
            )
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                            contentDescription = "back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Surface(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(32.dp),
                // Vertical padding lets the first and last lyrics scroll to the highlighted position (otherwise they'd be stuck at the edges).
                contentPadding = PaddingValues(vertical = 150.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                itemsIndexed(lyrics) { index, lyric ->
                    val isActive = index == activeLyricIndex

                    // Smooth alpha transition between active (1.0) and inactive (0.4)
                    val alpha by animateFloatAsState(
                        targetValue = if (isActive) 1f else 0.4f,
                        animationSpec = tween(durationMillis = 300),
                        label = "lyric_alpha"
                    )

                    Text(
                        text = lyric.text,
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = alpha)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            // Tapping a lyric seeks the player to that timestamp
                            .clickable {
                                onSeek(lyric.timestamp)
                            }
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun LyricsScreenPreview() {
    LyricsPlayerTheme {
        val context = LocalContext.current
        val song = getSongs(context)[0]

        LyricsScreen(
            lyrics = song.lyrics,
            currentPosition = 1,
            onSeek = {},
            onBackClick = {}
        )
    }
}