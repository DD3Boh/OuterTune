package com.dd3boh.outertune.utils.scanners

import com.dd3boh.outertune.db.entities.FormatEntity
import timber.log.Timber
import wah.mikooomich.ffMetadataEx.FFprobeWrapper
import java.lang.Integer.parseInt
import java.lang.Long.parseLong

const val EXTRACTOR_DEBUG = true
const val DEBUG_SAVE_OUTPUT = true // ignored (will be false) when EXTRACTOR_DEBUG IS false
const val EXTRACTOR_TAG = "FFProbeExtractor"

class FFProbeScanner : MetadataScanner {
    // load advanced scanner libs
    init {
        System.loadLibrary("avcodec")
        System.loadLibrary("avdevice")
        System.loadLibrary("ffprobejni")
        System.loadLibrary("avfilter")
        System.loadLibrary("avformat")
        System.loadLibrary("avutil")
        System.loadLibrary("swresample")
        System.loadLibrary("swscale")
    }

    /**
     * Given a path to a file, extract necessary metadata
     *
     * @param path Full file path
     */
    override fun getMediaStoreSupplement(path: String): ExtraMetadataWrapper {
        if (EXTRACTOR_DEBUG)
            Timber.tag(EXTRACTOR_TAG).d("Starting MediaStoreSupplement session on: $path")
        val ffprobe = FFprobeWrapper()
        val data = ffprobe.getAudioMetadata(path)

        if (EXTRACTOR_DEBUG && DEBUG_SAVE_OUTPUT) {
            Timber.tag(EXTRACTOR_TAG).d("Full output for: $path \n $data")
        }

        var artists: String? = null
        var genres: String? = null
        var date: String? = null

        data.lines().forEach {
            val tag = it.substringBefore(':')
            when (tag) {
                "ARTISTS", "ARTIST", "artist" -> artists = it.substringAfter(':')
                "GENRE", "genre" -> genres = it.substringAfter(':')
                "DATE", "date" -> date = it.substringAfter(':')
                else -> ""
            }
        }

        return ExtraMetadataWrapper(artists, genres, date, null)
    }

    /**
     * Given a path to a file, extract all necessary metadata
     *
     * @param path Full file path
     */
    override fun getAllMetadata(path: String, og: FormatEntity): ExtraMetadataWrapper {
        if (EXTRACTOR_DEBUG)
            Timber.tag(EXTRACTOR_TAG).d("Starting Full Extractor session on: $path")
        val ffprobe = FFprobeWrapper()
        val data = ffprobe.getFullAudioMetadata(path)

        if (EXTRACTOR_DEBUG && DEBUG_SAVE_OUTPUT) {
            Timber.tag(EXTRACTOR_TAG).d("Full output for: $path \n $data")
        }

        var artists: String? = null
        var genres: String? = null
        var date: String? = null
        var codec: String? = null
        var type: String? = null
        var bitrate: String? = null
        var sampleRate: String? = null
        var channels: String? = null
        var duration: String? = null

        data.lines().forEach {
            val tag = it.substringBefore(':')
            when (tag) {
                // why the fsck does an error here get swallowed silently????
                "ARTISTS", "ARTIST", "artist" -> artists = it.substringAfter(':')
                "GENRE", "genre" -> genres = it.substringAfter(':')
                "DATE", "date" -> date = it.substringAfter(':')
                "codec" -> codec = it.substringAfter(':')
                "type" -> type = it.substringAfter(':')
                "bitrate" -> bitrate = it.substringAfter(':')
                "sampleRate" -> sampleRate = it.substringAfter(':')
                "channels" -> channels = it.substringAfter(':')
                "duration" -> duration = it.substringAfter(':')
                else -> ""
            }
        }
        return ExtraMetadataWrapper(artists, genres, date, FormatEntity(
            id = og.id,
            itag = og.itag,
            mimeType = og.mimeType,
            codecs = codec?.trim() ?: og.codecs,
            bitrate = bitrate?.let { parseInt(it.trim()) } ?: og.bitrate,
            sampleRate = sampleRate?.let { parseInt(it.trim()) } ?: og.sampleRate,
            contentLength = duration?.let { parseLong(it.trim()) } ?: og.contentLength,
            loudnessDb = og.loudnessDb,
            playbackUrl = og.playbackUrl
        ))
    }

}