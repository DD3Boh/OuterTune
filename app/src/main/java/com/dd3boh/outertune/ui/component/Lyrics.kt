package com.dd3boh.outertune.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.DarkModeKey
import com.dd3boh.outertune.constants.LyricTrimKey
import com.dd3boh.outertune.constants.LyricsTextPositionKey
import com.dd3boh.outertune.constants.MultilineLrcKey
import com.dd3boh.outertune.constants.PlayerBackgroundStyleKey
import com.dd3boh.outertune.constants.ShowLyricsKey
import com.dd3boh.outertune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.dd3boh.outertune.lyrics.LyricsEntry
import com.dd3boh.outertune.lyrics.LyricsEntry.Companion.HEAD_LYRICS_ENTRY
import com.dd3boh.outertune.lyrics.LyricsUtils.findCurrentLineIndex
import com.dd3boh.outertune.lyrics.LyricsUtils.parseLyrics
import com.dd3boh.outertune.ui.component.shimmer.ShimmerHost
import com.dd3boh.outertune.ui.component.shimmer.TextPlaceholder
import com.dd3boh.outertune.ui.menu.LyricsMenu
import com.dd3boh.outertune.ui.screens.settings.DarkMode
import com.dd3boh.outertune.ui.screens.settings.LyricsPosition
import com.dd3boh.outertune.ui.screens.settings.PlayerBackgroundStyle
import com.dd3boh.outertune.ui.utils.fadingEdge
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds

