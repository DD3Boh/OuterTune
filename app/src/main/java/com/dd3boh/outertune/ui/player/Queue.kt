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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Timeline
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.ListItemHeight
import com.dd3boh.outertune.constants.SwipeToDismissKey
import com.dd3boh.outertune.extensions.metadata
import com.dd3boh.outertune.extensions.move
import com.dd3boh.outertune.extensions.togglePlayPause
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.models.MultiQueueObject
import com.dd3boh.outertune.models.isShuffleEnabled
import com.dd3boh.outertune.playback.PlayerConnection.Companion.queueBoard
import com.dd3boh.outertune.ui.component.BottomSheet
import com.dd3boh.outertune.ui.component.BottomSheetState
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.MediaMetadataListItem
import com.dd3boh.outertune.ui.component.ResizableIconButton
import com.dd3boh.outertune.ui.menu.QueueMenu
import com.dd3boh.outertune.ui.menu.SelectionMediaMetadataMenu
import com.dd3boh.outertune.utils.makeTimeString
import com.dd3boh.outertune.utils.rememberPreference
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
    onTerminate: () -> Unit,
    modifier: Modifier = Modifier,
    onBackgroundColor: Color = Color.Unspecified,
) {
    val (swipeToDismiss) = rememberPreference(key = SwipeToDismissKey, defaultValue = true)

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
        },
    ) {
        val coroutineScope = rememberCoroutineScope()

        // current queue vars
        val queueTitle by playerConnection.queueTitle.collectAsState()
        val queueWindows by playerConnection.queueWindows.collectAsState()
        val mutableQueueWindows = remember { mutableStateListOf<Timeline.Window>() }
        val queueLength = remember(queueWindows) {
            queueWindows.sumOf { it.mediaItem.metadata!!.duration }
        }

        // multi queue vars
        var multiqueueExpand by remember { mutableStateOf(false) }
        val mutableQueues = remember { mutableStateListOf<MultiQueueObject>() }
        var playingQueue by remember { mutableIntStateOf(queueBoard.getMasterIndex()) }
        var detachedHead by remember { mutableStateOf(queueBoard.detachedHead) }
        val detachedQueue = remember { mutableStateListOf<MediaMetadata>() }
        var detachedQueueIndex by remember { mutableIntStateOf(-1) }
        var detachedQueuePos by remember { mutableIntStateOf(-1) }
        var detachedQueueTitle by remember { mutableStateOf("") }
        val detachedQueueListState = rememberLazyListState()

        // for main songs list
        val reorderableState = rememberReorderableLazyListState(
            onMove = { from, to ->
                mutableQueueWindows.move(from.index, to.index)
            },
            onDragEnd = { fromIndex, toIndex ->
                if (fromIndex == toIndex) {
                    return@rememberReorderableLazyListState
                }

                queueBoard.moveSong(fromIndex, toIndex, playerConnection.player.currentMediaItemIndex)
                playerConnection.player.moveMediaItem(fromIndex, toIndex)
            }
        )

        /**
         * This reloads the queue in the UI. This exists because for some stupid reason reassigning a remember-ed var
         * does not trigger LaunchedEffect, even though that is the point of remember.
         */
        fun updateQueues() {
            if (detachedHead) {
                coroutineScope.launch {
                    delay(300) // needed for scrolling to queue when switching to new queue
                    detachedQueueListState.animateScrollToItem(detachedQueuePos)
                }
                return
            }

            mutableQueues.apply {
                clear()
                addAll(queueBoard.getAllQueues())
            }
            playingQueue = queueBoard.getMasterIndex()
            coroutineScope.launch {
                delay(300) // needed for scrolling to queue when switching to new queue
                reorderableState.listState.animateScrollToItem(
                   playerConnection.player.currentMediaItemIndex
                )
            }
        }

        // for multiqueue
        val reorderableStateEx = rememberReorderableLazyListState(
            onMove = { from, to ->
                mutableQueues.move(from.index, to.index)
            },
            onDragEnd = { fromIndex, toIndex ->
                queueBoard.move(fromIndex, toIndex)
                coroutineScope.launch {
                    updateQueues()
                }
            }
        )

        LaunchedEffect(queueWindows) { // add to queue windows
            mutableQueueWindows.apply {
                clear()
                addAll(queueWindows)
            }
        }

        LaunchedEffect(mutableQueueWindows) { // scroll to song
            if (currentWindowIndex != -1)
                reorderableState.listState.scrollToItem(currentWindowIndex)
        }

        LaunchedEffect(mutableQueues) { // scroll to queue
            if (currentWindowIndex != -1)
                reorderableStateEx.listState.scrollToItem(playingQueue)
        }

        LaunchedEffect(Unit) {
            updateQueues() // initiate queues
        }

        Column(
            modifier = Modifier
                .nestedScroll(state.preUpPostDownNestedScrollConnection)
                .padding(WindowInsets.systemBars
                    .add(
                        WindowInsets(
                            top = ListItemHeight,
                            bottom = ListItemHeight
                        )
                    )
                    .asPaddingValues())
        ) {
            // multiqueue list
            if (multiqueueExpand) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Queues",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ResizableIconButton(
                        icon = Icons.Rounded.Close,
                        onClick = {
                            multiqueueExpand = false
                        },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                if (mutableQueues.isEmpty()) {
                    Text(text = "No queues")
                }

                LazyColumn(
                    state = reorderableStateEx.listState,
                    modifier = Modifier
                        .reorderable(reorderableStateEx)
                        .nestedScroll(state.preUpPostDownNestedScrollConnection)
                        .heightIn(max = 250.dp) // TODO: set to 50% ish height
                ) {
                    itemsIndexed(
                        items = mutableQueues,
                        key = { _, item -> item.hashCode() }
                    ) { index, mq ->
                        ReorderableItem(
                            reorderableState = reorderableStateEx,
                            key = mq.hashCode()
                        ) {
                            Row( // wrapper
                                modifier = Modifier
                                    .background(
                                        if (playingQueue == index) {
                                            MaterialTheme.colorScheme.tertiary.copy(0.3f)
                                        } else if (detachedHead && detachedQueueIndex == index) {
                                            MaterialTheme.colorScheme.tertiary.copy(0.1f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            // clicking on queue shows it in the ui
                                            if (playingQueue == index) {
                                                detachedHead = false
                                            } else {
                                                detachedHead = true
                                                detachedQueue.clear()
                                                detachedQueue.addAll(mq.getCurrentQueueShuffled())
                                                detachedQueueIndex = index
                                                detachedQueuePos = mq.queuePos
                                                detachedQueueTitle = mq.title?: ""
                                            }

                                            updateQueues()
                                        },
                                        onLongClick = {
                                            menuState.show {
                                                QueueMenu(
                                                    onDismiss = menuState::dismiss,
                                                    refreshUi = { updateQueues() }
                                                )
                                            }
                                        }
                                    )
                            ) {
                                Row( // row contents (wrapper is needed for margin)
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(horizontal = 40.dp, vertical = 8.dp)
                                        .fillMaxWidth()
                                ) {
                                    Row {
                                        ResizableIconButton(
                                            icon = Icons.Rounded.Close,
                                            onClick = {
                                                val remainingQueues = queueBoard.deleteQueue(mq)
                                                queueBoard.setCurrQueue(playerConnection)
                                                detachedHead = false
                                                updateQueues()
                                                if (remainingQueues < 1) {
                                                    onTerminate.invoke()
                                                } else {
                                                    coroutineScope.launch {
                                                        reorderableState.listState.animateScrollToItem(
                                                            playerConnection.player.currentMediaItemIndex
                                                        )
                                                    }
                                                }
                                            },
                                        )
                                        Text(
                                            text = "${index + 1}. ${mq.title}",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 0.dp)
                                        )
                                    }

                                    ResizableIconButton(
                                        icon = Icons.Rounded.DragHandle,
                                        onClick = { },
                                        modifier = Modifier
                                            .detectReorder(reorderableStateEx)
                                    )
                                }
                            }
                        } // ReorderableItem
                    }
                }

                Row(modifier = Modifier.padding(vertical = 4.dp)) { }

                Text(
                    text = "Songs" + if (detachedHead) ": $detachedQueueTitle" else "",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            // songs list
            if (detachedHead) { // detached head queue
                /**
                 * TODO: Probably integrate this with the main queue. Currently it is read only
                 * Not sure if we even want all the extra complexity both in code and for the user
                 */
                LazyColumn(
                    state = detachedQueueListState,
                    modifier = Modifier.nestedScroll(state.preUpPostDownNestedScrollConnection)
                ) {
                    itemsIndexed(
                        items = detachedQueue,
                        key = { _, item -> item.hashCode() }
                    ) { index, window ->
                        Row(
                            horizontalArrangement = Arrangement.Center
                        ) {
                            MediaMetadataListItem(
                                mediaMetadata = window,
                                isActive = index == detachedQueuePos,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            coroutineScope.launch(Dispatchers.Main) {
                                                // change to this queue, seek to the item clicked on
                                                queueBoard.setCurrQueue(detachedQueueIndex, playerConnection.player, false)
                                                playerConnection.player.seekTo(index, C.TIME_UNSET)
                                                detachedHead = false
                                                updateQueues()
                                            }
                                        },
                                        onLongClick = { }
                                    )
                            )
                        }
                    }
                }
            } else { // actual playing queue
                LazyColumn(
                    state = reorderableState.listState,
                    contentPadding = if (multiqueueExpand) PaddingValues(0.dp) else PaddingValues(0.dp, 16.dp), // header may cut off first song
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
                                    if (!swipeToDismiss) {
                                        return@rememberSwipeToDismissBoxState false
                                    }
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
                                                    if (window.mediaItem.metadata!! in selectedSongs) Icons.Rounded.CheckBox
                                                    else Icons.Rounded.CheckBoxOutlineBlank,
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
                                                        if (!selection) {
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
                    // queue title and show multiqueue button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(12.dp))
                            .padding(2.dp)
                            .weight(1f)
                            .clickable { multiqueueExpand = !multiqueueExpand }
                    ) {
                        Text(
                            text = queueTitle.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        ResizableIconButton(
                            icon = if (multiqueueExpand) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            onClick = {
                                multiqueueExpand = !multiqueueExpand
                            },
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(horizontal = 8.dp)
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
