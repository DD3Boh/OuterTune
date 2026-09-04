package com.dd3boh.outertune.ui.menu


import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Output
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalDownloadUtil
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.M3U_EXPORT_RELATIVE_PATH
import com.dd3boh.outertune.db.entities.Playlist
import com.dd3boh.outertune.db.entities.PlaylistSong
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.ExoDownloadService
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.ui.component.items.PlaylistListItem
import com.dd3boh.outertune.ui.dialog.AddToPlaylistDialog
import com.dd3boh.outertune.ui.dialog.AddToQueueDialog
import com.dd3boh.outertune.ui.dialog.DefaultDialog
import com.dd3boh.outertune.ui.dialog.TextFieldDialog
import com.dd3boh.outertune.ui.utils.getNSongsString
import com.dd3boh.outertune.utils.getDownloadState
import com.dd3boh.outertune.utils.joinByBullet
import com.dd3boh.outertune.utils.lmScannerCoroutine
import com.dd3boh.outertune.utils.reportException
import com.dd3boh.outertune.utils.scanners.fileFromUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException

@Composable
fun PlaylistMenu(
    navController: NavController,
    playlist: Playlist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val queueBoard by playerConnection.queueBoard.collectAsState()
    var songs by remember {
        mutableStateOf(emptyList<Song>())
    }

    val m3uLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri: Uri? ->
        uri?.let {
            CoroutineScope(lmScannerCoroutine).launch {
                val result = StringBuilder()
                try {
                    val relativePath = fileFromUri(context, uri)?.absolutePath?.substringBeforeLast('/') + "/"

                    if (M3U_EXPORT_RELATIVE_PATH && relativePath == null) {
                        result.append("Failed to get m3u uri path")
                    } else {
                        result.append("#EXTM3U\n")
                        songs.forEach { s ->
                            val se = s.song
                            if (!M3U_EXPORT_RELATIVE_PATH) {
                                result.append("#EXTINF:${se.duration},${s.artists.joinToString(";") { it.name }} - ${s.title}\n")
                                if (se.isLocal) {
                                    result.append(se.localPath)
                                    result.append("\n")
                                    result.append("#ot_id:${se.id}\n")
                                } else {
                                    result.append("https://youtube.com/watch?v=${se.id}\n")
                                }
                                result.append("\n")
                            } else if (se.localPath != null) {
                                // write the song path as relative to M3U. YTM songs will be ignored
                                val localPath = se.localPath
                                val targetPath = relativePath

                                // playlist is in the parent or grandparent of song
                                if (localPath.contains(targetPath)) {
                                    result.append(localPath.replace(targetPath, ""))
                                } else {
                                    // find the common folder, then replace the common dir with the correct number of ../
                                    val lpSplit = localPath.split('/').toMutableList()
                                    val tpSplit = targetPath.trimEnd { it == '/' }.split('/').toMutableList()
                                    val tpCommon = ArrayList<String>()
                                    var commonDepth = 0
                                    while (!lpSplit.isEmpty() && !tpSplit.isEmpty() && lpSplit.first() == tpSplit.first()) {
                                        commonDepth++
                                        lpSplit.removeAt(0)
                                        tpCommon.add(tpSplit.removeAt(0))
                                    }

                                    result.append(
                                        localPath.replace(
                                            tpCommon.joinToString("/") + "/",
                                            "../".repeat(lpSplit.size)
                                        )
                                    )
                                }
                                result.append("\n")
                            }
                        }
                    }
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(result.toString().toByteArray(Charsets.UTF_8))
                    }
                } catch (e: IOException) {
                    reportException(e)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        database.playlistSongs(playlist.id).collect {
            songs = it.map(PlaylistSong::song)
        }
    }

    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    val editable: Boolean = playlist.playlist.isLocal

    var showEditDialog by remember {
        mutableStateOf(false)
    }
    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }
    var showDeletePlaylistDialog by remember {
        mutableStateOf(false)
    }
    var showChooseQueueDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(songs) {
        val songs = songs.filterNot { it.song.isLocal }
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState = getDownloadState(songs.map { downloads[it.id] })
        }
    }


    PlaylistListItem(
        playlist = playlist,
        subtitle = joinByBullet(
            getNSongsString(playlist.songCount, playlist.downloadCount),
            playlist.playlist.path.trimEnd { it == '/' }),
        showBadges = true
    )

    HorizontalDivider()

    GridMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        GridMenuItem(
            icon = Icons.Rounded.PlayArrow,
            title = R.string.play
        ) {
            onDismiss()
            playerConnection.playQueue(
                ListQueue(
                    title = playlist.playlist.name,
                    items = songs.map { it.toMediaMetadata() },
                )
            )
        }

        GridMenuItem(
            icon = Icons.Rounded.Shuffle,
            title = R.string.shuffle
        ) {
            onDismiss()
            playerConnection.playQueue(
                ListQueue(
                    title = playlist.playlist.name,
                    items = songs.map { it.toMediaMetadata() },
                    startShuffled = true,
                )
            )
        }

        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
            title = R.string.play_next
        ) {
            coroutineScope.launch {
                playerConnection.enqueueNext(songs.map { it.toMediaItem() })
            }
            onDismiss()
        }

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

        if (songs.fastAny { !it.song.isLocal }) {
            DownloadGridMenu(
                state = downloadState,
                onDownload = {
                    val _songs = songs.filterNot { it.song.isLocal }.map { it.toMediaMetadata() }
                    downloadUtil.download(_songs)
                },
                onRemoveDownload = {
                    showRemoveDownloadDialog = true
                }
            )
        }

        if (editable) {
            GridMenuItem(
                icon = Icons.Rounded.Edit,
                title = R.string.edit
            ) {
                showEditDialog = true
            }
        }
        GridMenuItem(
            icon = Icons.Rounded.PlaylistRemove,
            title = R.string.delete
        ) {
            showDeletePlaylistDialog = true
        }

        // TODO: m3u playlist share
