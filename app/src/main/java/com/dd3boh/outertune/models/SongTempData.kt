package com.dd3boh.outertune.models

import com.dd3boh.outertune.db.entities.FormatEntity

/**
 * For passing along song metadata
 */
data class SongTempData(
    val id: String, val path: String, val title: String, val duration: Int, val artist: String?,
    val artistID: String?, val album: String?, val albumID: String?, val formatEntity: FormatEntity,
    val genre: String?, val date: String?, val dateModified: String?,
)