package com.dd3boh.outertune.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "song_genre_map",
    primaryKeys = ["songId", "genreId"],
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE),
        ForeignKey(
            entity = GenreEntity::class,
            parentColumns = ["id"],
            childColumns = ["genreId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SongGenreMap(
    @ColumnInfo(index = true) val songId: String,
    @ColumnInfo(index = true) val genreId: String,
    val index: Int,
)