//        GridMenuItem(
//                icon = Icons.Rounded.Share,
//                title = R.string.share
//        ) {
//            val intent = Intent().apply {
//                action = Intent.ACTION_SEND
//                type = "text/plain"
//                putExtra(Intent.EXTRA_TEXT, shareLink)
//            }
//            context.startActivity(Intent.createChooser(intent, null))
//            onDismiss()
//        }
        GridMenuItem(
            icon = Icons.Rounded.Output,
            title = R.string.m3u_export
        ) {
            m3uLauncher.launch("${playlist.title}.m3u")
        }
    }

    if (showEditDialog) {
        TextFieldDialog(
            icon = { Icon(imageVector = Icons.Rounded.Edit, contentDescription = null) },
            title = { Text(text = stringResource(R.string.edit_playlist)) },
            onDismiss = { showEditDialog = false },
            initialTextFieldValue = TextFieldValue(
                playlist.playlist.path + playlist.playlist.name,
                TextRange(playlist.playlist.name.length)
            ),
            onDone = { playlistName ->
                onDismiss()
                val playlistName = playlistName.trimStart { it == '/' }
                val name = playlistName.substringAfterLast("/")
                val path = if (playlistName.contains("/")) {
                    "/" + playlistName.substringBeforeLast("/").trim { it == '/' } + "/"
                } else {
                    "/"
                }
                database.query {
                    update(playlist.playlist.copy(name = name, path = path))
                }
            }
        )
    }

    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, playlist.playlist.name),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.song.id,
                                false
                            )
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.delete_playlist_confirm, playlist.playlist.name),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        onDismiss()
                        database.query {
                            delete(playlist.playlist)
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    if (showChooseQueueDialog) {
        AddToQueueDialog(

            onAdd = { queueName ->
                val q = queueBoard.addQueue(
                    queueName, songs.map { it.toMediaMetadata() },
                    forceInsert = true, delta = false
                )
                q?.let {
                    queueBoard.setCurrQueue(it)
                }
            },
            onDismiss = {
                showChooseQueueDialog = false
            }
        )
    }

    if (showChoosePlaylistDialog) {
        AddToPlaylistDialog(
            navController = navController,
            songIds = songs.map { it.id },
            onPreAdd = { playlist ->
                songs.map { it.id }
            },
            onDismiss = { showChoosePlaylistDialog = false }
        )
    }
}
