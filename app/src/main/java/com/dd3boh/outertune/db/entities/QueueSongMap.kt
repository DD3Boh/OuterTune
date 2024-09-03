package com.dd3boh.outertune.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "queue_song_map",
    foreignKeys = [
        ForeignKey(
            entity = QueueEntity::class,
            parentColumns = ["id"],
            childColumns = ["queueId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE)
    ]
)
data class QueueSongMap(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(index = true) val queueId: Long,
    @ColumnInfo(index = true) val songId: String,
    /**
     * yes, I am aware having separate entities for shuffled and unshuffled is not a good idea,
     * HOWEVER this makes managing the datastructure much easier since all this database does is
     * serve as a copy of the QueueBoard object. No, the persist queue file is not reliable, hence why this exists
     */
    val shuffled: Boolean,
)
