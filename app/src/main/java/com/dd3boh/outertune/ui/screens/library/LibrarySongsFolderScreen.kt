package com.dd3boh.outertune.ui.screens.library

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.*
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.extensions.togglePlayPause
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.ui.component.HideOnScrollFAB
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.SongFolderItem
import com.dd3boh.outertune.ui.component.SongListItem
import com.dd3boh.outertune.ui.component.SortHeader
import com.dd3boh.outertune.ui.component.SwipeToQueueBox
import com.dd3boh.outertune.ui.menu.SongMenu
import com.dd3boh.outertune.ui.utils.getDirectorytree
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.viewmodels.LibrarySongsViewModel
import java.util.Stack

@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibrarySongsFolderScreen(
    navController: NavController,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
    filterContent: @Composable() (() -> Unit)? = null
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val folderStack = remember { viewModel.folderPositionStack }
    val (flatSubfolders) = rememberPreference(FlatSubfoldersKey, defaultValue = true)

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)

    var inLocal by viewModel.inLocal

    val lazyListState = rememberLazyListState()

    // destroy old structure when pref changes
    flatSubfolders.let {
        viewModel.folderPositionStack = Stack()
    }

    // initialize with first directory
    if (folderStack.isEmpty()) {
        val cachedTree = getDirectorytree()
        if (cachedTree == null) {
            viewModel.getLocalSongs(context, viewModel.databseLink)
        }

        folderStack.push(if (flatSubfolders) viewModel.localSongDirectoryTree.value.toFlattenedTree()
        else viewModel.localSongDirectoryTree.value)
    }

    // content to load for this page
    var currDir by remember {
        mutableStateOf(folderStack.peek())
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
            filterContent?.let {
                item(
                    key = "filter",
                    contentType = CONTENT_TYPE_HEADER
                ) {
                    it()
                }
            }

            item(
                key = "header",
                contentType = CONTENT_TYPE_HEADER
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    SortHeader(
                        sortType = sortType,
                        sortDescending = sortDescending,
                        onSortTypeChange = onSortTypeChange,
                        onSortDescendingChange = onSortDescendingChange,
                        sortTypeText = { sortType ->
                            when (sortType) {
                                SongSortType.CREATE_DATE -> R.string.sort_by_create_date
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
                            else inLocal = false
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
            if (currDir.subdirs.size > 0 && currDir.files.size > 0) {
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
                items = currDir.files,
                key = { _, item -> item.id },
                contentType = { _, _ -> CONTENT_TYPE_SONG }
            ) { index, song ->
                SwipeToQueueBox(
                    item = song.toMediaItem(),
                    content = {
                        SongListItem(
                            song = song,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            trailingContent = {
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
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (song.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = context.getString(R.string.queue_all_songs),
                                                    // I surely hope this applies to all in this folder...
                                                    items = currDir
                                                        .toList()
                                                        .map { it.toMediaItem() },
                                                    startIndex = index
                                                )
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        menuState.show {
                                            SongMenu(
                                                originalSong = song,
                                                navController = navController,
                                                onDismiss = menuState::dismiss
                                            )
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
                        title = context.getString(R.string.queue_all_songs),
                        items = currDir.toList().shuffled().map { it.toMediaItem() }
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
