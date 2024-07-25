package com.dd3boh.outertune.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditOff
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalDownloadUtil
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.GridThumbnailHeight
import com.dd3boh.outertune.constants.ListItemHeight
import com.dd3boh.outertune.constants.ListThumbnailSize
import com.dd3boh.outertune.constants.ThumbnailCornerRadius
import com.dd3boh.outertune.db.entities.Album
import com.dd3boh.outertune.db.entities.Artist
import com.dd3boh.outertune.db.entities.Playlist
import com.dd3boh.outertune.db.entities.PlaylistEntity
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.models.DirectoryTree
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.models.MultiQueueObject
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.ui.menu.FolderMenu
import com.dd3boh.outertune.ui.theme.extractThemeColor
import com.dd3boh.outertune.ui.utils.getLocalThumbnail
import com.dd3boh.outertune.utils.joinByBullet
import com.dd3boh.outertune.utils.makeTimeString
import com.dd3boh.outertune.utils.reportException
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.YTItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
inline fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    noinline subtitle: (@Composable RowScope.() -> Unit)? = null,
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (isActive) {
            modifier // playing highlight
                .height(ListItemHeight)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    color = // selected active
                    if (isSelected == true) MaterialTheme.colorScheme.primary.copy( alpha = 0.4f)
                    else MaterialTheme.colorScheme.secondaryContainer
                )
            } else if (isSelected == true) {
                modifier // inactive selected
                    .height(ListItemHeight)
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color = MaterialTheme.colorScheme.inversePrimary.copy( alpha = 0.4f))
            }
            else {
                modifier // default
                    .height(ListItemHeight)
                    .padding(horizontal = 8.dp)
        }
    ) {
        Box(
            modifier = Modifier.padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            thumbnailContent()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    subtitle()
                }
            }
        }

        trailingContent()
    }
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
    isLocalSong: Boolean? = null,
) = ListItem(
    title = title,
    subtitle = {
        badges()

        // local song indicator
        if (isLocalSong == true) {
            Icon(
                Icons.Rounded.FolderCopy,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }

        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    },
    thumbnailContent = thumbnailContent,
    trailingContent = trailingContent,
    modifier = modifier,
    isSelected = isSelected,
    isActive = isActive
)

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailShape: Shape,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
) {
    Column(
        modifier = if (fillMaxWidth) {
            modifier
                .padding(12.dp)
                .fillMaxWidth()
        } else {
            modifier
                .padding(12.dp)
                .width(GridThumbnailHeight * thumbnailRatio)
        }
    ) {
        BoxWithConstraints(
            modifier = if (fillMaxWidth) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.height(GridThumbnailHeight)
            }
                .aspectRatio(thumbnailRatio)
                .clip(thumbnailShape)
        ) {
            thumbnailContent()
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            badges()

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SongListItem(
    song: Song,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    isSelected: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {
        if (showLikedIcon && song.song.liked) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
        if (showInLibraryIcon && song.song.inLibrary != null) {
            Icon(
                painter = painterResource(R.drawable.library_add_check),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
        if (showDownloadIcon) {
            val download by LocalDownloadUtil.current.getDownload(song.id).collectAsState(initial = null)
            when (download?.state) {
                STATE_COMPLETED -> Icon(
                    imageVector = Icons.Rounded.OfflinePin,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 2.dp)
                )

                STATE_QUEUED, STATE_DOWNLOADING -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 2.dp)
                )

                else -> {}
            }
        }

        // local song indicator
        if (song.song.isLocal) {
            Icon(
                Icons.Rounded.FolderCopy,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = song.song.title,
    subtitle = joinByBullet(
        song.artists.joinToString { it.name },
        makeTimeString(song.song.duration * 1000L)
    ),
    badges = badges,
    thumbnailContent = {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(ListThumbnailSize)
        ) {
            if (albumIndex != null) {
                AnimatedVisibility(
                    visible = !isActive,
                    enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                    exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut()
                ) {

                    if (isSelected) {
                        Icon(
                            Icons.Rounded.Done,
                            modifier = Modifier.align(Alignment.Center),
                            contentDescription = null
                        )
                    }else {
                        Text(
                            text = albumIndex.toString(),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            } else {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(1000f)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius))
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.Rounded.Done,
                            modifier = Modifier.align(Alignment.Center),
                            contentDescription = null
                        )
                    }
                }

                if (song.song.isLocal) {
                    // local thumbnail arts
                    AsyncLocalImage(
                        image = { getLocalThumbnail(song.song.localPath, true) },
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(ThumbnailCornerRadius))
                            .aspectRatio(ratio = 1f)
                    )
                } else {
                    // YTM thumbnail arts
                    AsyncImage(
                        model = song.song.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    )
                }
            }

            PlayingIndicatorBox(
                isActive = isActive,
                playWhenReady = isPlaying,
                color = if (albumIndex != null) MaterialTheme.colorScheme.onBackground else Color.White,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = if (albumIndex != null) Color.Transparent else Color.Black.copy(
                            alpha = 0.4f
                        ),
                        shape = RoundedCornerShape(ThumbnailCornerRadius)
                    )
            )
        }
    },
    trailingContent = trailingContent,
    modifier = modifier,
    isSelected = isSelected,
    isActive = isActive
)
@Composable
fun SongFolderItem(
    folderTitle: String,
    modifier: Modifier = Modifier,
) = ListItem(title = folderTitle, thumbnailContent = {
        Icon(
            Icons.Rounded.Folder,
            contentDescription = null,
            modifier = modifier.size(48.dp)
        )
    },
    modifier = modifier
)

