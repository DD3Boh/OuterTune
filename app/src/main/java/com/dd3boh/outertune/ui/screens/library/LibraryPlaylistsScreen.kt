package com.dd3boh.outertune.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.CONTENT_TYPE_HEADER
import com.dd3boh.outertune.constants.CONTENT_TYPE_PLAYLIST
import com.dd3boh.outertune.constants.GridThumbnailHeight
import com.dd3boh.outertune.constants.LibraryViewType
import com.dd3boh.outertune.constants.LibraryViewTypeKey
import com.dd3boh.outertune.constants.PlaylistSortDescendingKey
import com.dd3boh.outertune.constants.PlaylistSortType
import com.dd3boh.outertune.constants.PlaylistSortTypeKey
import com.dd3boh.outertune.constants.PlaylistViewTypeKey
import com.dd3boh.outertune.db.entities.PlaylistEntity
import com.dd3boh.outertune.ui.component.AutoPlaylistGridItem
import com.dd3boh.outertune.ui.component.AutoPlaylistListItem
import com.dd3boh.outertune.ui.component.HideOnScrollFAB
import com.dd3boh.outertune.ui.component.LibraryPlaylistGridItem
import com.dd3boh.outertune.ui.component.LibraryPlaylistListItem
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.SortHeader
import com.dd3boh.outertune.ui.component.TextFieldDialog
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.viewmodels.LibraryPlaylistsViewModel
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import java.time.LocalDateTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
    libraryFilterContent: @Composable() (() -> Unit)? = null,
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current

    val coroutineScope = rememberCoroutineScope()

    var viewTypeLocal by rememberEnumPreference(PlaylistViewTypeKey, LibraryViewType.GRID)
    val libraryViewType by rememberEnumPreference(LibraryViewTypeKey, LibraryViewType.GRID)

    val viewType = if (libraryFilterContent != null) libraryViewType else viewTypeLocal

    val (sortType, onSortTypeChange) = rememberEnumPreference(PlaylistSortTypeKey, PlaylistSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(PlaylistSortDescendingKey, true)

    LaunchedEffect(Unit) { viewModel.sync() }

    val playlists by viewModel.allPlaylists.collectAsState()

    val likedPlaylist = PlaylistEntity(id = "liked", name = stringResource(id = R.string.liked_songs))
    val downloadedPlaylist = PlaylistEntity(id = "downloaded", name = stringResource(id = R.string.downloaded_songs))

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()

    var showAddPlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var syncedPlaylist: Boolean by remember {
        mutableStateOf(false)
    }

    if (showAddPlaylistDialog) {
        TextFieldDialog(
            icon = { Icon(imageVector = Icons.Rounded.Add, contentDescription = null) },
            title = { Text(text = stringResource(R.string.create_playlist)) },
            onDismiss = { showAddPlaylistDialog = false },
            onDone = { playlistName ->
                viewModel.viewModelScope.launch(Dispatchers.IO) {
                    val browseId = if (syncedPlaylist)
                        YouTube.createPlaylist(playlistName).getOrNull()
                    else null

                    database.query {
                        insert(
                            PlaylistEntity(
                                name = playlistName,
                                browseId = browseId,
                                bookmarkedAt = LocalDateTime.now()
                            )
                        )
                    }
                }
            },
            extraContent = {
                // synced/unsynced toggle
                Row(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 40.dp)
                ) {
                    Column() {
                        Text(
                            text = "Sync Playlist",
                            style = MaterialTheme.typography.titleLarge,
                        )

                        Text(
                            text = "Note: This allows for syncing with YouTube Music. This is NOT changeable later. You cannot add local songs to synced playlists.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(0.7f)
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Switch(
                            checked = syncedPlaylist,
                            onCheckedChange = {
                                syncedPlaylist = !syncedPlaylist
                            },
                        )
                    }
                }

            }
        )
    }

    val headerContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp)
        ) {
            SortHeader(
                sortType = sortType,
                sortDescending = sortDescending,
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                sortTypeText = { sortType ->
                    when (sortType) {
                        PlaylistSortType.CREATE_DATE -> R.string.sort_by_create_date
                        PlaylistSortType.NAME -> R.string.sort_by_name
                        PlaylistSortType.SONG_COUNT -> R.string.sort_by_song_count
                    }
                }
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = pluralStringResource(R.plurals.n_playlist, playlists.size, playlists.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary
            )

            if (libraryFilterContent == null) {
                IconButton(
                    onClick = {
                        viewTypeLocal = viewTypeLocal.toggle()
                    },
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp)
                ) {
                    Icon(
                        imageVector =
                        when (viewTypeLocal) {
                            LibraryViewType.LIST -> Icons.AutoMirrored.Rounded.List
                            LibraryViewType.GRID -> Icons.Rounded.GridView
                        },
                        contentDescription = null
                    )
                }
            } else {
                Spacer(Modifier.size(16.dp))
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when (viewType) {
            LibraryViewType.LIST -> {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                ) {
                    item(
                        key = "filter",
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        libraryFilterContent?.let { it() }
                    }

                    item(
                        key = "header",
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        headerContent()
                    }

                    if (libraryFilterContent != null) {
                        item(
                            key = likedPlaylist.id,
                            contentType = { CONTENT_TYPE_PLAYLIST }
                        ) {
                            AutoPlaylistListItem(
                                playlist = likedPlaylist,
                                thumbnail = Icons.Rounded.Favorite,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navController.navigate("auto_playlist/${likedPlaylist.id}")
                                    }
                                    .animateItemPlacement()
                            )
                        }

                        item(
                            key = downloadedPlaylist.id,
                            contentType = { CONTENT_TYPE_PLAYLIST }
                        ) {
                            AutoPlaylistListItem(
                                playlist = downloadedPlaylist,
                                thumbnail = Icons.Rounded.CloudDownload,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navController.navigate("auto_playlist/${downloadedPlaylist.id}")
                                    }
                                    .animateItemPlacement()
                            )
                        }
                    }

                    items(
                        items = playlists,
                        key = { it.id },
                        contentType = { CONTENT_TYPE_PLAYLIST }
                    ) { playlist ->
                        LibraryPlaylistListItem(
                            navController = navController,
                            menuState = menuState,
                            coroutineScope = coroutineScope,
                            playlist = playlist,
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }

                HideOnScrollFAB(
                    lazyListState = lazyListState,
                    icon = Icons.Rounded.Add,
                    onClick = {
                        showAddPlaylistDialog = true
                    }
                )
            }

            LibraryViewType.GRID -> {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                ) {
                    item(
                        key = "filter",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        libraryFilterContent?.let { it() }
                    }

                    item(
                        key = "header",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        headerContent()
                    }

                    if (libraryFilterContent != null) {
                        item(
                            key = likedPlaylist.id,
                            contentType = { CONTENT_TYPE_PLAYLIST }
                        ) {
                            AutoPlaylistGridItem(
                                playlist = likedPlaylist,
                                thumbnail = Icons.Rounded.Favorite,
                                fillMaxWidth = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navController.navigate("auto_playlist/${likedPlaylist.id}")
                                    }
                                    .animateItemPlacement()
                            )
                        }

                        item(
                            key = downloadedPlaylist.id,
                            contentType = { CONTENT_TYPE_PLAYLIST }
                        ) {
                            AutoPlaylistGridItem(
                                playlist = downloadedPlaylist,
                                thumbnail = Icons.Rounded.CloudDownload,
                                fillMaxWidth = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navController.navigate("auto_playlist/${downloadedPlaylist.id}")
                                    }
                                    .animateItemPlacement()
                            )
                        }
                    }

                    items(
                        items = playlists,
                        key = { it.id },
                        contentType = { CONTENT_TYPE_PLAYLIST }
                    ) { playlist ->
                        LibraryPlaylistGridItem(
                            navController = navController,
                            menuState = menuState,
                            coroutineScope = coroutineScope,
                            playlist = playlist,
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }

                HideOnScrollFAB(
                    lazyListState = lazyGridState,
                    icon = Icons.Rounded.Add,
                    onClick = {
                        showAddPlaylistDialog = true
                    }
                )
            }
        }

    }
}
