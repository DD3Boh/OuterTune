package com.dd3boh.outertune.lyrics

import android.content.Context
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

// true will prioritize local lyric files over all cloud providers, true is vice versa
private const val PREFER_LOCAL_LYRIC = true
class LyricsHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val lyricsProviders = listOf(YouTubeSubtitleLyricsProvider, LrcLibLyricsProvider, KuGouLyricsProvider, YouTubeLyricsProvider)
    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)

    /**
     * Retrieve lyrics from all sources
     *
     * @param mediaMetadata Song to fetch lyrics for
     * @param database MusicDatabase connection. Database lyrics are prioritized over all sources.
     * If no database is provided, the database source is disabled
     */
    suspend fun getLyrics(mediaMetadata: MediaMetadata, database: MusicDatabase? = null): String {
        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return cached.lyrics
        }
        val dbLyrics = database?.lyrics(mediaMetadata.id)?.let { it.first()?.lyrics }
        if (dbLyrics != null) {
            return dbLyrics
        }

        // Nougat support is likely going to be dropped soon
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return getRemoteLyrics(mediaMetadata) ?: LYRICS_NOT_FOUND
        }

        val localLyrics = getLocalLyrics(mediaMetadata)
        val remoteLyrics: String?

        // fallback to secondary provider when primary is unavailable
        if (PREFER_LOCAL_LYRIC) {
            if (localLyrics != null) {
                return localLyrics
            }

            // "lazy eval" the remote lyrics cuz it is laughably slow
            remoteLyrics = getRemoteLyrics(mediaMetadata)
            if (remoteLyrics != null) {
                return remoteLyrics
            }
        } else {
            remoteLyrics = getRemoteLyrics(mediaMetadata)
            if (remoteLyrics != null) {
                return remoteLyrics
            } else if (localLyrics != null) {
                return localLyrics
            }

        }

        return LYRICS_NOT_FOUND
    }

    /**
     * Lookup lyrics from remote providers
     */
    private suspend fun getRemoteLyrics(mediaMetadata: MediaMetadata): String? {
        lyricsProviders.forEach { provider ->
            if (provider.isEnabled(context)) {
                provider.getLyrics(
                    mediaMetadata.id,
                    mediaMetadata.title,
                    mediaMetadata.artists.joinToString { it.name },
                    mediaMetadata.duration
                ).onSuccess { lyrics ->
                    return lyrics
                }.onFailure {
                    reportException(it)
                }
            }
        }
        return null
    }

    /**
     * Lookup lyrics from local disk (.lrc) file
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getLocalLyrics(mediaMetadata: MediaMetadata): String? {
        if (LocalLyricsProvider.isEnabled(context)) {
            LocalLyricsProvider.getLyrics(
                mediaMetadata.id,
                "" + mediaMetadata.localPath, // title used as path
                mediaMetadata.artists.joinToString { it.name },
                mediaMetadata.duration
            ).onSuccess { lyrics ->
                return lyrics
            }
        }

        return null
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        callback: (LyricsResult) -> Unit,
    ) {
        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }
        val allResult = mutableListOf<LyricsResult>()
        lyricsProviders.forEach { provider ->
            if (provider.isEnabled(context)) {
                provider.getAllLyrics(mediaId, songTitle, songArtists, duration) { lyrics ->
                    val result = LyricsResult(provider.name, lyrics)
                    allResult += result
                    callback(result)
                }
            }
        }
        cache.put(cacheKey, allResult)
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