@Composable
fun SongFolderItem(
    folderTitle: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) = ListItem(title = folderTitle,
    subtitle = subtitle,
    thumbnailContent = {
        Icon(
            Icons.Rounded.Folder,
            contentDescription = null,
            modifier = modifier.size(48.dp)
        )
    },
    modifier = modifier
)

@Composable
fun SongFolderItem(
    folder: DirectoryTree,
    modifier: Modifier = Modifier,
    folderTitle: String? = null,
    menuState: MenuState,
    navController: NavController,
    subtitle: String
) = ListItem(title = folderTitle ?: folder.currentDir,
    subtitle = subtitle,
    thumbnailContent = {
    Icon(
        Icons.Rounded.Folder,
        contentDescription = null,
        modifier = modifier.size(48.dp)
    )
},
    trailingContent = {
        androidx.compose.material3.IconButton(
            onClick = {
                menuState.show {
                    FolderMenu(
                        folder = folder,
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
    modifier = modifier
)

@Composable
fun ArtistListItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }

        // assume if they have a non local artist ID, they are not local
        if (artist.artist.isLocalArtist) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
    },
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = artist.artist.name,
    subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            model = artist.artist.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(ListThumbnailSize)
                .clip(CircleShape)
        )
    },
    trailingContent = trailingContent,
    modifier = modifier
)

