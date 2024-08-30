package com.dd3boh.outertune.ui.player

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.constants.ListItemHeight
import com.dd3boh.outertune.constants.LockQueueKey
import com.dd3boh.outertune.constants.PlayerHorizontalPadding
import com.dd3boh.outertune.extensions.metadata
import com.dd3boh.outertune.extensions.move
import com.dd3boh.outertune.extensions.togglePlayPause
import com.dd3boh.outertune.extensions.toggleRepeatMode
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
    var lockQueue by rememberPreference(LockQueueKey, defaultValue = false)

    val menuState = LocalMenuState.current

    // player vars
    val playerConnection = LocalPlayerConnection.current ?: return

    val shuffleModeEnabled by isShuffleEnabled.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()

    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()

    // selection vars
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
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

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

                queueBoard.moveSong(
                    fromIndex,
                    toIndex,
                    playerConnection.player.currentMediaItemIndex,
                    playerConnection.service
                )
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
                reorderableState.listState.animateScrollToItem(playerConnection.player.currentMediaItemIndex)
            }
        }

        // for multiqueue
        val reorderableStateEx = rememberReorderableLazyListState(
            onMove = { from, to ->
                mutableQueues.move(from.index, to.index)
            },
            onDragEnd = { fromIndex, toIndex ->
                queueBoard.move(fromIndex, toIndex, playerConnection.service)
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


        val queueList: @Composable ColumnScope.() -> Unit = {
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

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            lockQueue = !lockQueue
                        }
                    ) {
                        Icon(
                            imageVector = if (lockQueue) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = null,
                        )
                    }
                    if (!landscape) {
                        ResizableIconButton(
                            icon = Icons.Rounded.Close,
                            onClick = {
                                multiqueueExpand = false
                            },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            }

            if (mutableQueues.isEmpty()) {
                Text(text = "No queues")
            }

            LazyColumn(
                state = reorderableStateEx.listState,
                modifier = Modifier
                    .reorderable(reorderableStateEx)
                    .nestedScroll(state.preUpPostDownNestedScrollConnection)
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
                                            detachedQueueTitle = mq.title ?: ""
                                        }

                                        updateQueues()
                                    },
                                    onLongClick = {
                                        menuState.show {
                                            QueueMenu(
                                                mq = mq,
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
                                Row(
                                    modifier = Modifier.weight(1f, false)
                                ) {
                                    if (!lockQueue) {
                                        ResizableIconButton(
                                            icon = Icons.Rounded.Close,
                                            onClick = {
                                                val remainingQueues =
                                                    queueBoard.deleteQueue(mq, playerConnection.service)
                                                if (playingQueue == index) {
                                                    queueBoard.setCurrQueue(playerConnection)
                                                }
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
                                    }
                                    Text(
                                        text = "${index + 1}. ${mq.title}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 0.dp)
                                    )
                                }

                                if (!lockQueue) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragHandle,
                                        contentDescription = null,
                                        modifier = Modifier.detectReorder(reorderableStateEx)
                                    )
                                }
                            }
                        }
                    } // ReorderableItem
                }
            }
        }

        val songHeader: @Composable ColumnScope.() -> Unit = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Column {
                    Text(
                        text = "Songs",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (detachedHead) {
                        Text(
                            text = detachedQueueTitle,
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // play the detached queue
                if (detachedHead) {
                    ResizableIconButton(
                        icon = Icons.Rounded.PlayArrow,
                        onClick = {
                            coroutineScope.launch(Dispatchers.Main) {
                                // change to this queue, seek to the item clicked on
                                queueBoard.setCurrQueue(detachedQueueIndex, playerConnection, false)
                                playerConnection.player.seekTo(detachedQueuePos, C.TIME_UNSET)
                                playerConnection.player.playWhenReady = true
                                detachedHead = false
                                updateQueues()
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        val songList: @Composable ColumnScope.() -> Unit = {
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
                                                queueBoard.setCurrQueue(detachedQueueIndex, playerConnection, false)
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
                    contentPadding = if (multiqueueExpand && !landscape) PaddingValues(0.dp)
                                     else PaddingValues(0.dp, 16.dp), // header may cut off first song
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
                            val content = @Composable {
                                MediaMetadataListItem(
                                    mediaMetadata = window.mediaItem.metadata!!,
                                    isActive = index == currentWindowIndex,
                                    isPlaying = isPlaying,
                                    trailingContent = {
                                        if (!lockQueue) {
                                            IconButton(
                                                onClick = { },
                                                modifier = Modifier.detectReorder(reorderableState)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DragHandle,
                                                    contentDescription = null
                                                )
                                            }
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
                                                            playerConnection.player.prepare()
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

                            if (!lockQueue) {
                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = {},
                                    content = { content() }
                                )
                            } else {
                                content()
                            }
                        }
                    }
                }
            }
        }

        // queue info + player controls
        val bottomNav: @Composable ColumnScope.() -> Unit = {
            // queue info
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
                            .clickable {
                                if (!landscape) {
                                    multiqueueExpand = !multiqueueExpand
                                }
                            }
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
                            enabled = !landscape,
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
                            text = "${currentWindowIndex + 1} / ${queueWindows.size}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = makeTimeString(queueLength * 1000L),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // player controls
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding)
                ) {

                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            icon = Icons.Rounded.Shuffle,
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.Center)
                                .padding(4.dp)
                                .alpha(if (shuffleModeEnabled) 1f else 0.3f),
                            color = onBackgroundColor,
                            onClick = { playerConnection.triggerShuffle() }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            icon = Icons.Rounded.SkipPrevious,
                            enabled = canSkipPrevious,
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.Center),
                            color = onBackgroundColor,
                            onClick = playerConnection.player::seekToPrevious
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            icon = if (playbackState == STATE_ENDED) Icons.Rounded.Replay else if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            modifier = Modifier
                                .size(36.dp)
                                .align(Alignment.Center),
                            color = onBackgroundColor,
                            onClick = {
                                if (playbackState == STATE_ENDED) {
                                    playerConnection.player.seekTo(0, 0)
                                    playerConnection.player.playWhenReady = true
                                } else {
                                    playerConnection.player.togglePlayPause()
                                }
                            }
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            icon = Icons.Rounded.SkipNext,
                            enabled = canSkipNext,
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.Center),
                            color = onBackgroundColor,
                            onClick = playerConnection.player::seekToNext
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            icon = when (repeatMode) {
                                REPEAT_MODE_OFF, REPEAT_MODE_ALL -> Icons.Rounded.Repeat
                                REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                                else -> throw IllegalStateException()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.Center)
                                .padding(4.dp)
                                .alpha(if (repeatMode == REPEAT_MODE_OFF) 0.3f else 1f),
                            color = onBackgroundColor,
                            onClick = playerConnection.player::toggleRepeatMode
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        // finally render ui
        if (landscape) {
            Row(
                modifier = Modifier
                    .nestedScroll(state.preUpPostDownNestedScrollConnection)
                    .padding(WindowInsets.systemBars.asPaddingValues())
            ) {
                // song header & song list
                Column(
                    modifier = Modifier.fillMaxWidth(0.5f)
                ) {
                    songHeader()
                    songList()
                }

                Spacer(Modifier.width(8.dp))

                // multiqueue list & navbar
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .nestedScroll(state.preUpPostDownNestedScrollConnection)
                            .weight(1f, false)
                    ) {
                        queueList()
                    }

                    // nav bar
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .fillMaxWidth()
                            .clickable {
                                state.collapseSoft()
                            }
                            .windowInsetsPadding(
                                WindowInsets.systemBars
                                    .only(WindowInsetsSides.Bottom)
                            )
                            .padding(horizontal = 12.dp)
                    ) {
                        bottomNav()
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        } else {
            // queue contents
            Column(
                modifier = Modifier
                    .nestedScroll(state.preUpPostDownNestedScrollConnection)
                    .padding(
                        WindowInsets.systemBars
                            .add(WindowInsets(bottom = ListItemHeight * 2))
                            .asPaddingValues()
                    )
            ) {
                // multiqueue list
                if (multiqueueExpand) {
                    Column(
                        modifier = Modifier.fillMaxHeight(0.4f)
                    ) {
                        queueList()
                    }

                    Spacer(Modifier.height(8.dp))
                    songHeader() // song header
                }

                songList() // song list
            }

            // nav bar
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clickable {
                        state.collapseSoft()
                    }
                    .windowInsetsPadding(
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    )
                    .padding(horizontal = 12.dp)
            ) {
                bottomNav()
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
