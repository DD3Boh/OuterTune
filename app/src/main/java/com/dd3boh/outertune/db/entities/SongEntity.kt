package com.dd3boh.outertune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.apache.commons.lang3.RandomStringUtils
import java.time.LocalDateTime

@Immutable
@Entity(
    tableName = "song",
    indices = [
        Index(
            value = ["albumId"]
        )
    ]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val duration: Int = -1, // in seconds
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val year: Int? = null,
    val date: LocalDateTime? = null, // ID3 tag property
    val dateModified: LocalDateTime? = null, // file property
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val totalPlayTime: Long = 0, // in milliseconds
    val inLibrary: LocalDateTime? = null, // doubles as "date added"
    val isLocal: Boolean = false,
    val localPath: String?,
) {
    val isLocalSong: Boolean
        get() = id.startsWith("LA")

    fun localToggleLike() = copy(
        liked = !liked,
        likedDate = if (!liked) LocalDateTime.now() else null,
    )

    fun toggleLike() = copy(
        liked = !liked,
        likedDate = if (!liked) LocalDateTime.now() else null,
        inLibrary = if (!liked) inLibrary ?: LocalDateTime.now() else inLibrary
    ).also {
        CoroutineScope(Dispatchers.IO).launch {
            YouTube.likeVideo(id, !liked)
            this.cancel()
        }
    }

    fun toggleLibrary() = copy(inLibrary = if (inLibrary == null) LocalDateTime.now() else null)

    /**
     * Returns a full date string. If no full date is present, returns the year.
     * This is the song's tag's date/year, NOT dateModified.
     */
    fun getDateString(): String? {
        return date?.toString()
            ?: if (year != null) {
                return year.toString()
            } else {
                return null
            }
    }

    /**
     * Creates a copy of this song with the same ID, but properties of the new one
     *
     * @param s New song
     */
    fun getNewSong(s: SongEntity) = SongEntity(
            id,
            s.title,
            s.duration,
            s.thumbnailUrl,
            s.albumId,
            s.albumName,
            s.year,
            s.date,
            s.dateModified,
            s.liked,
            s.likedDate,
            s.totalPlayTime,
            s.inLibrary,
            s.isLocal,
            s.localPath,
        )

    companion object {
        fun generateSongId() = "LA" + RandomStringUtils.random(8, true, false)
    }
}