@Composable
fun ArtistGridItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }

        // assume if they have a non local artist ID, they are not local
        if (artist.artist.isLocalArtist) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
    },
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = artist.artist.name,
    subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            model = artist.artist.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    },
    thumbnailShape = CircleShape,
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun AlbumListItem(
    album: Album,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val downloadUtil = LocalDownloadUtil.current
        var songs by remember {
            mutableStateOf(emptyList<Song>())
        }

        LaunchedEffect(Unit) {
            database.albumSongs(album.id).collect {
                songs = it
            }
        }

        var downloadState by remember {
            mutableIntStateOf(Download.STATE_STOPPED)
        }

        LaunchedEffect(songs) {
            if (songs.isEmpty()) return@LaunchedEffect
            downloadUtil.downloads.collect { downloads ->
                downloadState =
                    if (songs.all { downloads[it.id]?.state == STATE_COMPLETED })
                        STATE_COMPLETED
                    else if (songs.all {
                            downloads[it.id]?.state == STATE_QUEUED
                                    || downloads[it.id]?.state == STATE_DOWNLOADING
                                    || downloads[it.id]?.state == STATE_COMPLETED
                        })
                        STATE_DOWNLOADING
                    else
                        Download.STATE_STOPPED
            }
        }

        if (showLikedIcon && album.album.bookmarkedAt != null) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }

        when (downloadState) {
            STATE_COMPLETED -> Icon(
                imageVector = Icons.Rounded.OfflinePin,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )

            STATE_DOWNLOADING -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 2.dp)
            )

            else -> {}
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = album.album.title,
    subtitle = joinByBullet(
        album.artists.joinToString { it.name },
        album.album.songCount.takeIf { it != 0 }?.let { songCount ->
            pluralStringResource(R.plurals.n_song, songCount, songCount)
        },
        album.album.year?.toString()
    ),
    badges = badges,
    thumbnailContent = {
        val database = LocalDatabase.current
        val coroutineScope = rememberCoroutineScope()

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(album.album.thumbnailUrl)
                .allowHardware(false)
                .build(),
            contentDescription = null,
            onState = { state ->
                if (album.album.themeColor == null && state is AsyncImagePainter.State.Success) {
                    coroutineScope.launch(Dispatchers.IO) {
                        state.result.drawable.toBitmapOrNull()?.extractThemeColor()?.toArgb()?.let { color ->
                            database.query {
                                update(album.album.copy(themeColor = color))
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .size(ListThumbnailSize)
                .clip(RoundedCornerShape(ThumbnailCornerRadius))
        )

        PlayingIndicatorBox(
            isActive = isActive,
            playWhenReady = isPlaying,
            modifier = Modifier
                .size(ListThumbnailSize)
                .background(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(ThumbnailCornerRadius)
                )
        )
    },
    trailingContent = trailingContent,
    modifier = modifier
)

@Composable
fun AlbumGridItem(
    album: Album,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val downloadUtil = LocalDownloadUtil.current
        var songs by remember {
            mutableStateOf(emptyList<Song>())
        }

        LaunchedEffect(Unit) {
            database.albumSongs(album.id).collect {
                songs = it
            }
        }

        var downloadState by remember {
            mutableStateOf(Download.STATE_STOPPED)
        }

        LaunchedEffect(songs) {
            if (songs.isEmpty()) return@LaunchedEffect
            downloadUtil.downloads.collect { downloads ->
                downloadState =
                    if (songs.all { downloads[it.id]?.state == STATE_COMPLETED })
                        STATE_COMPLETED
                    else if (songs.all {
                            downloads[it.id]?.state == STATE_QUEUED
                                    || downloads[it.id]?.state == STATE_DOWNLOADING
                                    || downloads[it.id]?.state == STATE_COMPLETED
                        })
                        STATE_DOWNLOADING
                    else
                        Download.STATE_STOPPED
            }
        }

        if (album.album.bookmarkedAt != null) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }

        when (downloadState) {
            STATE_COMPLETED -> Icon(
                imageVector = Icons.Rounded.OfflinePin,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )

            STATE_DOWNLOADING -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 2.dp)
            )

            else -> {}
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = album.album.title,
    subtitle = album.artists.joinToString { it.name },
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            model = album.album.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(500)),
            exit = fadeOut(tween(500))
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(ThumbnailCornerRadius)
                    )
            ) {
                if (isPlaying) {
                    PlayingIndicator(
                        color = Color.White,
                        modifier = Modifier.height(24.dp)
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !isActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        ) {
            val database = LocalDatabase.current
            val playerConnection = LocalPlayerConnection.current ?: return@AnimatedVisibility

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable {
                        coroutineScope.launch {
                            database
                                .albumWithSongs(album.id)
                                .first()
                                ?.songs
                                ?.map { it.toMediaMetadata() }
                                ?.let {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = album.album.title,
                                            items = it,
                                            playlistId = album.album.playlistId
                                        )
                                    )
                                }
                        }
                    }
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    },
    thumbnailShape = RoundedCornerShape(ThumbnailCornerRadius),
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun AutoPlaylistListItem(
    playlist: PlaylistEntity,
    thumbnail: ImageVector,
    modifier: Modifier = Modifier,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = playlist.name,
    subtitle = stringResource(id = R.string.auto_playlist),
    thumbnailContent = {
        Box(
            modifier = Modifier
                .size(ListThumbnailSize)
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                    shape = RoundedCornerShape(ThumbnailCornerRadius))
        ) {
            Icon(
                imageVector = thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .size(ListThumbnailSize / 2 + 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
                    .align(Alignment.Center)
            )
        }
    },
    trailingContent = trailingContent,
    modifier = modifier
)

