package com.dd3boh.outertune.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dd3boh.outertune.db.entities.PlaylistSong
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.ui.menu.SelectionMediaMetadataMenu
import com.dd3boh.outertune.ui.menu.SelectionSongMenu
import com.dd3boh.outertune.ui.utils.ItemWrapper
import com.zionhuang.innertube.models.SongItem

@Composable
private fun SelectHeaderContent(
    count: Int,
    total: Int,
    onSelectAllToggle: () -> Unit,
    onMenuClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp, bottom = 3.dp)
    ) {
        Text(
            text = "${count}/${total} selected",
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        IconButton(onClick = onSelectAllToggle) {
            Icon(
                imageVector = if (count == total) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                contentDescription = null
            )
        }

        IconButton(onClick = onMenuClick) {
            Icon(Icons.Rounded.MoreVert, contentDescription = null)
        }

        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = null)
        }
    }
}

@Composable
fun SelectHeader(
    wrappedSongs: MutableList<ItemWrapper<PlaylistSong>>,
    menuState: MenuState,
    onDismiss: (() -> Unit)? = null,
    jvmHax: Boolean // Remove if not needed
) {
    val count = wrappedSongs.count { it.isSelected }

    SelectHeaderContent(
        count = count,
        total = wrappedSongs.size,
        onSelectAllToggle = { wrappedSongs.forEach { it.isSelected = !it.isSelected } },
        onMenuClick = {
            menuState.show {
                SelectionSongMenu(
                    songSelection = wrappedSongs.filter { it.isSelected }.map { it.item.song },
                    onDismiss = menuState::dismiss,
                    clearAction = { wrappedSongs.forEach { it.isSelected = false }; onDismiss?.invoke() }
                )
            }
        },
        onDismiss = { wrappedSongs.forEach { it.isSelected = false }; onDismiss?.invoke() }
    )
}

@Composable
fun SelectHeader(
    wrappedSongs: MutableList<ItemWrapper<SongItem>>,
    menuState: MenuState,
    onDismiss: (() -> Unit)? = null,
    jvmHax: Int // Remove if not needed
) {
    val count = wrappedSongs.count { it.isSelected }

    SelectHeaderContent(
        count = count,
        total = wrappedSongs.size,
        onSelectAllToggle = { wrappedSongs.forEach { it.isSelected = !it.isSelected } },
        onMenuClick = {
            menuState.show {
                SelectionMediaMetadataMenu(
                    songSelection = wrappedSongs.filter { it.isSelected }.map { it.item.toMediaMetadata() },
                    onDismiss = menuState::dismiss,
                    currentItems = emptyList(),
                    clearAction = { wrappedSongs.forEach { it.isSelected = false }; onDismiss?.invoke() }
                )
            }
        },
        onDismiss = { wrappedSongs.forEach { it.isSelected = false }; onDismiss?.invoke() }
    )
}

@Composable
fun SelectHeader(
    wrappedSongs: MutableList<ItemWrapper<Song>>,
    menuState: MenuState,
    onDismiss: (() -> Unit)? = null
) {
    val count = wrappedSongs.count { it.isSelected }

    SelectHeaderContent(
        count = count,
        total = wrappedSongs.size,
        onSelectAllToggle = { wrappedSongs.forEach { it.isSelected = !it.isSelected } },
        onMenuClick = {
            menuState.show {
                SelectionSongMenu(
                    songSelection = wrappedSongs.filter { it.isSelected }.map { it.item },
                    onDismiss = menuState::dismiss,
                    clearAction = { wrappedSongs.forEach { it.isSelected = false }; onDismiss?.invoke() }
                )
            }
        },
        onDismiss = { wrappedSongs.forEach { it.isSelected = false }; onDismiss?.invoke() }
    )
}