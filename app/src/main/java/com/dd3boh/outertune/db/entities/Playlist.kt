package com.dd3boh.outertune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

@Immutable
data class Playlist(
    @Embedded
    val playlist: PlaylistEntity,
    val songCount: Int,
    val downloadCount: Int,
    @Relation(
        entity = SongEntity::class,
        entityColumn = "id",
        parentColumn = "id",
        projection = ["thumbnailUrl"],
        associateBy = Junction(
            value = PlaylistSongMapPreview::class,
            parentColumn = "playlistId",
            entityColumn = "songId"
        )
    )
    val songThumbnails: List<String>,
) : LocalItem() {
    override val id: String
        get() = playlist.id

    val thumbnails: List<String>
        get() {
            return if (playlist.thumbnailUrl != null)
                listOf(playlist.thumbnailUrl)
            else songThumbnails
        }
}