@Composable
fun AutoPlaylistGridItem(
    playlist: PlaylistEntity,
    thumbnail: ImageVector,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = { },
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = playlist.name,
    subtitle = stringResource(id = R.string.auto_playlist),
    badges = badges,
    thumbnailContent = {
        val width = maxWidth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
        ) {
            Icon(
                imageVector = thumbnail,
                contentDescription = null,
                tint = LocalContentColor.current.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(width / 2 + 10.dp)
                    .align(Alignment.Center)
            )
        }
    },
    thumbnailShape = RoundedCornerShape(ThumbnailCornerRadius),
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun PlaylistListItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = playlist.playlist.name,
    subtitle =
        if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null)
            pluralStringResource(R.plurals.n_song, playlist.playlist.remoteSongCount, playlist.playlist.remoteSongCount)
        else
            pluralStringResource(R.plurals.n_song, playlist.songCount, playlist.songCount),
    badges = {
         Icon(
             imageVector = if (playlist.playlist.isEditable) Icons.Rounded.Edit else Icons.Rounded.EditOff,
             contentDescription = null,
             modifier = Modifier
                 .size(18.dp)
                 .padding(end = 2.dp)
         )

        if (playlist.playlist.isLocal) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
    },
    thumbnailContent = {
        when (playlist.thumbnails.size) {
            0 -> Icon(
                painter = painterResource(R.drawable.queue_music),
                contentDescription = null,
                modifier = Modifier.size(ListThumbnailSize)
            )

            1 -> if (playlist.thumbnails[0].startsWith("/storage")) {
                AsyncImage(
                    model = playlist.thumbnails[0],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(ListThumbnailSize)
                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                )
            } else {
                AsyncImage(
                    model = playlist.thumbnails[0],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(ListThumbnailSize)
                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                )
            }

            else -> Box(
                modifier = Modifier
                    .size(ListThumbnailSize)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
            ) {
                listOf(
                    Alignment.TopStart,
                    Alignment.TopEnd,
                    Alignment.BottomStart,
                    Alignment.BottomEnd
                ).fastForEachIndexed { index, alignment ->
                    if (playlist.thumbnails.getOrNull(index)?.startsWith("/storage") == true) {
                        AsyncLocalImage(
                            image = { getLocalThumbnail(playlist.thumbnails[index], true) },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(alignment)
                                .size(ListThumbnailSize / 2)
                        )
                    } else {
                        AsyncImage(
                            model = playlist.thumbnails.getOrNull(index),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(alignment)
                                .size(ListThumbnailSize / 2)
                        )
                    }
                }
            }
        }
    },
    trailingContent = trailingContent,
    modifier = modifier
)

