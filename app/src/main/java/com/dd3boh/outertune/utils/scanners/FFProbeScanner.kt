package com.dd3boh.outertune.utils.scanners

import com.dd3boh.outertune.FFprobeWrapper
import com.dd3boh.outertune.utils.ExtraMetadataWrapper
import com.dd3boh.outertune.utils.MetadataScanner
import timber.log.Timber

const val DEBUG_SAVE_OUTPUT = false
const val EXTRACTOR_TAG = "FFProbeExtractor"

class FFProbeScanner : MetadataScanner {
    /**
     * Given a path to a file, extract necessary metadata
     *
     * @param path Full file path
     */
    override fun getMediaStoreSupplement(path: String): ExtraMetadataWrapper {
        Timber.tag(EXTRACTOR_TAG).d("Starting session on: $path")
        val ffprobe = FFprobeWrapper()
        val data = ffprobe.getAudioMetadata(path)
//    Log.d(EXTRACTOR_TAG, data)

        var artists: String? = null
        var genres: String? = null
        var date: String? = null

        data.lines().forEach {
            val tag = it.substringBefore(':')
            when (tag) {
                "ARTISTS" -> artists = it.substringAfter(':')
                "ARTIST" -> artists = it.substringAfter(':')
                "artist" -> artists = it.substringAfter(':')
                "GENRE" -> genres = it.substringAfter(':')
                "DATE" -> date = it.substringAfter(':')
                else -> ""
            }
        }

        if (DEBUG_SAVE_OUTPUT) {
            Timber.tag(EXTRACTOR_TAG).d("Full output for: $path \n $data")
        }
        return ExtraMetadataWrapper(artists, genres, date)
    }

}