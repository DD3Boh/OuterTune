package com.dd3boh.outertune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Embedded

@Immutable
data class Artist(
    @Embedded
    val artist: ArtistEntity,
    val songCount: Int,
    val downloadCount: Int
) : LocalItem() {
    override val id: String
        get() = artist.id
}
