package com.dd3boh.outertune.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.CONTENT_TYPE_HEADER
import com.dd3boh.outertune.constants.CONTENT_TYPE_SONG
import com.dd3boh.outertune.constants.SongFilter
import com.dd3boh.outertune.constants.SongFilterKey
import com.dd3boh.outertune.constants.SongSortDescendingKey
import com.dd3boh.outertune.constants.SongSortType
import com.dd3boh.outertune.constants.SongSortTypeKey
import com.dd3boh.outertune.constants.YtmSyncKey
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.extensions.togglePlayPause
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.ui.component.ChipsRow
import com.dd3boh.outertune.ui.component.HideOnScrollFAB
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.SelectHeader
import com.dd3boh.outertune.ui.component.SongListItem
import com.dd3boh.outertune.ui.component.SortHeader
import com.dd3boh.outertune.ui.component.SwipeToQueueBox
import com.dd3boh.outertune.ui.menu.SongMenu
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.viewmodels.LibrarySongsViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibrarySongsScreen(
    navController: NavController,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
    libraryFilterContent: @Composable() (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var filter by rememberEnumPreference(SongFilterKey, SongFilter.LIKED)
    libraryFilterContent?.let { filter = SongFilter.LIBRARY }

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    val songs by viewModel.allSongs.collectAsState()
    val isSyncingRemoteLikedSongs by viewModel.isSyncingRemoteLikedSongs.collectAsState()
    val isSyncingRemoteSongs by viewModel.isSyncingRemoteSongs.collectAsState()

    val lazyListState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    // multiselect
    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    LaunchedEffect(inSelectMode) {
        backStackEntry?.savedStateHandle?.set("inSelectMode", inSelectMode)
    }

    LaunchedEffect(songs) {
        selection.fastForEachReversed { songId ->
            if (songs.find { it.id == songId } == null) {
                selection.remove(songId)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (ytmSync) {
            when (filter) {
                SongFilter.LIKED -> viewModel.syncLikedSongs()
                SongFilter.LIBRARY -> viewModel.syncLibrarySongs()
                else -> return@LaunchedEffect
            }
        }
    }

    val filterContent = @Composable {
        ChipsRow(
            chips = listOf(
                SongFilter.LIKED to stringResource(R.string.filter_liked),
                SongFilter.LIBRARY to stringResource(R.string.filter_library),
                SongFilter.DOWNLOADED to stringResource(R.string.filter_downloaded)
            ),
            currentValue = filter,
            onValueUpdate = {
                filter = it
                if (ytmSync) {
                    if (it == SongFilter.LIKED) viewModel.syncLikedSongs()
                    else if (it == SongFilter.LIBRARY) viewModel.syncLibrarySongs()
                }
            },
            isLoading = { filter ->
                (filter == SongFilter.LIKED && isSyncingRemoteLikedSongs) || (filter == SongFilter.LIBRARY && isSyncingRemoteSongs)
            }
        )
    }

    val headerContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            if (inSelectMode) {
                SelectHeader(
                    selectedItems = selection.mapNotNull { songId ->
                        songs.find { it.id == songId }
                    }.map { it.toMediaMetadata()},
                    totalItemCount = songs.size,
                    onSelectAll = {
                        selection.clear()
                        selection.addAll(songs.map { it.id })
                    },
                    onDeselectAll = { selection.clear() },
                    menuState = menuState,
                    onDismiss = onExitSelectionMode
                )
            } else {
                SortHeader(
                    sortType = sortType,
                    sortDescending = sortDescending,
                    onSortTypeChange = onSortTypeChange,
                    onSortDescendingChange = onSortDescendingChange,
                    sortTypeText = { sortType ->
                        when (sortType) {
                            SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                            SongSortType.MODIFIED_DATE -> R.string.sort_by_date_modified
                            SongSortType.RELEASE_DATE -> R.string.sort_by_date_released
                            SongSortType.NAME -> R.string.sort_by_name
                            SongSortType.ARTIST -> R.string.sort_by_artist
                            SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                        }
                    }
                )

                Spacer(Modifier.weight(1f))

                Text(
                    text = pluralStringResource(R.plurals.n_song, songs.size, songs.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
            item(
                key = "header",
                contentType = CONTENT_TYPE_HEADER
            ) {
                Column(
                    modifier = Modifier.background(MaterialTheme.colorScheme.background)
                ) {
                    libraryFilterContent?.let { it() } ?: filterContent()
                    headerContent()
                }
            }

            itemsIndexed(
                items = songs,
                key = { _, item -> item.id },
                contentType = { _, _ -> CONTENT_TYPE_SONG }
            ) { index, song ->
                val onCheckedChange: (Boolean) -> Unit = {
                    if (it) {
                        selection.add(song.id)
                    } else {
                        selection.remove(song.id)
                    }
                }

                SwipeToQueueBox(
                    item = song.toMediaItem(),
                    content = {
                        SongListItem(
                            song = song,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            trailingContent = {
                                if (inSelectMode) {
                                    Checkbox(
                                        checked = song.id in selection,
                                        onCheckedChange = onCheckedChange
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song,
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
                                }
                            },
                            isSelected = inSelectMode && song.id in selection,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (inSelectMode) {
                                            onCheckedChange(song.id !in selection)
                                        } else if (song.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = context.getString(R.string.queue_all_songs),
                                                    items = songs.map { it.toMediaMetadata() },
                                                    startIndex = index
                                                )
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        if (!inSelectMode) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            inSelectMode = true
                                            onCheckedChange(true)
                                        }
                                    }
                                )
                                .animateItemPlacement()
                        )
                    },
                    snackbarHostState = snackbarHostState
                )
            }
        }

        HideOnScrollFAB(
            visible = songs.isNotEmpty(),
            lazyListState = lazyListState,
            icon = Icons.Rounded.Shuffle,
            onClick = {
                playerConnection.playQueue(
                    ListQueue(
                        title = context.getString(R.string.queue_all_songs),
                        items = songs.shuffled().map { it.toMediaMetadata()}
                    )
                )
            }
        )


        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .align(Alignment.BottomCenter)
        )
    }
}
