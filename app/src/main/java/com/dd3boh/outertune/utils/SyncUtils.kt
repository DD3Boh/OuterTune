package com.dd3boh.outertune.utils

import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.SongEntity
import com.dd3boh.outertune.models.toMediaMetadata
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncUtils @Inject constructor(
    val database: MusicDatabase,
) {
    suspend fun syncLikedSongs() {
        YouTube.playlist("LM").completed().onSuccess { page ->
            val songs = page.songs.reversed()

            database.likedSongsByNameAsc().first()
                .filterNot { it.id in songs.map(SongItem::id) }
                .forEach { database.update(it.song.localToggleLike()) }

            songs.forEach { song ->
                val dbSong = database.song(song.id).firstOrNull()
                database.transaction {
                    when (dbSong) {
                        null -> insert(song.toMediaMetadata(), SongEntity::localToggleLike)
                        else -> if (!dbSong.song.liked) update(dbSong.song.localToggleLike())
                    }
                }
            }
        }
    }
}
