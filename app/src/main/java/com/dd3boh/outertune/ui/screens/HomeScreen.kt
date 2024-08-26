package com.dd3boh.outertune.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.InnerTubeCookieKey
import com.dd3boh.outertune.constants.ListItemHeight
import com.dd3boh.outertune.extensions.togglePlayPause
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.queues.YouTubeAlbumRadio
import com.dd3boh.outertune.playback.queues.YouTubeQueue
import com.dd3boh.outertune.ui.component.HideOnScrollFAB
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.NavigationTile
import com.dd3boh.outertune.ui.component.NavigationTitle
import com.dd3boh.outertune.ui.component.SongListItem
import com.dd3boh.outertune.ui.component.YouTubeCardItem
import com.dd3boh.outertune.ui.component.YouTubeGridItem
import com.dd3boh.outertune.ui.menu.SongMenu
import com.dd3boh.outertune.ui.menu.YouTubeAlbumMenu
import com.dd3boh.outertune.ui.utils.SnapLayoutInfoProvider
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.viewmodels.HomeViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.WatchEndpoint
import com.zionhuang.innertube.utils.parseCookieString
import kotlin.random.Random

@Suppress("DEPRECATION")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queuePlaylistId by playerConnection.queuePlaylistId.collectAsState()

    val quickPicks by viewModel.quickPicks.collectAsState()
    val explorePage by viewModel.explorePage.collectAsState()
    val recentActivity by viewModel.recentActivity.collectAsState()
    val recentPlaylistsDb by viewModel.recentPlaylistsDb.collectAsState()

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val mostPlayedLazyGridState = rememberLazyGridState()
    val recentActivityGridState = rememberLazyGridState()

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            scrollState.animateScrollTo(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    SwipeRefresh(
        state = rememberSwipeRefreshState(isRefreshing),
        onRefresh = viewModel::refresh,
        indicatorPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
            val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor
            val snapLayoutInfoProvider = remember(mostPlayedLazyGridState) {
                SnapLayoutInfoProvider(
                    lazyGridState = mostPlayedLazyGridState,
                )
            }

            Column(
                modifier = Modifier.verticalScroll(scrollState)
            ) {
                Spacer(Modifier.height(LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateTopPadding()))

                Row(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .fillMaxWidth()
                ) {
                    NavigationTile(
                        title = stringResource(R.string.history),
                        icon = Icons.Rounded.History,
                        onClick = { navController.navigate("history") },
                        modifier = Modifier.weight(1f)
                    )

                    NavigationTile(
                        title = stringResource(R.string.stats),
                        icon = Icons.AutoMirrored.Rounded.TrendingUp,
                        onClick = { navController.navigate("stats") },
                        modifier = Modifier.weight(1f)
                    )

                    if (isLoggedIn) {
                        NavigationTile(
                            title = stringResource(R.string.account),
                            icon = Icons.Rounded.Person,
                            onClick = {
                                navController.navigate("account")
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (isLoggedIn && !recentActivity.isNullOrEmpty()) {
                    NavigationTitle(
                        title = stringResource(R.string.recent_activity)
                    )

                    LazyHorizontalGrid(
                        state = recentActivityGridState,
                        rows = GridCells.Fixed(4),
                        flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp * 4)
                    ) {
                        items(
                            items = recentActivity!!,
                            key = { it.id }
                        ) { item ->
                            YouTubeCardItem(
                                item,
                                onClick = {
                                    when (item) {
                                        is PlaylistItem -> {
                                            val playlistDb = recentPlaylistsDb
                                                ?.firstOrNull { it.playlist.browseId == item.id }

                                            println(recentPlaylistsDb)

                                            if (playlistDb != null && playlistDb.songCount != 0)
                                                navController.navigate("local_playlist/${playlistDb.id}")
                                            else
                                                navController.navigate("online_playlist/${item.id}")
                                        }

                                        is AlbumItem -> navController.navigate("album/${item.id}")

                                        is ArtistItem -> navController.navigate("artist/${item.id}")

                                        else -> {}
                                    }
                                },
                                isPlaying = isPlaying,
                                isActive = when (item) {
                                    is PlaylistItem -> queuePlaylistId == item.id
                                    is AlbumItem -> queuePlaylistId == item.playlistId
                                    is ArtistItem -> (queuePlaylistId == item.radioEndpoint?.playlistId ||
                                                    queuePlaylistId == item.shuffleEndpoint?.playlistId ||
                                                    queuePlaylistId == item.playEndpoint?.playlistId)
                                    else -> false
                                },
                            )
                        }
                    }
                }

                NavigationTitle(
                    title = stringResource(R.string.quick_picks)
                )

                quickPicks?.let { quickPicks ->
                    if (quickPicks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ListItemHeight * 4)
                        ) {
                            Text(
                                text = stringResource(R.string.quick_picks_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    } else {
                        LazyHorizontalGrid(
                            state = mostPlayedLazyGridState,
                            rows = GridCells.Fixed(4),
                            flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                            contentPadding = WindowInsets.systemBars
                                .only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ListItemHeight * 4)
                        ) {
                            items(
                                items = quickPicks,
                                key = { it.id }
                            ) { originalSong ->
                                val song by database.song(originalSong.id).collectAsState(initial = originalSong)

                                SongListItem(
                                    song = song!!,
                                    showInLibraryIcon = true,
                                    isActive = song!!.id == mediaMetadata?.id,
                                    isPlaying = isPlaying,
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = song!!,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss
                                                    )
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Rounded.MoreVert,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .width(horizontalLazyGridItemWidth)
                                        .combinedClickable(
                                            onClick = {
                                                if (song!!.id == mediaMetadata?.id) {
                                                    playerConnection.player.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        YouTubeQueue(
                                                            WatchEndpoint(videoId = song!!.id),
                                                            song!!.toMediaMetadata()
                                                        )
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = song!!,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss
                                                    )
                                                }
                                            }
                                        )
                                )
                            }
                        }
                    }
                }

                explorePage?.newReleaseAlbums?.let { newReleaseAlbums ->
                    NavigationTitle(
                        title = stringResource(R.string.new_release_albums),
                        onClick = {
                            navController.navigate("new_release")
                        }
                    )

                    LazyRow(
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues()
                    ) {
                        items(
                            items = newReleaseAlbums,
                            key = { it.id }
                        ) { album ->
                            YouTubeGridItem(
                                item = album,
                                isActive = mediaMetadata?.album?.id == album.id,
                                isPlaying = isPlaying,
                                coroutineScope = coroutineScope,
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("album/${album.id}")
                                        },
                                        onLongClick = {
                                            menuState.show {
                                                YouTubeAlbumMenu(
                                                    albumItem = album,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss
                                                )
                                            }
                                        }
                                    )
                                    .animateItemPlacement()
                            )
                        }
                    }
                }

                explorePage?.moodAndGenres?.let { moodAndGenres ->
                    NavigationTitle(
                        title = stringResource(R.string.mood_and_genres),
                        onClick = {
                            navController.navigate("mood_and_genres")
                        }
                    )

                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(4),
                        contentPadding = PaddingValues(6.dp),
                        modifier = Modifier.height((MoodAndGenresButtonHeight + 12.dp) * 4 + 12.dp)
                    ) {
                        items(moodAndGenres) {
                            MoodAndGenresButton(
                                title = it.title,
                                onClick = {
                                    navController.navigate("youtube_browse/${it.endpoint.browseId}?params=${it.endpoint.params}")
                                },
                                modifier = Modifier
                                    .padding(6.dp)
                                    .width(180.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()))
            }

            HideOnScrollFAB(
                visible = !quickPicks.isNullOrEmpty() || explorePage?.newReleaseAlbums?.isNotEmpty() == true,
                scrollState = scrollState,
                icon = Icons.Rounded.Casino,
                onClick = {
                    if (Random.nextBoolean() && !quickPicks.isNullOrEmpty()) {
                        val song = quickPicks!!.random()
                        playerConnection.playQueue(YouTubeQueue(WatchEndpoint(videoId = song.id), song.toMediaMetadata()))
                    } else if (explorePage?.newReleaseAlbums?.isNotEmpty() == true) {
                        val album = explorePage?.newReleaseAlbums!!.random()
                        playerConnection.playQueue(YouTubeAlbumRadio(album.playlistId))
                    }
                }
            )
        }
    }
}
