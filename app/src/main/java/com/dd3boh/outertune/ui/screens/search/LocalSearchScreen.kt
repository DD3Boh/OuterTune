package com.dd3boh.outertune.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.CONTENT_TYPE_LIST
import com.dd3boh.outertune.constants.ListItemHeight
import com.dd3boh.outertune.db.entities.Album
import com.dd3boh.outertune.db.entities.Artist
import com.dd3boh.outertune.db.entities.Playlist
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.extensions.togglePlayPause
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.ui.component.AlbumListItem
import com.dd3boh.outertune.ui.component.ArtistListItem
import com.dd3boh.outertune.ui.component.ChipsRow
import com.dd3boh.outertune.ui.component.EmptyPlaceholder
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.PlaylistListItem
import com.dd3boh.outertune.ui.component.SongListItem
import com.dd3boh.outertune.ui.component.SwipeToQueueBox
import com.dd3boh.outertune.ui.menu.SongMenu
import com.dd3boh.outertune.viewmodels.LocalFilter
import com.dd3boh.outertune.viewmodels.LocalSearchViewModel
import kotlinx.coroutines.flow.drop

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun LocalSearchScreen(
    query: String,
    navController: NavController,
    onDismiss: () -> Unit,
    viewModel: LocalSearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val searchFilter by viewModel.filter.collectAsState()
    val result by viewModel.result.collectAsState()

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(query) {
        viewModel.query.value = query
    }

    Column {
        ChipsRow(
            chips = listOf(
                LocalFilter.ALL to stringResource(R.string.filter_all),
                LocalFilter.SONG to stringResource(R.string.filter_songs),
                LocalFilter.ALBUM to stringResource(R.string.filter_albums),
                LocalFilter.ARTIST to stringResource(R.string.filter_artists),
                LocalFilter.PLAYLIST to stringResource(R.string.filter_playlists)
            ),
            currentValue = searchFilter,
            onValueUpdate = { viewModel.filter.value = it }
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.weight(1f)
        ) {
            result.map.forEach { (filter, items) ->
                if (result.filter == LocalFilter.ALL) {
                    item(
                        key = filter
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ListItemHeight)
                                .clickable { viewModel.filter.value = filter }
                                .padding(start = 12.dp, end = 18.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    when (filter) {
                                        LocalFilter.SONG -> R.string.filter_songs
                                        LocalFilter.ALBUM -> R.string.filter_albums
                                        LocalFilter.ARTIST -> R.string.filter_artists
                                        LocalFilter.PLAYLIST -> R.string.filter_playlists
                                        LocalFilter.ALL -> error("")
                                    }
                                ),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f)
                            )

                            Icon(
                                Icons.AutoMirrored.Rounded.NavigateNext,
                                contentDescription = null
                            )
                        }
                    }
                }

                items(
                    items = items,
                    key = { it.id },
                    contentType = { CONTENT_TYPE_LIST }
                ) { item ->
                    when (item) {
                        is Song -> SwipeToQueueBox(
                            item = item.toMediaItem(),
                            content = {
                                SongListItem(
                                    song = item,
                                    isActive = item.id == mediaMetadata?.id,
                                    isPlaying = isPlaying,
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = item,
                                                        navController = navController
                                                    ) {
                                                        onDismiss()
                                                        menuState.dismiss()
                                                    }
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
                                        .clickable {
                                            if (item.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                val songs = result.map
                                                    .getOrDefault(LocalFilter.SONG, emptyList())
                                                    .filterIsInstance<Song>()
                                                    .map { it.toMediaItem() }
                                                playerConnection.playQueue(ListQueue(
                                                    title = context.getString(R.string.queue_searched_songs),
                                                    items = songs,
                                                    startIndex = songs.indexOfFirst { it.mediaId == item.id }
                                                ))
                                            }
                                        }
                                        .animateItemPlacement()
                                )
                            },
                            snackbarHostState = snackbarHostState
                        )

                        is Album -> AlbumListItem(
                            album = item,
                            isActive = item.id == mediaMetadata?.album?.id,
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .clickable {
                                    onDismiss()
                                    navController.navigate("album/${item.id}")
                                }
                                .animateItemPlacement()
                        )

                        is Artist -> ArtistListItem(
                            artist = item,
                            modifier = Modifier
                                .clickable {
                                    onDismiss()
                                    navController.navigate("artist/${item.id}")
                                }
                                .animateItemPlacement()
                        )

                        is Playlist -> PlaylistListItem(
                            playlist = item,
                            modifier = Modifier
                                .clickable {
                                    onDismiss()
                                    navController.navigate("local_playlist/${item.id}")
                                }
                                .animateItemPlacement()
                        )
                    }
                }
            }

            if (result.query.isNotEmpty() && result.map.isEmpty()) {
                item(
                    key = "no_result"
                ) {
                    EmptyPlaceholder(
                        icon = Icons.Rounded.Search,
                        text = stringResource(R.string.no_results_found)
                    )
                }
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
    )
}