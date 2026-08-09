package org.akanework.gramophone.db.entities

import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.Entity
import androidx.room.PrimaryKey
import uk.akane.libphonograph.items.EXTRA_ADD_DATE
import uk.akane.libphonograph.items.EXTRA_ALBUM_ID
import uk.akane.libphonograph.items.EXTRA_ALBUM_YEAR
import uk.akane.libphonograph.items.EXTRA_ARTIST_ID
import uk.akane.libphonograph.items.EXTRA_AUTHOR
import uk.akane.libphonograph.items.EXTRA_CD_TRACK_NUMBER
import uk.akane.libphonograph.items.EXTRA_FILE
import uk.akane.libphonograph.items.EXTRA_HD_ARTWORK_URI
import uk.akane.libphonograph.items.EXTRA_MODIFIED_DATE
import uk.akane.libphonograph.items.addDate
import uk.akane.libphonograph.items.albumId
import uk.akane.libphonograph.items.albumYear
import uk.akane.libphonograph.items.artistId
import uk.akane.libphonograph.items.cdTrackNumber
import uk.akane.libphonograph.items.modifiedDate
import androidx.core.net.toUri

@Immutable
@Entity(tableName = "tag_cache")
data class SongTagEntity(
    @PrimaryKey val mediaId: String,
    val uri: String?,
    val path: String?,
    val mimeType: String?,
    val title: String?,
    val artist: String?,
    val albumTitle: String?,
    val albumArtist: String?,
    val artworkUri: String?,
    val hdArtworkUri: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val recordingYear: Int?,
    val recordingMonth: Int?,
    val recordingDay: Int?,
    val releaseYear: Int?,
    val albumYear: Long?,
    val cdTrackNumber: String?,
    val isBrowsable: Boolean?,
    val isPlayable: Boolean?,
    val addDate: Long?,
    val modifiedDate: Long?,
    val durationMs: Long?,
    val writer: String?,
    val compilation: String?,
    val composer: String?,
    val genre: String?,
    val author: String?,
    val artistId: Long?,
    val albumId: Long?,
)


fun SongTagEntity.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setUri(uri)
        .setMediaId(mediaId)
        .setMimeType(mimeType)
        .setMediaMetadata(
            MediaMetadata
                .Builder()
                .setTitle(title)
                .setArtist(artist)
                .setWriter(writer)
                .setComposer(composer)
                .setGenre(genre)
                .setCompilation(compilation)
                .setRecordingDay(recordingDay)
                .setRecordingMonth(recordingMonth)
                .setAlbumTitle(albumTitle)
                .setAlbumArtist(albumArtist)
                .setArtworkUri(artworkUri?.toUri())
                .setTrackNumber(trackNumber)
                .setDiscNumber(discNumber)
                .setRecordingYear(recordingYear)
                .setReleaseYear(releaseYear)
                .setDurationMs(durationMs)
                .setIsBrowsable(isBrowsable)
                .setIsPlayable(isPlayable)
                .setExtras(Bundle().apply {
                    if (addDate != null) {
                        putLong(EXTRA_ADD_DATE, addDate)
                    }
                    if (artistId != null) {
                        putLong(EXTRA_ARTIST_ID, artistId)
                    }
                    if (albumId != null) {
                        putLong(EXTRA_ALBUM_ID, albumId)
                    }
                    if (albumYear != null) {
                        putLong(EXTRA_ALBUM_YEAR, albumYear)
                    }
                    putString(EXTRA_CD_TRACK_NUMBER, cdTrackNumber)
                    putString(EXTRA_AUTHOR, author)
                    if (modifiedDate != null) {
                        putLong(EXTRA_MODIFIED_DATE, modifiedDate)
                    }
                    if (hdArtworkUri != null) {
                        putParcelable(EXTRA_HD_ARTWORK_URI, hdArtworkUri.toUri())
                    }
                    if (path != null) {
                        putString(EXTRA_FILE, path)
                    }
                })
                .build()
        )
        .build()
}

fun MediaItem.toSongTagEntity(): SongTagEntity {
    val md = mediaMetadata
    return SongTagEntity(
        mediaId = mediaId,
        uri = localConfiguration?.uri.toString(),
        path = localConfiguration?.uri.toString(),
        mimeType = localConfiguration?.mimeType,
        title = md.title?.toString(),
        artist = md.artist?.toString(),
        albumTitle = md.albumTitle?.toString(),
        albumArtist = md.albumArtist?.toString(),
        artworkUri = md.artworkUri.toString(),
        hdArtworkUri = localConfiguration?.uri.toString(),
        trackNumber = md.trackNumber,
        discNumber = md.discNumber,
        recordingYear = md.recordingYear,
        recordingMonth = md.recordingMonth,
        recordingDay = md.recordingDay,
        releaseYear = md.releaseYear,
        albumYear = md.albumYear,
        cdTrackNumber = md.cdTrackNumber,
        isBrowsable = md.isBrowsable,
        isPlayable = md.isPlayable,
        addDate = md.addDate,
        modifiedDate = md.modifiedDate,
        durationMs = md.durationMs,
        writer = md.writer?.toString(),
        compilation = md.compilation?.toString(),
        composer = md.composer?.toString(),
        genre = md.genre?.toString(),
        author = md.author?.toString(),
        artistId = md.artistId,
        albumId = md.albumId,
    )
}
