package com.dd3boh.outertune.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.SongListItem
import com.dd3boh.outertune.ui.component.SortHeader
import com.dd3boh.outertune.ui.menu.SongMenu
import com.dd3boh.outertune.ui.utils.getDirectorytree
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.viewmodels.LibrarySongsViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibrarySongsFolderScreen(
    navController: NavController,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)

    val lazyListState = rememberLazyListState()

    // initialize with first directory
    if (viewModel.folderPositionStack.isEmpty()) {
        val cachedTree = getDirectorytree()
        if (cachedTree == null) {
            viewModel.syncLocalSongs(context, viewModel.databseLink)
        }

        viewModel.folderPositionStack.push(viewModel.localSongDirectoryTree.value)
    }

    // content to load for this page
    var currDir by remember { mutableStateOf(viewModel.folderPositionStack.peek()) }


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


                TopAppBar(
                    title = { if (viewModel.folderPositionStack.size > 1) Text(currDir.currentDir) else "Music Folders" },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                // go back to songs screen when backing out of root
                                if (viewModel.folderPositionStack.size <= 1) {
                                    navController.navigate("songs_folders_screen")
                                }
                                currDir = viewModel.folderPositionStack.pop()
                            }

                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null
                            )
                        }
                    },
//                            scrollBehavior = scrollBehavior
                )

            }


            // all subdirectories listed here
            itemsIndexed(
                items = currDir.subdirs,
                key = { _, item -> item.uid },
                contentType = { _, _ -> CONTENT_TYPE_SONG }
            ) { index, song ->

                Text(text = song.currentDir)

                IconButton(
                    onClick = {
//                        println("current is " + page.currentDir)
//                        println("next we go is " + song.currentDir)

                        // navigate to next page
                        currDir = viewModel.folderPositionStack.push(song)
                    }
                ) {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null
                    )
                }
            }


            // all songs get listed here
            itemsIndexed(
                items = currDir.files,
                key = { _, item -> item.id },
                contentType = { _, _ -> CONTENT_TYPE_SONG }
            ) { index, song ->
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

                            // local song indicator
                            if (song.song.isLocal == true) {
                                return@IconButton Icon(
                                    Icons.Rounded.Folder,
                                    contentDescription = null
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable {
                            if (song.id == mediaMetadata?.id) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                playerConnection.playQueue(ListQueue(title = context.getString(R.string.queue_all_songs),
                                    // I surely hope this applies to all in this folder...
                                    items = currDir
                                        .toList()
                                        .map { it.toMediaItem() }, startIndex = index))
                            }
                        }
                        .animateItemPlacement()
                )
            }
        }

    }
}
