package com.dd3boh.outertune.models

import com.dd3boh.outertune.db.entities.FormatEntity
import com.dd3boh.outertune.db.entities.Song

/**
 * For passing along song metadata
 */
data class SongTempData(val song: Song, val format: FormatEntity)