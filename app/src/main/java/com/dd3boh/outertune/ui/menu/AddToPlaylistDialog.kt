package com.dd3boh.outertune.ui.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.ListThumbnailSize
import com.dd3boh.outertune.db.entities.Playlist
import com.dd3boh.outertune.db.entities.PlaylistEntity
import com.dd3boh.outertune.ui.component.DefaultDialog
import com.dd3boh.outertune.ui.component.ListDialog
import com.dd3boh.outertune.ui.component.ListItem
import com.dd3boh.outertune.ui.component.PlaylistListItem
import com.dd3boh.outertune.ui.component.TextFieldDialog
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Composable
fun AddToPlaylistDialog(
    isVisible: Boolean,
    noSyncing: Boolean = false,
    initialTextFieldValue: String? = null,
    onGetSong: suspend (Playlist) -> List<String>, // list of song ids. Songs should be inserted to database in this function.
    onDismiss: () -> Unit,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    var playlists by remember {
        mutableStateOf(emptyList<Playlist>())
    }
    var showCreatePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var syncedPlaylist: Boolean by remember {
        mutableStateOf(false)
    }

    var showDuplicateDialog by remember {
        mutableStateOf(false)
    }
    var selectedPlaylist by remember {
        mutableStateOf<Playlist?>(null)
    }
    var songIds by remember {
        mutableStateOf<List<String>?>(null) // list is not saveable
    }
    var duplicates by remember {
        mutableStateOf(emptyList<String>())
    }

    LaunchedEffect(Unit) {
        database.editablePlaylistsByCreateDateAsc().collect {
            playlists = it.asReversed()
        }
    }

    if (isVisible) {
        ListDialog(
            onDismiss = onDismiss
        ) {
            item {
                ListItem(
                    title = stringResource(R.string.create_playlist),
                    thumbnailContent = {
                        Image(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize)
                        )
                    },
                    modifier = Modifier.clickable {
                        showCreatePlaylistDialog = true
                    }
                )
            }

            items(playlists) { playlist ->
                PlaylistListItem(
                    playlist = playlist,
                    modifier = Modifier.clickable {
                        selectedPlaylist = playlist
                        coroutineScope.launch(Dispatchers.IO) {
                            if (songIds == null) {
                                songIds = onGetSong(playlist)
                            }
                            duplicates = database.playlistDuplicates(playlist.id, songIds!!)
                            if (duplicates.isNotEmpty()) {
                                showDuplicateDialog = true
                            } else {
                                onDismiss()
                                database.addSongToPlaylist(playlist, songIds!!)
                            }
                        }
                    }
                )
            }

            item {
                Text(
                    text = "Note: Adding local songs to synced/remote playlists is unsupported. Any other combination is valid.",
                    fontSize = TextUnit(12F, TextUnitType.Sp),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }

    if (showCreatePlaylistDialog) {
        TextFieldDialog(
            icon = { Icon(imageVector = Icons.Rounded.Add, contentDescription = null) },
            title = { Text(text = stringResource(R.string.create_playlist)) },
            initialTextFieldValue = TextFieldValue(initialTextFieldValue?: ""),
            onDismiss = { showCreatePlaylistDialog = false },
            onDone = { playlistName ->
                coroutineScope.launch(Dispatchers.IO) {
                    val browseId = if (syncedPlaylist)
                        YouTube.createPlaylist(playlistName).getOrNull()
                    else null

                    database.query {
                        insert(
                            PlaylistEntity(
                                name = playlistName,
                                browseId = browseId,
                                bookmarkedAt = LocalDateTime.now(),
                                isEditable = !syncedPlaylist,
                                isLocal = !syncedPlaylist // && check that all songs are non-local
                            )
                        )
                    }
                }
            },
            extraContent = {
                // synced/unsynced toggle
                Row(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 40.dp)
                ) {
                    Column() {
                        Text(
                            text = "Sync Playlist",
                            style = MaterialTheme.typography.titleLarge,
                        )

                        Text(
                            text = "Note: This allows for syncing with YouTube Music. This is NOT changeable later. You cannot add local songs to synced playlists.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(0.7f)
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Switch(
                            enabled = !noSyncing,
                            checked = syncedPlaylist,
                            onCheckedChange = {
                                syncedPlaylist = !syncedPlaylist
                            },
                        )
                    }
                }

            }
        )
    }

    // duplicate songs warning
    if (showDuplicateDialog) {
        DefaultDialog(
            title = { Text(stringResource(R.string.duplicates)) },
            buttons = {
                TextButton(
                    onClick = {
                        showDuplicateDialog = false
                        onDismiss()
                        database.transaction {
                            addSongToPlaylist(
                                selectedPlaylist!!,
                                songIds!!.filter {
                                    !duplicates.contains(it)
                                }
                            )
                        }
                    }
                ) {
                    Text(stringResource(R.string.skip_duplicates))
                }

                TextButton(
                    onClick = {
                        showDuplicateDialog = false
                        onDismiss()
                        database.transaction {
                            addSongToPlaylist(selectedPlaylist!!, songIds!!)
                        }
                    }
                ) {
                    Text(stringResource(R.string.add_anyway))
                }

                TextButton(
                    onClick = {
                        showDuplicateDialog = false
                    }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            onDismiss = {
                showDuplicateDialog = false
            }
        ) {
            Text(
                text = if (duplicates.size == 1) {
                    stringResource(R.string.duplicates_description_single)
                } else {
                    stringResource(R.string.duplicates_description_multiple, duplicates.size)
                },
                textAlign = TextAlign.Start,
                modifier = Modifier.align(Alignment.Start)
            )
        }
    }
}
