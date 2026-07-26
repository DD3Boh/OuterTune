package org.akanework.gramophone.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "tag_cache")
data class SongTagEntity(
    @PrimaryKey val chromaprint: String,
    val path: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val year: Int?,

    val generation: Long, // // Q- use mtime
    val volume: String,
    // more...
)