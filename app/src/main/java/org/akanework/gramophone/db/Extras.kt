package org.akanework.gramophone.db

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

fun genMediaItem(

    chromaprint: String?,
    title: String?,
    artist: String?,
    album: String?,

    uri: Uri?,
    id: String? = null,
): MediaItem {
    if (chromaprint == null && title == null) throw IllegalArgumentException("chromaprint or title must be defined")
    val metadata = MediaMetadata.Builder()
        .setTitle(title.takeIf { !it.isNullOrBlank() })
        .setArtist(artist.takeIf { !it.isNullOrBlank() })
        .setAlbumTitle(album.takeIf { !it.isNullOrBlank() })

    if (chromaprint != null) {
        metadata.setExtras(Bundle().apply { putString("chromaprint", chromaprint) })
    }

    val mediaItem =  MediaItem.Builder()
        .setUri(uri)
        .setMediaMetadata(metadata.build())

    id?.let {
        mediaItem.setMediaId(it)
    }

    return mediaItem.build()
}