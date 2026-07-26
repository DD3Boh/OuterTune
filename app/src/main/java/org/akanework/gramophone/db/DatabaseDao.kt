package org.akanework.gramophone.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import org.akanework.gramophone.db.entities.PlayEventEntity
import org.akanework.gramophone.db.entities.SongEntity

@Dao
interface DatabaseDao : PlayCountDao {

// tag cache
/*
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(song: SongTagEntity)

    @Upsert
    fun update(songEntity: SongTagEntity)

    @Delete
    fun delete(songEntity: SongTagEntity)

    @Query("DELETE FROM tag_cache WHERE chromaprint NOT IN (:songs)")
    fun purge(songs: List<String>)
*/

    // debug helpers
    @Query("SELECT * from song")
    fun dumpSongDb(): List<SongEntity>

    @Query("SELECT * from play_event")
    fun dumpPlayEventDb(): List<PlayEventEntity>

    @Query(
        """
        SELECT song.*, COUNT(DISTINCT play_event.id) AS playCount, SUM(play_event_legacy.count) AS playCountLegacy
        FROM song
        LEFT JOIN play_event ON play_event.songId = song.id
        LEFT JOIN play_event_legacy ON play_event_legacy.songId = song.id
        GROUP BY song.id
        """
    )
    fun dumpSongsWithPlayCount(): List<SongWithPlaycount>


    @RawQuery
    fun raw(supportSQLiteQuery: SupportSQLiteQuery): Int

    fun checkpoint() {
        raw("PRAGMA wal_checkpoint(FULL)".toSQLiteQuery())
    }
}

fun String.toSQLiteQuery(): SimpleSQLiteQuery = SimpleSQLiteQuery(this)
