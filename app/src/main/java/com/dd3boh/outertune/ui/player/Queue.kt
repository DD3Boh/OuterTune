package com.dd3boh.outertune.ui.player

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Timeline
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.ListItemHeight
import com.dd3boh.outertune.extensions.metadata
import com.dd3boh.outertune.extensions.move
import com.dd3boh.outertune.extensions.togglePlayPause
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.models.isShuffleEnabled
import com.dd3boh.outertune.playback.PlayerConnection.Companion.queueBoard
import com.dd3boh.outertune.ui.component.BottomSheet
import com.dd3boh.outertune.ui.component.BottomSheetState
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.MediaMetadataListItem
import com.dd3boh.outertune.ui.menu.SelectionMediaMetadataMenu
import com.dd3boh.outertune.utils.makeTimeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Queue(
    state: BottomSheetState,
    onBackgroundColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    val menuState = LocalMenuState.current

    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()

    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()

    var selection by remember {
        mutableStateOf(false)
    }
    val selectedSongs: MutableList<MediaMetadata> = mutableStateListOf()
    val selectedItems: MutableList<Timeline.Window> = mutableStateListOf()

    BottomSheet(
        state = state,
        backgroundColor = MaterialTheme.colorScheme.surfaceColorAtElevation(NavigationBarDefaults.Elevation),
        modifier = modifier,
        collapsedContent = {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    )
            ) {
                IconButton(onClick = { state.expandSoft() }) {
                    Icon(
                        imageVector = Icons.Rounded.ExpandLess,
                        tint = onBackgroundColor,
                        contentDescription = null,
                    )
                }
            }
        }
    ) {
        val queueTitle by playerConnection.queueTitle.collectAsState()
        val queueWindows by playerConnection.queueWindows.collectAsState()
        val mutableQueueWindows = remember { mutableStateListOf<Timeline.Window>() }
        val queueLength = remember(queueWindows) {
            queueWindows.sumOf { it.mediaItem.metadata!!.duration }
        }

        val coroutineScope = rememberCoroutineScope()
        val reorderableState = rememberReorderableLazyListState(
            onMove = { from, to ->
                mutableQueueWindows.move(from.index, to.index)
            },
            onDragEnd = { fromIndex, toIndex ->
                val pos = playerConnection.player.currentPosition
                val newQueuePos = queueBoard.move(fromIndex, toIndex, playerConnection.player.currentMediaItemIndex)
                queueBoard.setCurrQueue(playerConnection, autoSeek = false)
                queueBoard.getCurrentQueue()?.let {
                    if (newQueuePos != null) {
                        try {
                            playerConnection.player.seekTo(newQueuePos, pos)
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        )

        LaunchedEffect(queueWindows) {
            mutableQueueWindows.apply {
                clear()
                addAll(queueWindows)
            }
        }

        LaunchedEffect(mutableQueueWindows) {
            if (currentWindowIndex != -1)
                reorderableState.listState.scrollToItem(currentWindowIndex)
        }

        LazyColumn(
            state = reorderableState.listState,
            contentPadding = WindowInsets.systemBars
                .add(
                    WindowInsets(
                        top = ListItemHeight,
                        bottom = ListItemHeight
                    )
                )
                .asPaddingValues(),
            modifier = Modifier
                .reorderable(reorderableState)
                .nestedScroll(state.preUpPostDownNestedScrollConnection)
        ) {
            itemsIndexed(
                items = mutableQueueWindows,
                key = { _, item -> item.uid.hashCode() }
            ) { index, window ->
                ReorderableItem(
                    reorderableState = reorderableState,
                    key = window.uid.hashCode()
                ) {
                    val currentItem by rememberUpdatedState(window)
                    val dismissState = rememberSwipeToDismissBoxState(
                        positionalThreshold = { totalDistance ->
                            totalDistance
                        },
                        confirmValueChange = { dismissValue ->
                            when (dismissValue) {
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    playerConnection.player.removeMediaItem(currentItem.firstPeriodIndex)
                                    return@rememberSwipeToDismissBoxState true
                                }

                                SwipeToDismissBoxValue.EndToStart -> {
                                    playerConnection.player.removeMediaItem(currentItem.firstPeriodIndex)
                                    return@rememberSwipeToDismissBoxState true
                                }

                                SwipeToDismissBoxValue.Settled -> {
                                    return@rememberSwipeToDismissBoxState false
                                }
                            }
                        }
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {},
                        content = {
                            Row(
                                horizontalArrangement = Arrangement.Center
                            ) {
                                // selection checkbox. Standard multiselect doesn't work in queue...
                                if (selection) {
                                    IconButton(
                                        modifier = Modifier
                                            .align(Alignment.CenterVertically),
                                        onClick = {
                                            println(window.mediaItem.metadata!!.title)
                                            if (window.mediaItem.metadata!! in selectedSongs) {
                                                selectedSongs.remove(window.mediaItem.metadata!!)
                                                selectedItems.remove(currentItem)
                                            } else {
                                                selectedSongs.add(window.mediaItem.metadata!!)
                                                selectedItems.add(currentItem)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            if (window.mediaItem.metadata!! in selectedSongs) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                            contentDescription = null,
                                            tint = LocalContentColor.current,
                                        )
                                    }
                                }

                                MediaMetadataListItem(
                                    mediaMetadata = window.mediaItem.metadata!!,
                                    isActive = index == currentWindowIndex,
                                    isPlaying = isPlaying,
                                    trailingContent = {
                                        IconButton(
                                            onClick = { },
                                            modifier = Modifier
                                                .detectReorder(reorderableState)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DragHandle,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    isSelected = selection && selectedSongs.find { it == window.mediaItem.metadata!! } != null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (selectedSongs.isEmpty()) {
                                                    coroutineScope.launch(Dispatchers.Main) {
                                                        if (index == currentWindowIndex) {
                                                            playerConnection.player.togglePlayPause()
                                                        } else {
                                                            playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                                                            playerConnection.player.playWhenReady = true
                                                        }
                                                    }
                                                } else {
                                                    if (window.mediaItem.metadata!! in selectedSongs) {
                                                        selectedSongs.remove(window.mediaItem.metadata!!)
                                                        selectedItems.remove(currentItem)
                                                    } else {
                                                        selectedSongs.add(window.mediaItem.metadata!!)
                                                        selectedItems.add(currentItem)
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                selection = true
                                                if (window.mediaItem.metadata!! in selectedSongs) {
                                                    selectedSongs.remove(window.mediaItem.metadata!!)
                                                    selectedItems.remove(currentItem)
                                                } else {
                                                    selectedSongs.add(window.mediaItem.metadata!!)
                                                    selectedItems.add(currentItem)
                                                }
                                            }
                                        )
                                        .detectReorderAfterLongPress(reorderableState)
                                )
                            }
                        }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme
                        .surfaceColorAtElevation(NavigationBarDefaults.Elevation)
                        .copy(alpha = 0.95f)
                )
                .windowInsetsPadding(
                    WindowInsets.systemBars
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                // handle selection mode
                if (selection) {
                    Text(
                        text = "${selectedSongs.size}/${queueWindows.size} selected",
                        modifier = Modifier.weight(1f)
                    )

                    // option menu
                    IconButton(
                        onClick = {
                            menuState.show {
                                SelectionMediaMetadataMenu(
                                    songSelection = selectedSongs,
                                    onDismiss = menuState::dismiss,
                                    clearAction = {
                                        selectedSongs.clear()
                                        selectedItems.clear()
                                    },
                                    currentItems = selectedItems
                                )
                            }
                        }
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = null,
                            tint = LocalContentColor.current
                        )
                    }

                    // select/deselect all
                    if (selectedSongs.size < queueWindows.size) {
                        IconButton(
                            onClick = {
                                selectedSongs.clear()
                                selectedSongs.addAll(mutableQueueWindows.map { it.mediaItem.metadata!! })
                                selectedItems.clear()
                                selectedItems.addAll(mutableQueueWindows)
                            }
                        ) {
                            Icon(
                                Icons.Rounded.SelectAll,
                                contentDescription = null,
                                tint = LocalContentColor.current
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                selectedSongs.clear()
                                selectedItems.clear()
                            }
                        ) {
                            Icon(
                                Icons.Rounded.Deselect,
                                contentDescription = null,
                                tint = LocalContentColor.current
                            )
                        }
                    }

                    // close selection mode
                    IconButton(
                        onClick = { selection = false },
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = null
                        )
                    }
                } else {
                    // queue title and multiqueue
                    var expand by remember { mutableStateOf(false) }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(12.dp))
                            .padding(2.dp)
                            .weight(1f)
                            .clickable { expand = !expand }
                    ) {
                        Text(
                            text = queueTitle.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        )
    
                        IconButton(
                            onClick = {
                                expand = !expand
                            }
                        ) {
                            Icon(
                                imageVector = if (expand) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription =  null,
                            )
                        }
    
                        DropdownMenu(
                            expanded = expand,
                            onDismissRequest = { expand = false },
                            modifier = Modifier.weight(1f) // how tf do i make this take up whole length? or should I?
                        ) {
                            var queueNum = 0 // used for cosmetic purposes only
                            // list of queues you can switch to
                            queueBoard.getAllQueues().forEach {
                                queueNum ++
                                DropdownMenuItem(
                                    text = { Text("$queueNum . ${it.title}") },
                                    onClick = {
                                      // switch to this queue
                                        queueBoard.setCurrQueue(it, playerConnection.player)
                                        expand = false
                                    },
                                    leadingIcon = {
                                        IconButton(
                                            onClick = {
                                                queueBoard.deleteQueue(it)
                                                queueBoard.setCurrQueue(playerConnection)
                                                expand = false
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription =  null,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = pluralStringResource(R.plurals.n_song, queueWindows.size, queueWindows.size),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = makeTimeString(queueLength * 1000L),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        val shuffleModeEnabled by isShuffleEnabled.collectAsState()

        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f))
                .fillMaxWidth()
                .height(
                    ListItemHeight +
                            WindowInsets.systemBars
                                .asPaddingValues()
                                .calculateBottomPadding()
                )
                .align(Alignment.BottomCenter)
                .clickable {
                    state.collapseSoft()
                }
                .windowInsetsPadding(
                    WindowInsets.systemBars
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                )
                .padding(12.dp)
        ) {
            IconButton(
                modifier = Modifier.align(Alignment.CenterStart),
                onClick = {
                    coroutineScope.launch {
                        reorderableState.listState.animateScrollToItem(
                            if (shuffleModeEnabled) playerConnection.player.currentMediaItemIndex else 0
                        )
                    }.invokeOnCompletion {
                        playerConnection.triggerShuffle()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.alpha(if (shuffleModeEnabled) 1f else 0.5f)
                )
            }

            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
