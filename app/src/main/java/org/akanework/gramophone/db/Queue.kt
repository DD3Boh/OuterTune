package org.akanework.gramophone.db

import androidx.room.Embedded
import androidx.room.Relation
import org.akanework.gramophone.db.entities.QueueEntity
import org.akanework.gramophone.db.entities.QueueSongMap

data class Queue(
    @Embedded val queue: QueueEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "queueId"
    )
    val songs: List<QueueSongMap>
)
