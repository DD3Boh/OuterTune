package com.dd3boh.outertune.utils


/**
 * Returns metadata information
 */
interface MetadataScanner {
    /**
     * Given a path to a file, extract necessary metadata MediaStore fails to
     * deliver upon. Extracts artists, genres, and date
     *
     * @param path Full file path
     */
    fun getMediaStoreSupplement(path: String): ExtraMetadataWrapper
}

/**
 * A wrapper containing extra raw metadata that MediaStore fails to read properly
 */
data class ExtraMetadataWrapper(val artists: String?, val genres: String?, val date: String?)