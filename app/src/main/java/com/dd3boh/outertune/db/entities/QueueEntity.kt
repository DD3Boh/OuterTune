package com.dd3boh.outertune.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queue")
data class QueueEntity(
    @PrimaryKey val id: String, // title
    var shuffled: Boolean = false,
    var queuePos: Int = -1,
)