@Composable
fun PlaylistGridItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = { },
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = playlist.playlist.name,
    subtitle =
        if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null)
            pluralStringResource(R.plurals.n_song, playlist.playlist.remoteSongCount, playlist.playlist.remoteSongCount)
        else
            pluralStringResource(R.plurals.n_song, playlist.songCount, playlist.songCount),
    badges = badges,
    thumbnailContent = {
        val width = maxWidth
        when (playlist.thumbnails.size) {
            0 -> Icon(
                painter = painterResource(R.drawable.queue_music),
                contentDescription = null,
                tint = LocalContentColor.current.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(width / 2)
                    .align(Alignment.Center)
            )

            1 -> AsyncImage(
                model = playlist.thumbnails[0],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
            )

            else -> Box(
                modifier = Modifier
                    .size(width)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
            ) {
                listOf(
                    Alignment.TopStart,
                    Alignment.TopEnd,
                    Alignment.BottomStart,
                    Alignment.BottomEnd
                ).fastForEachIndexed { index, alignment ->
                    AsyncImage(
                        model = playlist.thumbnails.getOrNull(index),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .align(alignment)
                            .size(width / 2)
                    )
                }
            }
        }
    },
    thumbnailShape = RoundedCornerShape(ThumbnailCornerRadius),
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun MediaMetadataListItem(
    mediaMetadata: MediaMetadata,
    modifier: Modifier,
    isActive: Boolean = false,
    isSelected: Boolean? = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = mediaMetadata.title,
    subtitle = joinByBullet(
        mediaMetadata.artists.joinToString { it.name },
        makeTimeString(mediaMetadata.duration * 1000L)
    ),
    thumbnailContent = {
        if (mediaMetadata.isLocal) {
            // local thumbnail arts
            AsyncLocalImage(
                image = { getLocalThumbnail(mediaMetadata.localPath, true) },
                contentDescription = null,
                modifier = Modifier
                    .size(ListThumbnailSize)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    .aspectRatio(ratio = 1f)
            )
        } else {
            // YTM thumbnail arts
            AsyncImage(
                model = mediaMetadata.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(ListThumbnailSize)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
            )
        }

        PlayingIndicatorBox(
            isActive = isActive,
            playWhenReady = isPlaying,
            modifier = Modifier
                .size(ListThumbnailSize)
                .background(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(ThumbnailCornerRadius)
                )
        )
    },
    trailingContent = trailingContent,
    modifier = modifier,
    isSelected = isSelected,
    isActive = isActive,
    isLocalSong = mediaMetadata.isLocal
)

@Composable
fun QueueListItem(
    queue: MultiQueueObject,
    modifier: Modifier = Modifier,
    number: Int? = null,
) = ListItem(
    title = (if (number != null) "$number. " else "") + (queue.title ?: "Queue"),
    subtitle = joinByBullet(
        pluralStringResource(R.plurals.n_song, queue.getCurrentQueueShuffled().size, queue.getCurrentQueueShuffled().size),
        makeTimeString(queue.getDuration() * 1000L)
    ),
    thumbnailContent = {
        Icon(
            Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
    },
    modifier = modifier
)

@Composable
fun YouTubeListItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val song by database.song(item.id).collectAsState(initial = null)
        val album by database.album(item.id).collectAsState(initial = null)

        if (item is SongItem && song?.song?.liked == true ||
            item is AlbumItem && album?.album?.bookmarkedAt != null
        ) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
        if (item.explicit) {
            Icon(
                imageVector = Icons.Rounded.Explicit,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
        if (item is SongItem && song?.song?.inLibrary != null) {
            Icon(
                painter = painterResource(R.drawable.library_add_check),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
        if (item is SongItem) {
            val downloads by LocalDownloadUtil.current.downloads.collectAsState()
            when (downloads[item.id]?.state) {
                STATE_COMPLETED -> Icon(
                    imageVector = Icons.Rounded.OfflinePin,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 2.dp)
                )

                STATE_QUEUED, STATE_DOWNLOADING -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 2.dp)
                )

                else -> {}
            }
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = item.title,
    subtitle = when (item) {
        is SongItem -> joinByBullet(item.artists.joinToString { it.name }, makeTimeString(item.duration?.times(1000L)))
        is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
        is ArtistItem -> null
        is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
    },
    badges = badges,
    thumbnailContent = {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(ListThumbnailSize)
        ) {
            val thumbnailShape = if (item is ArtistItem) CircleShape else RoundedCornerShape(ThumbnailCornerRadius)
            if (albumIndex != null) {
                AnimatedVisibility(
                    visible = !isActive,
                    enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                    exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut()
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Done,
                            modifier = Modifier.align(Alignment.Center),
                            contentDescription = null
                        )
                    } else {
                        Text(
                            text = albumIndex.toString(),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            } else {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(1000f)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius))
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Done,
                            modifier = Modifier.align(Alignment.Center),
                            contentDescription = null
                        )
                    }
                }
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(thumbnailShape)
                )
            }

            PlayingIndicatorBox(
                isActive = isActive,
                playWhenReady = isPlaying,
                color = if (albumIndex != null) MaterialTheme.colorScheme.onBackground else Color.White,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = if (albumIndex != null) Color.Transparent else Color.Black.copy(
                            alpha = 0.4f
                        ),
                        shape = thumbnailShape
                    )
            )
        }
    },
    trailingContent = trailingContent,
    modifier = modifier,
    isActive = isActive
)

