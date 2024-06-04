package com.dd3boh.outertune.models

import androidx.compose.runtime.Immutable
import com.zionhuang.innertube.models.SongItem
import com.dd3boh.outertune.db.entities.*
import com.dd3boh.outertune.ui.utils.resize
import java.io.Serializable
import java.time.LocalDateTime

@Immutable
data class MediaMetadata(
    val id: String,
    val title: String,
    val artists: List<Artist>,
    val duration: Int,
    val thumbnailUrl: String? = null,
    val album: Album? = null,
    val genre: List<Genre>?,
    val year: Int? = null,
    val date: LocalDateTime? = null, // ID3 tag property
    val dateModified: LocalDateTime? = null, // file property
    val dateAdded: LocalDateTime? = null, // aka inLibrary
    val setVideoId: String? = null,
    val isLocal: Boolean = false,
    val localPath: String? = null,
) : Serializable {
    data class Artist(
        val id: String?,
        val name: String,
        val isLocal: Boolean = false,
    ) : Serializable

    data class Album(
        val id: String,
        val title: String,
        val isLocal: Boolean = false,
    ) : Serializable

    data class Genre(
        val id: String?,
        val title: String,
        val isLocal: Boolean = false,
    ) : Serializable

    fun toSongEntity() = SongEntity(
        id = id,
        title = title,
        duration = duration,
        thumbnailUrl = thumbnailUrl,
        albumId = album?.id,
        albumName = album?.title,
        year = year,
        date = date,
        dateModified = dateModified,
        isLocal = isLocal,
        inLibrary = if (isLocal) LocalDateTime.now() else null,
        localPath = localPath
    )
}

fun Song.toMediaMetadata() = MediaMetadata(
    id = song.id,
    title = song.title,
    artists = artists.map {
        MediaMetadata.Artist(
            id = it.id,
            name = it.name,
            isLocal = it.isLocal
        )
    },
    duration = song.duration,
    thumbnailUrl = song.thumbnailUrl,
    album = album?.let {
        MediaMetadata.Album(
            id = it.id,
            title = it.title,
            isLocal = it.isLocal
        )
    } ?: song.albumId?.let { albumId ->
        MediaMetadata.Album(
            id = albumId,
            title = song.albumName.orEmpty(),
            // no possible local albums somehow
        )
    },
    genre = genre?.map {
        MediaMetadata.Genre(
            id = it.id,
            title = it.title,
            isLocal = it.isLocal
        )
    },
    year = song.year,
    date = song.date,
    dateModified = song.dateModified,
    dateAdded = song.inLibrary,
    isLocal = song.isLocal,
    localPath = song.localPath
)

fun SongItem.toMediaMetadata() = MediaMetadata(
    id = id,
    title = title,
    artists = artists.map {
        MediaMetadata.Artist(
            id = it.id,
            name = it.name
        )
    },
    duration = duration ?: -1,
    thumbnailUrl = thumbnail.resize(544, 544),
    album = album?.let {
        MediaMetadata.Album(
            id = it.id,
            title = it.name
        )
    },
    setVideoId = setVideoId
)
