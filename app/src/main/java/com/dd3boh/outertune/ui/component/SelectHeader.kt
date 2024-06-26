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
import androidx.compose.ui.unit.dp
import com.dd3boh.outertune.db.entities.PlaylistSong
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.ui.menu.SelectionSongMenu
import com.dd3boh.outertune.ui.utils.ItemWrapper


@Composable
fun SelectHeader(
    wrappedSongs: MutableList<ItemWrapper<PlaylistSong>>,
    menuState: MenuState,
    onDismiss: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        val count = wrappedSongs.count { it.isSelected }
        Text(
            text = "${count}/${wrappedSongs.size} selected",
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = {
                if (count == wrappedSongs.size) {
                    wrappedSongs.forEach { it.isSelected = false }
                } else {
                    wrappedSongs.forEach { it.isSelected = true }
                }
            },
        ) {
            Icon(
                if (count == wrappedSongs.size) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                contentDescription = null
            )
        }

        IconButton(
            onClick = {
                menuState.show {
                    SelectionSongMenu(
                        songSelection = wrappedSongs.filter { it.isSelected }.map { it.item.song },
                        onDismiss = menuState::dismiss,
                        clearAction = {
                            wrappedSongs.forEach { it.isSelected = false }
                            onDismiss?.invoke()
                        }
                    )
                }
            },
        ) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = null
            )
        }

        IconButton(
            onClick = {
                wrappedSongs.forEach { it.isSelected = false }
                onDismiss?.invoke()
            },
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = null
            )
        }
    }
}

@Composable
fun SelectHeader(
    wrappedSongs: MutableList<ItemWrapper<Song>>,
    menuState: MenuState,
    onDismiss: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        val count = wrappedSongs.count { it.isSelected }
        Text(
            text = "${count}/${wrappedSongs.size} selected",
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = {
                if (count == wrappedSongs.size) {
                    wrappedSongs.forEach { it.isSelected = false }
                } else {
                    wrappedSongs.forEach { it.isSelected = true }
                }
            },
        ) {
            Icon(
                if (count == wrappedSongs.size) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                contentDescription = null
            )
        }

        IconButton(
            onClick = {
                menuState.show {
                    SelectionSongMenu(
                        songSelection = wrappedSongs.filter { it.isSelected }.map { it.item },
                        onDismiss = menuState::dismiss,
                        clearAction = {
                            wrappedSongs.forEach { it.isSelected = false }
                            onDismiss?.invoke()
                        }
                    )
                }
            },
        ) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = null
            )
        }

        IconButton(
            onClick = {
                wrappedSongs.forEach { it.isSelected = false }
                onDismiss?.invoke()
            },
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = null
            )
        }
    }
}