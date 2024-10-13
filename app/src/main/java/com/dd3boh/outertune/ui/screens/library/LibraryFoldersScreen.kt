package com.dd3boh.outertune.ui.screens.library

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.CONTENT_TYPE_FOLDER
import com.dd3boh.outertune.constants.CONTENT_TYPE_HEADER
import com.dd3boh.outertune.constants.CONTENT_TYPE_SONG
import com.dd3boh.outertune.constants.FlatSubfoldersKey
import com.dd3boh.outertune.constants.LastLocalScanKey
import com.dd3boh.outertune.constants.SongSortDescendingKey
import com.dd3boh.outertune.constants.SongSortType
import com.dd3boh.outertune.constants.SongSortTypeKey
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.extensions.togglePlayPause
import com.dd3boh.outertune.models.DirectoryTree
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.ui.component.HideOnScrollFAB
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.SelectHeader
import com.dd3boh.outertune.ui.component.SongFolderItem
import com.dd3boh.outertune.ui.component.SongListItem
import com.dd3boh.outertune.ui.component.SortHeader
import com.dd3boh.outertune.ui.component.SwipeToQueueBox
import com.dd3boh.outertune.ui.menu.SongMenu
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.viewmodels.LibrarySongsViewModel
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Stack

@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryFoldersScreen(
    navController: NavController,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
    filterContent: @Composable() (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    /**
     * The top of the stack is the folder that the page will render.
     * Clicking on a folder pushes, while the back button pops.
     */
    val folderStack = remember { Stack<DirectoryTree>() }
    val flatSubfolders by rememberPreference(FlatSubfoldersKey, defaultValue = true)
    val lastLocalScan by rememberPreference(LastLocalScanKey, LocalDateTime.now().atOffset(ZoneOffset.UTC).toEpochSecond())

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)

    val lazyListState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    // destroy old structure when pref changes
    LaunchedEffect(flatSubfolders, lastLocalScan) {
        folderStack.clear()
    }

    // initialize with first directory
    if (folderStack.isEmpty()) {
        println("wtrf reinti")
        viewModel.getLocalSongs(database)

        folderStack.push(
            if (flatSubfolders) viewModel.localSongDirectoryTree.value.toFlattenedTree()
            else viewModel.localSongDirectoryTree.value
        )
    }

    // content to load for this page
    var currDir by remember {
        // hello mikooo from the fture, this is mikooo from the past warning you to not touch this.
        // mikooo, you clearly are just going to waste time trying to put this in the in the viewmodel
        // If anyone else would like to try, be my guest
        mutableStateOf(folderStack.peek())
    }

    val mutableSongs = remember {
        mutableStateListOf<Song>()
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

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(sortType, sortDescending, currDir) {
        val tempList = currDir.files.map { it }.toMutableList()
        // sort songs
        tempList.sortBy {
            when (sortType) {
                SongSortType.CREATE_DATE -> it.song.inLibrary?.toEpochSecond(ZoneOffset.UTC).toString()
                SongSortType.MODIFIED_DATE -> it.song.getDateModifiedLong().toString()
                SongSortType.RELEASE_DATE -> it.song.getDateLong().toString()
                SongSortType.NAME -> it.song.title.lowercase()
                SongSortType.ARTIST -> it.artists.joinToString { artist -> artist.name }.lowercase()
                SongSortType.PLAY_TIME -> it.song.totalPlayTime.toString()
            }
        }
        // sort folders
        currDir.subdirs.sortBy { it.currentDir.lowercase() } // only sort by name

        if (sortDescending) {
            currDir.subdirs.reverse()
            tempList.reverse()
        }

        mutableSongs.clear()
        mutableSongs.addAll(tempList)
    }

    BackHandler(folderStack.size > 1) {
        folderStack.pop()
        currDir = folderStack.peek()
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
                    filterContent?.let {
                        it()
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        if (inSelectMode) {
                            SelectHeader(
                                selectedItems = selection.mapNotNull { songId ->
                                    mutableSongs.find { it.id == songId }
                                }.map { it.toMediaMetadata()},
                                totalItemCount = mutableSongs.size,
                                onSelectAll = {
                                    selection.clear()
                                    selection.addAll(mutableSongs.map { it.id })
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
                                text = pluralStringResource(
                                    R.plurals.n_song, currDir.toList().size, currDir.toList().size
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
            if (folderStack.size > 1)
                item(
                    key = "previous",
                    contentType = CONTENT_TYPE_FOLDER
                ) {
                    SongFolderItem(
                        folderTitle = "..",
                        subtitle = "Previous folder",
                        modifier = Modifier
                            .clickable {
                                if (folderStack.size > 1) {
                                    folderStack.pop()
                                    currDir = folderStack.peek()
                                }
                            }
                    )
                }

            // all subdirectories listed here
            itemsIndexed(
                items = currDir.subdirs,
                key = { _, item -> item.uid },
                contentType = { _, _ -> CONTENT_TYPE_FOLDER }
            ) { index, folder ->
                SongFolderItem(
                    folder = folder,
                    subtitle = "${folder.toList().size} Song${if (folder.toList().size > 1) "" else "s"}",
                    modifier = Modifier
                        .combinedClickable {
                            // navigate to next page
                            currDir = folderStack.push(folder)
                        }
                        .animateItemPlacement(),
                    menuState = menuState,
                    navController = navController
                )
            }

            // separator
            if (currDir.subdirs.size > 0 && mutableSongs.size > 0) {
                item(
                    key = "folder_songs_divider",
                ) {
                    HorizontalDivider(
                        thickness = DividerDefaults.Thickness,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }

            // all songs get listed here
            itemsIndexed(
                items = mutableSongs,
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
                                            println()
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = currDir.currentDir,
                                                    items = mutableSongs.map { it.toMediaMetadata() },
                                                    startIndex = mutableSongs.indexOf(song)
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
            visible = currDir.toList().isNotEmpty(),
            lazyListState = lazyListState,
            icon = Icons.Rounded.Shuffle,
            onClick = {
                playerConnection.playQueue(
                    ListQueue(
                        title = currDir.currentDir,
                        items = currDir.toSortedList(sortType, sortDescending).shuffled().map { it.toMediaMetadata() }
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
