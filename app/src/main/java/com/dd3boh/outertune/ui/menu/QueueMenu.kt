package com.dd3boh.outertune.ui.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.db.entities.PlaylistSongMap
import com.dd3boh.outertune.playback.PlayerConnection.Companion.queueBoard
import com.dd3boh.outertune.ui.component.GridMenu
import com.dd3boh.outertune.ui.component.GridMenuItem
import com.dd3boh.outertune.ui.component.QueueListItem

@Composable
fun QueueMenu(
    onDismiss: () -> Unit,
    refreshUi: () -> Unit,
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val currQueue = queueBoard.getCurrentQueue()
    if (currQueue == null) {
        onDismiss()
        refreshUi()
        return
    }
    val songs = currQueue.getCurrentQueueShuffled()

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showChooseQueueDialog by rememberSaveable {
        mutableStateOf(false)
    }

    // dialogs
    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onAdd = { playlist ->
            // shove all songs into the playlist
            var position = playlist.songCount
            database.query {
                songs.forEach {
                    insert(
                        PlaylistSongMap(
                            songId = it.id,
                            playlistId = playlist.id,
                            position = position++
                        )
                    )
                }

            }
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        }
    )

    AddToQueueDialog(
        isVisible = showChooseQueueDialog,
        onAdd = { queueName ->
            queueBoard.add(queueName, songs, forceInsert = true, delta = false)
            queueBoard.setCurrQueue(playerConnection)
        },
        onDismiss = {
            showChooseQueueDialog = false
            onDismiss() // here we dismiss since we switch to the queue anyways
            refreshUi()
        }
    )

    // queue item
    QueueListItem(queue = currQueue)

    HorizontalDivider()

    // menu options
    GridMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            title = R.string.add_to_queue
        ) {
            showChooseQueueDialog = true
        }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            title = R.string.add_to_playlist
        ) {
            showChoosePlaylistDialog = true
        }
    }
}