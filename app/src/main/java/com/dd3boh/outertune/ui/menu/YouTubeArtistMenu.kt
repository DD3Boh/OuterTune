package com.dd3boh.outertune.ui.menu

import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zionhuang.innertube.models.ArtistItem
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.db.entities.ArtistEntity
import com.dd3boh.outertune.playback.queues.YouTubeQueue
import com.dd3boh.outertune.ui.component.GridMenu
import com.dd3boh.outertune.ui.component.GridMenuItem
import com.dd3boh.outertune.ui.component.YouTubeListItem
import java.time.LocalDateTime

@Composable
fun YouTubeArtistMenu(
    artist: ArtistItem,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val libraryArtist by database.artist(artist.id).collectAsState(initial = null)

    YouTubeListItem(
        item = artist,
        trailingContent = {
            IconButton(
                onClick = {
                    database.query {
                        val libraryArtistLocal = libraryArtist
                        if (libraryArtistLocal != null) {
                            update(libraryArtistLocal.artist.toggleLike())
                        } else {
                            insert(
                                ArtistEntity(
                                    id = artist.id,
                                    name = artist.title,
                                    channelId = artist.channelId,
                                    thumbnailUrl = artist.thumbnail,
                                ).toggleLike()
                            )
                        }
                    }
                }
            ) {
                Icon(
                    painter = painterResource(if (libraryArtist?.artist?.bookmarkedAt != null) R.drawable.favorite else R.drawable.favorite_border),
                    tint = if (libraryArtist?.artist?.bookmarkedAt != null) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    contentDescription = null
                )
            }

        }
    )

    Divider()

    GridMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        artist.radioEndpoint?.let { watchEndpoint ->
            GridMenuItem(
                icon = R.drawable.radio,
                title = R.string.start_radio
            ) {
                playerConnection.playQueue(YouTubeQueue(watchEndpoint))
                onDismiss()
            }
        }
        artist.shuffleEndpoint?.let { watchEndpoint ->
            GridMenuItem(
                icon = R.drawable.shuffle,
                title = R.string.shuffle
            ) {
                playerConnection.playQueue(YouTubeQueue(watchEndpoint))
                onDismiss()
            }
        }
        GridMenuItem(
            icon = R.drawable.share,
            title = R.string.share
        ) {
            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, artist.shareLink)
            }
            context.startActivity(Intent.createChooser(intent, null))
            onDismiss()
        }
    }
}