@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val density = LocalDensity.current
    var showLyrics by rememberPreference(ShowLyricsKey, false)
    val landscapeOffset = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val lyricsTextPosition by rememberEnumPreference(LyricsTextPositionKey, LyricsPosition.CENTER)

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val lyrics = remember(lyricsEntity) { lyricsEntity?.lyrics?.trim() }
    val multilineLrc = rememberPreference(MultilineLrcKey, defaultValue = true)
    val lyricTrim = rememberPreference(LyricTrimKey, defaultValue = false)

    val playerBackground by rememberEnumPreference(key = PlayerBackgroundStyleKey, defaultValue = PlayerBackgroundStyle.DEFAULT)

    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }

    val lines = remember(lyrics) {
        if (lyrics == null || lyrics == LYRICS_NOT_FOUND) emptyList()
        else if (lyrics.startsWith("[")) listOf(HEAD_LYRICS_ENTRY) +
                parseLyrics(lyrics, lyricTrim.value, multilineLrc.value)
        else lyrics.lines().mapIndexed { index, line -> LyricsEntry(index * 100L, line) }
    }
    val isSynced = remember(lyrics) {
        !lyrics.isNullOrEmpty() && lyrics.startsWith("[")
    }

    val textColor = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.secondary
        else ->
            if (useDarkTheme)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onPrimary
    }

    var currentLineIndex by remember {
        mutableIntStateOf(-1)
    }
    // Because LaunchedEffect has delay, which leads to inconsistent with current line color and scroll animation,
    // we use deferredCurrentLineIndex when user is scrolling
    var deferredCurrentLineIndex by rememberSaveable {
        mutableIntStateOf(0)
    }

    var lastPreviewTime by rememberSaveable {
        mutableLongStateOf(0L)
    }
    var isSeeking by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(lyrics) {
        if (lyrics.isNullOrEmpty() || !lyrics.startsWith("[")) {
            currentLineIndex = -1
            return@LaunchedEffect
        }
        while (isActive) {
            delay(50)
            val sliderPosition = sliderPositionProvider()
            isSeeking = sliderPosition != null
            currentLineIndex = findCurrentLineIndex(lines, sliderPosition ?: playerConnection.player.currentPosition)
        }
    }

    LaunchedEffect(isSeeking, lastPreviewTime) {
        if (isSeeking) {
            lastPreviewTime = 0L
        } else if (lastPreviewTime != 0L) {
            delay(LyricsPreviewTime)
            lastPreviewTime = 0L
        }
    }

    val lazyListState = rememberLazyListState()

    LaunchedEffect(currentLineIndex, lastPreviewTime) {
        /**
         * Count number of new lines in a lyric
         */
        fun countNewLine(str: String) = str.count { it == '\n' }

        /**
         * Calculate the lyric offset Based on how many lines (\n chars)
         */
        fun calculateOffset() = with(density) {
            if (landscapeOffset) {
                16.dp.toPx().toInt() * countNewLine(lines[currentLineIndex].text) // landscape sits higher by default
            } else {
                20.dp.toPx().toInt() * countNewLine(lines[currentLineIndex].text)
            }
        }

        if (!isSynced) return@LaunchedEffect
        if (currentLineIndex != -1) {
            deferredCurrentLineIndex = currentLineIndex
            if (lastPreviewTime == 0L) {
                if (isSeeking) {
                    lazyListState.scrollToItem(currentLineIndex,
                        with(density) { 36.dp.toPx().toInt() } + calculateOffset())
                } else {
                    lazyListState.animateScrollToItem(currentLineIndex,
                        with(density) { 36.dp.toPx().toInt() } + calculateOffset())
                }
            }
        }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 12.dp)
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = WindowInsets.systemBars
                .only(WindowInsetsSides.Top)
                .add(WindowInsets(top = maxHeight / 2, bottom = maxHeight / 2))
                .asPaddingValues(),
            modifier = Modifier
                .fadingEdge(vertical = 64.dp)
                .nestedScroll(remember {
                    object : NestedScrollConnection {
                        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                            lastPreviewTime = System.currentTimeMillis()
                            return super.onPostScroll(consumed, available, source)
                        }

                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                            lastPreviewTime = System.currentTimeMillis()
                            return super.onPostFling(consumed, available)
                        }
                    }
                })
        ) {
            val displayedCurrentLineIndex = if (isSeeking) deferredCurrentLineIndex else currentLineIndex

            if (lyrics == null) {
                item {
                    ShimmerHost {
                        repeat(10) {
                            Box(
                                contentAlignment = when (lyricsTextPosition) {
                                    LyricsPosition.LEFT -> Alignment.CenterStart
                                    LyricsPosition.CENTER -> Alignment.Center
                                    LyricsPosition.RIGHT -> Alignment.CenterEnd
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 4.dp)
                            ) {
                                TextPlaceholder()
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = lines
                ) { index, item ->
                    Text(
                        text = item.text,
                        fontSize = 20.sp,
                        color = textColor,
                        textAlign = when (lyricsTextPosition) {
                            LyricsPosition.LEFT -> TextAlign.Left
                            LyricsPosition.CENTER -> TextAlign.Center
                            LyricsPosition.RIGHT -> TextAlign.Right
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isSynced) {
                                playerConnection.player.seekTo(item.time)
                                lastPreviewTime = 0L
                            }
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .alpha(if (!isSynced || index == displayedCurrentLineIndex) 1f else 0.5f)
                    )
                }
            }
        }

        if (lyrics == LYRICS_NOT_FOUND) {
            Text(
                text = stringResource(R.string.lyrics_not_found),
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = when (lyricsTextPosition) {
                    LyricsPosition.LEFT -> TextAlign.Left
                    LyricsPosition.CENTER -> TextAlign.Center
                    LyricsPosition.RIGHT -> TextAlign.Right
                },
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .alpha(0.5f)
            )
        }

        mediaMetadata?.let { mediaMetadata ->
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp)
            ) {
                IconButton(
                    onClick = { showLyrics = false }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = textColor
                    )
                }
                IconButton(
                    onClick = {
                        menuState.show {
                            LyricsMenu(
                                lyricsProvider = { lyricsEntity },
                                mediaMetadataProvider = { mediaMetadata },
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = null,
                        tint = textColor
                    )
                }
            }
        }
    }
}

const val animateScrollDuration = 300L
val LyricsPreviewTime = 4.seconds