@Composable
fun YouTubeGridItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope? = null,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val song by database.song(item.id).collectAsState(initial = null)
        val album by database.album(item.id).collectAsState(initial = null)

        if (item is SongItem && song?.song?.liked == true ||
            item is AlbumItem && album?.album?.bookmarkedAt != null
        ) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
        if (item.explicit) {
            Icon(
                imageVector = Icons.Rounded.Explicit,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
        if (item is SongItem && song?.song?.inLibrary != null) {
            Icon(
                painter = painterResource(R.drawable.library_add_check),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
        if (item is SongItem) {
            val downloads by LocalDownloadUtil.current.downloads.collectAsState()
            when (downloads[item.id]?.state) {
                STATE_COMPLETED -> Icon(
                    imageVector = Icons.Rounded.OfflinePin,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 2.dp)
                )

                STATE_DOWNLOADING -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 2.dp)
                )

                else -> {}
            }
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) {
    val thumbnailShape = if (item is ArtistItem) CircleShape else RoundedCornerShape(ThumbnailCornerRadius)
    val thumbnailRatio = if (item is SongItem) 16f / 9 else 1f

    Column(
        modifier = if (fillMaxWidth) {
            modifier
                .padding(12.dp)
                .fillMaxWidth()
        } else {
            modifier
                .padding(12.dp)
                .width(GridThumbnailHeight * thumbnailRatio)
        }
    ) {
        Box(
            modifier = if (fillMaxWidth) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.height(GridThumbnailHeight)
            }
                .aspectRatio(thumbnailRatio)
                .clip(thumbnailShape)
        ) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = isActive,
                enter = fadeIn(tween(500)),
                exit = fadeOut(tween(500))
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = Color.Black.copy(alpha = 0.4f),
                            shape = thumbnailShape
                        )
                ) {
                    if (isPlaying) {
                        PlayingIndicator(
                            color = Color.White,
                            modifier = Modifier.height(24.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = item is AlbumItem && !isActive,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                val database = LocalDatabase.current
                val playerConnection = LocalPlayerConnection.current ?: return@AnimatedVisibility

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable {
                            coroutineScope?.launch(Dispatchers.IO) {
                                var songs = database
                                    .albumWithSongs(item.id)
                                    .first()?.songs?.map { it.toMediaMetadata() }
                                if (songs == null) {
                                    YouTube
                                        .album(item.id)
                                        .onSuccess { albumPage ->
                                            database.transaction {
                                                insert(albumPage)
                                            }
                                            songs = albumPage.songs.map { it.toMediaMetadata() }
                                        }
                                        .onFailure {
                                            reportException(it)
                                        }
                                }
                                songs?.let {
                                    withContext(Dispatchers.Main) {
                                        val playlistId: String? = when (item) {
                                            is AlbumItem -> item.playlistId
                                            is PlaylistItem -> item.id

                                            else -> null
                                        }

                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = item.title,
                                                items = it,
                                                playlistId = playlistId
                                            )
                                        )
                                    }
                                }
                            }
                        }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (item is ArtistItem) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            badges()

            val subtitle = when (item) {
                is SongItem -> joinByBullet(item.artists.joinToString { it.name }, makeTimeString(item.duration?.times(1000L)))
                is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
                is ArtistItem -> null
                is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
            }

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun YouTubeCardItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(60.dp)
            .width(screenWidthDp.dp / 2)
            .padding(6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(ListThumbnailSize)
            ) {
                val thumbnailShape = RoundedCornerShape(ThumbnailCornerRadius)
                val thumbnailRatio = 1f

                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(thumbnailRatio)
                        .clip(thumbnailShape)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(500)),
            exit = fadeOut(tween(500))
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(8.dp).background(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(ThumbnailCornerRadius)
                ).size(20.dp)
            ) {
                PlayingIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(18.dp),
                    barWidth = 3.dp,
                    isPlaying = isPlaying
                )
            }
        }
    }
}

