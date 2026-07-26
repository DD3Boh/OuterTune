/*
 *     Copyright (C) 2026 The Gramophone contributors
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.akanework.gramophone.db

import androidx.media3.common.MediaItem
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.akanework.gramophone.db.entities.ChromaprintEntity
import org.akanework.gramophone.db.entities.PlayEventEntity
import org.akanework.gramophone.db.entities.PlayEventLegacyEntity
import org.akanework.gramophone.db.entities.SongEntity

@Dao
interface PlayCountDao : SongDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun _recordEvent(event: PlayEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun _recordEventLegacy(event: PlayEventLegacyEntity)


    @Transaction
    fun recordEvent(mediaItem: MediaItem, timestamp: Long, duration: Long) {
        val s = mediaItem.mediaMetadata
        val chromaprint = s.extras?.getString("chromaprint")

        // check if exists, if not then toss new song in
        var dbSong: Song? = getSongByFallbacks(
            mediaItem.localConfiguration?.uri?.toString(),
            s.title.toString(),
            s.artist.toString()
        ).firstOrNull() ?: chromaprint?.let {
            getSongByChromaprint(chromaprint)
        }
        val knownChromaprints = dbSong?.chromaprints ?: emptyList()

        // db has no chromaprint, but we have one now, migrate the old song with placeholder to a chromaprint song
        val runPostmigrate = knownChromaprints.isEmpty() && chromaprint != null

        if (dbSong == null) {
            val songId = SongEntity.generateSongIdForUnitTests()
            dbSong = Song(
                song = SongEntity(
                    id = songId,
                    uri = mediaItem.localConfiguration?.uri?.path,
                    title = s.title.toString(),
                    artist = s.artist.toString(),
                    album = s.albumTitle.toString(),
                    year = s.recordingYear
                ),
                chromaprints = if (chromaprint != null) {
                    listOf(ChromaprintEntity(chromaprint = chromaprint, songId = songId))
                } else {
                    emptyList()
                }
            )
        }

        insert(dbSong, knownChromaprints)
        _recordEvent(
            PlayEventEntity(
                songId = dbSong.song.id,
                timestamp = timestamp,
                duration = duration
            )
        )
        if (runPostmigrate) {
            insert(ChromaprintEntity(chromaprint = chromaprint, songId = dbSong.song.id))
            mergeSongsByChromaprint()
        }
    }

    @Transaction
    fun recordEventLegacy(mediaItem: MediaItem, month: Int, year: Int, count: Int) {
        if (month !in 0..12) throw IllegalArgumentException("Months must be a number from (inclusive) 1-12, or 0 to signify an unknown month")
        val s = mediaItem.mediaMetadata
        val chromaprint = s.extras?.getString("chromaprint")

        // check if exists, if not then toss new song in
        var dbSong: Song? = getSongByFallbacks(
            mediaItem.localConfiguration?.uri?.toString(),
            s.title.toString(),
            s.artist.toString()
        ).firstOrNull() ?: chromaprint?.let {
            getSongByChromaprint(chromaprint)
        }
        val knownChromaprints = dbSong?.chromaprints ?: emptyList()

        // db has no chromaprint, but we have one now, migrate the old song with placeholder to a chromaprint song
        val runPostmigrate = knownChromaprints.isEmpty() && chromaprint != null

        if (dbSong == null) {
            val songId = SongEntity.generateSongIdForUnitTests()
            dbSong = Song(
                song = SongEntity(
                    id = songId,
                    uri = mediaItem.localConfiguration?.uri?.path,
                    title = s.title.toString(),
                    artist = s.artist.toString(),
                    album = s.albumTitle.toString(),
                    year = s.recordingYear
                ),
                chromaprints = if (chromaprint != null) {
                    listOf(ChromaprintEntity(chromaprint = chromaprint, songId = songId))
                } else {
                    emptyList()
                }
            )
        }

        insert(dbSong, knownChromaprints)
        _recordEventLegacy(
            PlayEventLegacyEntity(
                songId = dbSong.song.id,
                year = year,
                month = month,
                count = count
            )
        )

        if (runPostmigrate) {
            insert(ChromaprintEntity(chromaprint = chromaprint, songId = dbSong.song.id))
            mergeSongsByChromaprint()
        }
    }


    @Query("UPDATE play_event SET songId = :newSongId WHERE songId = :oldSongId")
    fun migrateSongEvents(oldSongId: Long, newSongId: Long)

    /**
     * Merge histories for all songs with same chromaprint
     */
    @Transaction
    fun mergeSongsByChromaprint() {
        val songs: List<Song> = songsWithChromaprints()

        // Union-Find
        val parent = songs.associate { it.song.id to it.song.id }.toMutableMap()

        fun find(id: Long): Long {
            if (parent[id] != id) parent[id] = find(parent[id]!!)
            return parent[id]!!
        }

        fun union(a: Long, b: Long) {
            parent[find(a)] = find(b)
        }

        // Union songs that share any chromaprint
        val chromaprintToSongId = mutableMapOf<String, Long>()
        songs.forEach { song ->
            song.chromaprints.forEach { cp ->
                val existing = chromaprintToSongId[cp.chromaprint]
                if (existing != null) {
                    union(song.song.id, existing)
                } else {
                    chromaprintToSongId[cp.chromaprint] = song.song.id
                }
            }
        }

        songs.groupBy { find(it.song.id) }.values
            .filter { it.size > 1 }
            .forEach { duplicates ->
                // for all intents and purposes, the song we merge into doesnt matter
                val adopter = duplicates.first()
                duplicates.subList(1, duplicates.size).forEach {
                    migrateSongEvents(it.song.id, adopter.song.id)
                    delete(it.song)
                }
            }
    }

}