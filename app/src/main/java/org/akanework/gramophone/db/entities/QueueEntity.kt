package org.akanework.gramophone.db.entities

import androidx.compose.runtime.Immutable
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "queue")
data class QueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val index: Int,
    val isActiveQueue: Boolean,

    var title: String,
    var expiry: Long?,
    var startIndex: Int = C.INDEX_UNSET,
    var startPositionMs: Long = C.TIME_UNSET,
    var repeatMode: @Player.RepeatMode Int = 0,
    var shuffleOrder: String? = null,
    var ended: Boolean = false,
    val isOriginal: Boolean = true,
)


