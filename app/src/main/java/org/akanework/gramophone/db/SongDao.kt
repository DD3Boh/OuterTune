package org.akanework.gramophone.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import org.akanework.gramophone.db.entities.ChromaprintEntity
import org.akanework.gramophone.db.entities.SongEntity

@Dao
interface SongDao {

// Primitives

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(chromaprintEntity: ChromaprintEntity)


    @Transaction
    fun insert(song: Song, knownChromaprints: List<ChromaprintEntity>): Long {
        val songId = upsert(song.song).let {
            if (it == -1L) {
                song.song.id
            } else {
                it
            }
        }
        song.chromaprints
            .filter { c -> knownChromaprints.none { it.chromaprint == c.chromaprint } }
            .forEach {
                insert(it.copy(songId = songId))
            }
        return songId
    }


    @Upsert
    fun upsert(songEntity: SongEntity): Long

    @Delete
    fun delete(songEntity: SongEntity)


    // Getters

    @Transaction
    @Query(
        """
        SELECT song.* FROM song
        INNER JOIN chromaprint_map ON song.id = chromaprint_map.songId
        GROUP BY song.id
    """
    )
    fun songsWithChromaprints(): List<Song>

    @Query("SELECT * FROM song WHERE id = :id")
    fun getSongById(id: Long): SongEntity?

    @Transaction
    @Query(
        """
        SELECT * FROM song 
        INNER JOIN chromaprint_map ON song.id = chromaprint_map.songId
        WHERE chromaprint_map.chromaprint = :chromaprint
        LIMIT 1
    """
    )
    fun getSongByChromaprint(chromaprint: String): Song?

    // TODO: do we care if all fallbacks are null
    @Transaction
    @Query(
        """
        SELECT * FROM song
        WHERE uri IS NOT NULL AND uri = :filePath AND title = :title AND artist = :artist
    """
    )
    fun getSongByFallbacks(
        filePath: String?,
        title: String,
        artist: String
    ): List<Song>
}