package com.dd3boh.outertune.utils.scanners

import com.dd3boh.outertune.db.entities.AlbumEntity
import com.dd3boh.outertune.db.entities.ArtistEntity
import com.dd3boh.outertune.db.entities.FormatEntity
import com.dd3boh.outertune.db.entities.GenreEntity
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.db.entities.SongEntity
import com.dd3boh.outertune.models.SongTempData
import com.dd3boh.outertune.ui.utils.ARTIST_SEPARATORS
import timber.log.Timber
import wah.mikooomich.ffMetadataEx.FFprobeWrapper
import java.io.File
import java.lang.Integer.parseInt
import java.lang.Long.parseLong
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.roundToLong

const val EXTRACTOR_DEBUG = false
const val DEBUG_SAVE_OUTPUT = false // ignored (will be false) when EXTRACTOR_DEBUG IS false
const val EXTRACTOR_TAG = "FFProbeExtractor"
const val toSeconds = 1000 * 60 * 16.7 // convert FFmpeg duration to seconds

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
    override fun getAllMetadata(path: String): SongTempData {
        if (EXTRACTOR_DEBUG)
            Timber.tag(EXTRACTOR_TAG).d("Starting Full Extractor session on: $path")
        val ffprobe = FFprobeWrapper()
        val data = ffprobe.getFullAudioMetadata(path)

        if (EXTRACTOR_DEBUG && DEBUG_SAVE_OUTPUT) {
            Timber.tag(EXTRACTOR_TAG).d("Full output for: $path \n $data")
        }

        val songId = SongEntity.generateSongId()
        var rawTitle: String? = null
        var artists: String? = null
        var albumName: String? = null
        var genres: String? = null
        var rawDate: String? = null
        var codec: String? = null
        var type: String? = null
        var bitrate: String? = null
        var sampleRate: String? = null
        var channels: String? = null
        var rawDuration: String? = null
        var replayGain: Double? = null

        // read data from FFmpeg
        data.lines().forEach {
            val tag = it.substringBefore(':')
            when (tag) {
                // why the fsck does an error here get swallowed silently????
                "ARTISTS", "ARTIST", "artist" -> artists = it.substringAfter(':')
                "ALBUM", "album" -> albumName = it.substringAfter(':')
                "TITLE", "title" -> rawTitle = it.substringAfter(':')
//                "replaygain" -> replayGain = it.substringAfter(':')
                "GENRE", "genre" -> genres = it.substringAfter(':')
                "DATE", "date" -> rawDate = it.substringAfter(':')
                "codec" -> codec = it.substringAfter(':')
                "type" -> type = it.substringAfter(':')
                "bitrate" -> bitrate = it.substringAfter(':')
                "sampleRate" -> sampleRate = it.substringAfter(':')
                "channels" -> channels = it.substringAfter(':')
                "duration" -> rawDuration = it.substringAfter(':')
                else -> ""
            }
        }


        /**
         * These vars need a bit more parsing
         */

        val title: String = if (rawTitle != null && rawTitle?.isBlank() == false) { // songs with no title tag
            rawTitle!!.trim()
        } else {
            path.substringAfterLast('/').substringBeforeLast('.')
        }

        val duration: Long = try {
            (parseLong(rawDuration?.trim()) / toSeconds).roundToLong() // just let it crash
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }

        // should never be invalid if scanner even gets here fine...
        val dateModified = LocalDateTime.ofInstant(Instant.ofEpochMilli(File(path).lastModified()), ZoneOffset.UTC)
        val albumId = if (albumName != null) AlbumEntity.generateAlbumId() else null
        val mime = if (type != null && codec != null) {
            "${type?.trim()}/${codec?.trim()}"
        } else {
            "Unknown"
        }

        /**
         * Parse the more complicated structures
         */

        val artistList = ArrayList<ArtistEntity>()
        val genresList = ArrayList<GenreEntity>()
        var year: Int? = null
        var date: LocalDateTime? = null

        // parse album
        val albumEntity = if (albumName != null && albumId != null) AlbumEntity(
            id = albumId,
            title = albumName!!,
            songCount = 1,
            duration = duration.toInt()
        ) else null

        // parse artist
        artists?.split(ARTIST_SEPARATORS)?.forEach { element ->
            val artistVal = element.trim()
            artistList.add(ArtistEntity(ArtistEntity.generateArtistId(), artistVal, isLocal = true))
        }

        // parse genre
        genres?.split(";")?.forEach { element ->
            val genreVal = element.trim()
            genresList.add(GenreEntity(GenreEntity.generateGenreId(), genreVal, isLocal = true))
        }

        // parse date and year
        try {
            if (rawDate != null) {
                try {
                    date = LocalDate.parse(rawDate!!.substringAfter(';').trim()).atStartOfDay()
                } catch (e: Exception) {
                }

                year = date?.year ?: parseInt(rawDate!!.trim())
            }
        } catch (e: Exception) {
            // user error at this point. I am not parsing all the weird ways the string can come in
        }


        return SongTempData(
            Song(
                song = SongEntity(
                    id = songId,
                    title = title,
                    duration = duration.toInt(), // we use seconds for duration
                    albumId = albumId,
                    albumName = albumName,
                    year = year,
                    date = date,
                    dateModified = dateModified,
                    isLocal = true,
                    inLibrary = LocalDateTime.now(),
                    localPath = path
                ),
                artists = artistList,
                // album not working
                album = albumEntity,
                genre = genresList
            ),
            FormatEntity(
                id = songId,
                itag = -1,
                mimeType = mime,
                codecs = codec?.trim() ?: "Unknown",
                bitrate = bitrate?.let { parseInt(it.trim()) } ?: -1,
                sampleRate = sampleRate?.let { parseInt(it.trim()) } ?: -1,
                contentLength = duration,
                loudnessDb = replayGain,
                playbackUrl = null
            )
        )
    }

}