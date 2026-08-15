package org.akanework.gramophone.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import org.akanework.gramophone.db.entities.PlayEventEntity
import org.akanework.gramophone.db.entities.QueueEntity
import org.akanework.gramophone.db.entities.QueueSongMap
import org.akanework.gramophone.db.entities.SongEntity
import org.akanework.gramophone.db.entities.SongTagEntity
import org.akanework.gramophone.db.entities.toMediaItem
/*
import org.akanework.gramophone.db.entities.toSongTagEntity
import org.akanework.gramophone.logic.MultiQueueObject
import org.akanework.gramophone.logic.utils.CircularShuffleOrder
 */
import kotlin.collections.map

@Dao
interface DatabaseDao : PlayCountDao {

// tag cache
/*
    @Upsert
    fun upsert(songEntity: SongTagEntity)

    @Delete
    fun delete(songEntity: SongTagEntity)

    @Upsert
    fun upsert(queueEntity: QueueEntity)

    @Transaction
    fun updateAllQueues(mqs: List<MultiQueueObject>, activeQueueIndex: Int) {
        val mqs = mqs.toList() // please no more ConcurrentModificationException I beg you
        mqs.forEachIndexed { index, q -> q.index = index }
//        nukeAliens(mqs.map { it.id })
        mqs.forEachIndexed { index, q ->
            updateQueue(q, index == q.index)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(queueSong: QueueSongMap)

    @Delete
    fun delete(mq: QueueEntity)

    @Query("DELETE FROM queue")
    fun deleteAllQueues()

    @Query("DELETE FROM queue_song_map WHERE queueId = :id")
    fun deleteAllQueueSongs(id: Long)

    @Query("DELETE FROM queue WHERE id = :id")
    fun deleteQueue(id: Long)

    @Query("SELECT * from queue ORDER BY `index`")
    fun getAllQueues(): List<QueueEntity>

    @Query("DELETE FROM queue WHERE id NOT IN (:ids)")
    fun nukeAliens(ids: List<Long>)

    @Transaction
    @Query("SELECT tag_cache.* FROM queue_song_map JOIN tag_cache ON queue_song_map.songId = tag_cache.mediaId WHERE queueId = :queueId ORDER BY `index`")
    fun getQueueSongs(queueId: Long): List<SongTagEntity>

    @Transaction
    fun readQueues(): Pair<MultiQueueObject?, List<MultiQueueObject>> {
        val inactiveQueues = ArrayList<MultiQueueObject>()
        val queues = getAllQueues()
        var activeQueue: MultiQueueObject? = null

        queues.forEach { queue ->
            val songs = getQueueSongs(queue.id)
            if (songs.isEmpty()) {
                deleteQueue(queue.id)
                return@forEach
            }

            val q = MultiQueueObject(
                id = queue.id,
                title = queue.title,
                queue = songs.map {
                    it.toMediaItem()
                }.toMutableList(),
                index = queue.index,
                expiry = queue.expiry,
                startIndex = queue.startIndex,
                startPositionMs = queue.startPositionMs,
                repeatMode = queue.repeatMode,
                shuffleOrder = if (queue.shuffleOrder != null) {
                    CircularShuffleOrder.Persistent.deserialize(queue.shuffleOrder)
                } else {
                    null
                },
                ended = queue.ended,
                isOriginal = queue.isOriginal,
            )

            if (queue.isActiveQueue) {
                activeQueue = q
            } else {
                inactiveQueues.add(q)
            }
        }

        return Pair(activeQueue, inactiveQueues)
    }

    @Transaction
    fun updateQueue(mq: MultiQueueObject, isActiveQueue: Boolean) {
        upsert(
            QueueEntity(
                id = mq.id,
                index = mq.index,
                isActiveQueue = isActiveQueue,
                title = mq.title,
                expiry = mq.expiry,
                startIndex = mq.startIndex,
                startPositionMs = mq.startPositionMs,
                repeatMode = mq.repeatMode,
                shuffleOrder = mq.shuffleOrder?.toString(),
                ended = mq.ended,
                isOriginal = mq.isOriginal,
            )
        )
    }

    /**
     * WARNING: This removes all queue song data and re-adds the queue. Did you mean to use updateQueue()?
     */
    @Transaction
    suspend fun saveQueue(mq: MultiQueueObject, isActiveQueue: Boolean) {
        if (mq.queue.isEmpty()) {
            return
        }

        upsert(
            QueueEntity(
                id = mq.id,
                index = mq.index,
                isActiveQueue = isActiveQueue,
                title = mq.title,
                expiry = mq.expiry,
                startIndex = mq.startIndex,
                startPositionMs = mq.startPositionMs,
                repeatMode = mq.repeatMode,
                shuffleOrder = mq.shuffleOrder?.toString(),
                ended = mq.ended,
                isOriginal = mq.isOriginal,
                )
        )

        deleteAllQueueSongs(mq.id)
        // insert songs

        // why does kotlin not have for i loop???
        var i = 0
        while (i < mq.getSize()) {
            upsert(mq.queue[i].toSongTagEntity()) // make sure song exists
            insert(
                QueueSongMap(
                    queueId = mq.id,
                    songId = mq.queue[i].mediaId,
                    index = i.toLong(),
                )
            )
            i++
        }
    }

    @Query("UPDATE queue SET isActiveQueue = 0")
    fun hax()
*/

    // debug helpers
    @Query("SELECT * from song")
    fun dumpSongDb(): List<SongEntity>

    @Query("SELECT * from play_event")
    fun dumpPlayEventDb(): List<PlayEventEntity>

    @Transaction
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

    @Transaction
    @Query("SELECT * from play_event LEFT JOIN song ON play_event.songId = song.id  ORDER BY timestamp DESC ")
    fun history(): List<PlayEventWithSong>


    @RawQuery
    fun raw(supportSQLiteQuery: SupportSQLiteQuery): Int

    fun checkpoint() {
        raw("PRAGMA wal_checkpoint(FULL)".toSQLiteQuery())
    }
}

fun String.toSQLiteQuery(): SimpleSQLiteQuery = SimpleSQLiteQuery(this)
