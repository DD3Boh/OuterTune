package com.dd3boh.outertune.utils.scanners

import com.dd3boh.outertune.db.entities.FormatEntity
import com.dd3boh.outertune.models.SongTempData


/**
 * Returns metadata information
 */
interface MetadataScanner {

    /**
     * Given a path to a file, extract necessary metadata. For fields FFmpeg is
     * unable to extract, use the provided FormatEntity data.
     *
     * @param path Full file path
     * @param og Initial FormatEntity data to build upon
     */
    fun getAllMetadata(path: String): SongTempData
}

/**
 * A wrapper containing extra raw metadata that MediaStore fails to read properly
 */
data class ExtraMetadataWrapper(val artists: String?, val genres: String?, val date: String?, var format: FormatEntity?)