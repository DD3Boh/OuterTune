package com.dd3boh.outertune.models

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.dd3boh.outertune.extensions.metadata
import com.dd3boh.outertune.extensions.move
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.playback.PlayerConnection
import com.dd3boh.outertune.playback.queues.EmptyQueue
import com.dd3boh.outertune.playback.queues.Queue
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.internal.toImmutableList
import timber.log.Timber
import java.io.Serializable
import kotlin.math.min
import kotlin.math.max

const val QUEUE_DEBUG = false
const val MAX_QUEUES = 20

/**
 * This is for UI display purposes only. Do not modify externally.
 */
var isShuffleEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)

/**
 * @param title Queue title (and UID)
 * @param queue List of media items
 */
data class MultiQueueObject(
    val title: String?,
    /**
     * The order of songs are dynamic. This should not be accessed form outside QueueBoard.
     */
    val queue: MutableList<MediaMetadata>,
    /**
     * The order of songs stays in the order songs are added in. This should not be accessed form outside QueueBoard.
     */
    val unShuffled: MutableList<MediaMetadata>,
    var shuffled: Boolean = false,
    var queuePos: Int = -1,
//    val ytmQueue: Queue?
) : Serializable

/**
 * Multiple queues manager. Methods will not automatically (re)load queues into the player unless
 * otherwise explicitly stated.
 */
class QueueBoard : Serializable {
    private val masterQueues: ArrayList<MultiQueueObject> = ArrayList()
    private var masterIndex = -1 // current queue index


    /**
     * ========================
     * Data structure management
     * ========================
     */


    /**
     * Push this queue to top of the master queue list, and track set this as current queue
     *
     * @param item
     */
    private fun bubbleUp(item: MultiQueueObject) {
        masterQueues.remove(item)
        masterQueues.add(item)
        masterIndex = masterQueues.size - 1
    }

    /**
     * Push this queue at index to top of the master queue list, and track set this as current queue.
     *
     * @param index
     */
    private fun bubbleUp(index: Int) {
        val item = masterQueues[index]
        masterQueues.remove(item)
        masterQueues.add(item)
        masterIndex = masterQueues.size - 1
    }

    /**
     * Add a new queue to the QueueBoard, or add to a queue if it exists.
     *
     * Depending on the circumstances, there can be varying behaviour.
     * 1. Queue does not exist: Queue is added as a new queue.
     * 2. Queue exists, and the contents are a perfect match (by songID): Current position (queuePos)
     *      index is updated. Queue itself is not modified.
     * 3. Queue exists, contents are different:
     *      delta is true: Extra items are added to the old queue. Current position is updated.
     *      delta is false: Items are added to the end of the queue, see 4.
     * 4. Items are purely added into the queue: Current position is NOT updated.
     *      When delta is false, this is "add mode". A new "+" suffix queue is spawned if it doesn't
     *      exist, and items are added to the end of the queue. We want queues with titles to represent
     *      the source (title), while the "+" suffix denotes a custom user queue where "anything goes".
     *
     * or add songs to queue it exists (and forceInsert is not true).
     *
     * @param title Title (id) of the queue
     * @param mediaList List of items to add
     * @param queue Queue object for song continuation et al
     * @param forceInsert When mediaList contains one item, force an insert instead of jumping to an
     *      item if it exists
     * @param delta Takes not effect if forceInsert is false. Setting this to true will add only new
     *      songs, false will add all songs
     * @param startIndex Index/position to instantiate the new queue with. This value takes no effect
     * if the queue already exists
     */
    fun add(
        title: String,
        mediaList: List<MediaMetadata?>,
        queue: Queue? = EmptyQueue,
        forceInsert: Boolean = false,
        delta: Boolean = true,
        startIndex: Int = 0
    ) {
        if (QUEUE_DEBUG)
            Timber.tag(TAG).d(
                "Adding to queue \"$title\". medialist size = ${mediaList.size}. " +
                        "forceInsert/delta/startIndex = $forceInsert/$delta/$startIndex"
            )

        if (mediaList.isEmpty()) {
            return
        }

        val match = masterQueues.firstOrNull { it.title == title } // look for matching queue. Title is uid
        if (match != null) { // found an existing queue
            // don't add songs to the queue if it's just one EXISTING song AND the new medialist is a subset of what we have
            // UNLESS forced to
            val containsAll = mediaList.all { s -> match.queue.any { s?.id == it.id } } // if is subset
            if (containsAll && match.queue.size == mediaList.size && !forceInsert) { // jump to song, don't add
                if (QUEUE_DEBUG)
                    Timber.tag(TAG).d("Adding to queue: jump only")
                // find the song in existing queue song, track the index to jump to
                val findSong = match.queue.firstOrNull { it.id == mediaList[startIndex]?.id }
                if (findSong != null) {
                    match.queuePos = match.queue.indexOf(findSong)
                }

                bubbleUp(match)  // move queue to end of list so it shows as most recent
            } else if (delta) {
                if (QUEUE_DEBUG)
                    Timber.tag(TAG).d("Adding to queue: delta additive")
                // add only the songs that are not already in the queue
                match.queue.addAll(mediaList.filter { s -> match.queue.none { s?.id == it.id } }.filterNotNull())
                match.unShuffled.addAll(mediaList.filter { s -> match.queue.none { s?.id == it.id } }.filterNotNull())

                // find the song in existing queue song, track the index to jump to
                val findSong = match.queue.firstOrNull { it.id == mediaList[startIndex]?.id }
                if (findSong != null) {
                    match.queuePos = match.queue.indexOf(findSong) // track the index we jumped to
                }

                bubbleUp(match) // move queue to end of list so it shows as most recent
            } else if (match.title?.endsWith('+') == true) { // this queue is an already an extension queue
                if (QUEUE_DEBUG)
                    Timber.tag(TAG).d("Adding to queue: extension queue additive")
                // add items to existing queue unconditionally
                match.queue.addAll(mediaList.filterNotNull())
                match.unShuffled.addAll(mediaList.filterNotNull())

                // don't change index
                bubbleUp(match) // move queue to end of list so it shows as most recent
            } else { // make new extension queue
                if (QUEUE_DEBUG)
                    Timber.tag(TAG).d("Adding to queue: extension queue creation (and additive)")
                // add items to NEW queue unconditionally (add entirely new queue)
                if (masterQueues.size > MAX_QUEUES) {
                    deleteQueue(masterQueues.first())
                }

                // create new queues
                val shufQueue = ArrayList(match.queue.map { it })
                val unShufQueue = ArrayList(match.unShuffled.map { it })

                shufQueue.addAll((mediaList.filterNotNull()))
                unShufQueue.addAll((mediaList.filterNotNull()))
                masterQueues.add(
                    MultiQueueObject("$title +", shufQueue, unShufQueue, false, match.queuePos)
                )

                // don't change index, don't move match queue to end
                masterIndex = masterQueues.size - 1 // track the newly modified queue
            }
        } else {
            // add entirely new queue
            if (masterQueues.size > MAX_QUEUES) {
                deleteQueue(masterQueues.first())
            }
            masterQueues.add(
                MultiQueueObject(
                    title,
                    ArrayList(mediaList.filterNotNull()),
                    ArrayList(mediaList.filterNotNull()),
                    false,
                    startIndex,
//                    ytmQueue = queue
                )
            )
            masterIndex = masterQueues.size - 1 // track the newly modified queue
        }
    }

    /**
     * Deletes a queue
     *
     * @param item
     */
    fun deleteQueue(item: MultiQueueObject): Int {
        if (QUEUE_DEBUG)
            Timber.tag(TAG).d("DELETING QUEUE ${item.title}")

        val match = masterQueues.firstOrNull { it.title == item.title }
        if (match != null) {
            masterQueues.remove(match)
            if (masterQueues.isNotEmpty()) {
                masterIndex -= 1
            } else {
                masterIndex = -1
            }
        } else if (QUEUE_DEBUG) {
            Timber.tag(TAG).w("Cannot find queue ${item.title}")
        }

        return masterQueues.size
    }


    /**
     * Un-shuffles current queue
     *
     * @return New current position tracker
     */
    fun unShuffleCurrent() = unShuffle(masterIndex)

    /**
     * Un-shuffles a queue
     *
     * @return New current position tracker
     */
    fun unShuffle(index: Int): Int {
        val item = masterQueues[index]
        if (item.shuffled) {
            if (QUEUE_DEBUG)
                Timber.tag(TAG).d("Un-shuffling queue ${item.title}")
            // re-track current position
            item.queuePos = item.unShuffled.indexOf(item.queue[item.queuePos])
            item.shuffled = false
            isShuffleEnabled.value = false
        }
        bubbleUp(item)
        return item.queuePos
    }

    /**
     * Shuffles current queue
     */
    fun shuffleCurrent(preserveCurrent: Boolean = true) = shuffle(masterIndex, preserveCurrent)


    /**
     * Un-shuffles a queue
     *
     * If shuffle is enabled, it will pull from the shuffled queue, if shuffle is not enabled, it pulls from the
     * un-shuffled queue
     *
     * @param index
     * @param preserveCurrent True will push the currently playing song to the top of the queue. False will
     *      fully shuffle everything.
     * @return New current position tracker
     */
    fun shuffle(index: Int, preserveCurrent: Boolean = true): Int {
        val item = masterQueues[index]
        if (QUEUE_DEBUG)
            Timber.tag(TAG).d("Shuffling queue ${item.title}")

        val currentSong = if (item.shuffled) item.queue[item.queuePos] else item.unShuffled[item.queuePos]

        // shuffle & push the current song to top if requested to
        item.queue.shuffle()
        if (preserveCurrent) {
            item.queue.remove(currentSong)
            item.queue.add(0, currentSong)
        }
        item.queuePos = 0
        item.shuffled = true
        isShuffleEnabled.value = true

        bubbleUp(item)
        return item.queuePos
    }

    /**
     * Move a queue in masterQueues
     *
     * @param fromIndex Song to move
     * @param toIndex Destination
     *
     * @return New current position tracker
     */
    fun move(fromIndex: Int, toIndex: Int) {
        // update current position only if the move will affect it
        if (masterIndex >= min(fromIndex, toIndex) && masterIndex <= max(fromIndex, toIndex)) {
            if (fromIndex == masterIndex) {
                masterIndex = toIndex
            } else if (masterIndex == toIndex) {
                if (masterIndex < fromIndex) {
                    masterIndex++
                } else {
                    masterIndex--
                }
            } else if (toIndex > masterIndex) {
                masterIndex--
            } else {
                masterIndex++
            }
        }

        masterQueues.move(fromIndex, toIndex)
    }


    /**
     * Move a song in the current queue
     *
     * @param fromIndex Song to move
     * @param toIndex Destination
     * @param currentMediaItemIndex Index of now playing song
     *
     * @return New current position tracker
     */
    fun moveSong(fromIndex: Int, toIndex: Int, currentMediaItemIndex: Int) =
        getCurrentQueue()?.let { moveSong(it, fromIndex, toIndex, currentMediaItemIndex) }

    /**
     * Move a song, given a queue.
     *
     * @param queue Queue to operate on
     * @param fromIndex Song to move
     * @param toIndex Destination
     * @param currentMediaItemIndex Index of now playing song
     *
     * @return New current position tracker
     */
    private fun moveSong(queue: MultiQueueObject, fromIndex: Int, toIndex: Int, currentMediaItemIndex: Int): Int {
        // update current position only if the move will affect it
        if (currentMediaItemIndex >= min(fromIndex, toIndex) && currentMediaItemIndex <= max(fromIndex, toIndex)) {
            if (fromIndex == currentMediaItemIndex) {
                queue.queuePos = toIndex
            } else if (currentMediaItemIndex == toIndex) {
                if (currentMediaItemIndex < fromIndex) {
                    queue.queuePos++
                } else {
                    queue.queuePos--
                }
            } else if (toIndex > currentMediaItemIndex) {
                queue.queuePos--
            } else {
                queue.queuePos++
            }
        }

        // I like to move it move it
        if (queue.shuffled) {
            queue.queue.move(fromIndex, toIndex)
        } else {
            queue.unShuffled.move(fromIndex, toIndex)
        }

        if (QUEUE_DEBUG)
            Timber.tag(TAG).d("Moved item from $currentMediaItemIndex to ${queue.queuePos}")
        return queue.queuePos
    }


    /**
     * =================
     * Player management
     * =================
     */

    /**
     * Get all copy of all queues
     */
    fun getAllQueues() = masterQueues.toImmutableList()


    /**
     * Get the index of the current queue
     */
    fun getMasterIndex() = masterIndex

    /**
     * Retrieve the current queue
     *
     * @return Queue object (entire object)
     */
    fun getCurrentQueue(): MultiQueueObject? {
        try {
            return masterQueues[masterIndex]
        } catch (e: IndexOutOfBoundsException) {
            masterIndex = masterQueues.size - 1 // reset var if invalid
            return null
        }
    }

    /**
     * Retrieve the current queue in list form, with shuffle state taken in account
     *
     * @return Queue object (entire object)
     */
    fun getCurrentQueueShuffled(): MutableList<MediaMetadata>? {
        val item = getCurrentQueue()
        return if (item == null) {
            null
        } else if (item.shuffled) {
            item.queue
        } else {
            item.unShuffled
        }
    }

    /**
     * Load the current queue into the media player\
     *
     * @param playerConnection link
     * @param autoSeek true will automatically jump to a position in the queue after loading it
     * @return New current position tracker
     */
    fun setCurrQueue(playerConnection: PlayerConnection, autoSeek: Boolean = true) =
        setCurrQueue(getCurrentQueue(), playerConnection.player, autoSeek)

    /**
     * Load the current queue into the media player\
     *
     * @param player Player link
     * @param autoSeek true will automatically jump to a position in the queue after loading it
     * @return New current position tracker
     */
    fun setCurrQueue(player: Player, autoSeek: Boolean = true) = setCurrQueue(getCurrentQueue(), player, autoSeek)

    /**
     * Load a queue into the media player\
     *
     * @param item Queue object
     * @param player Player link
     * @param autoSeek true will automatically jump to a position in the queue after loading it
     * @return New current position tracker
     */
    fun setCurrQueue(item: MultiQueueObject?, player: Player, autoSeek: Boolean = true): Int? {
        if (QUEUE_DEBUG)
            Timber.tag(TAG).d(
                "Loading queue ${item?.title ?: "null"} into player. " +
                        "autoSeek = $autoSeek shuffle state = ${item?.shuffled}"
            )

        if (item == null) {
            player.setMediaItems(ArrayList())
            return null
        }

        val queuePos = item.queuePos // I have no idea why this value gets reset to 0 by the end... but ig this works
        masterIndex = masterQueues.indexOf(item)

        // if requested to get shuffled queue
        if (item.shuffled) {
            player.setMediaItems(item.queue.map { it.toMediaItem() })
        } else {
            player.setMediaItems(item.unShuffled.map { it.toMediaItem() })
        }
        isShuffleEnabled.value = item.shuffled

        if (autoSeek) {
            player.seekTo(queuePos, C.TIME_UNSET)
        }

        return queuePos
    }

    /**
     * Update the current position index of the current queue
     *
     * @param index
     */
    fun setCurrQueuePosIndex(index: Int) {
        getCurrentQueue()?.queuePos = index
    }

    /**
     * Update the current position index of the current queue to the index of the media item
     *
     * @param mediaItem
     */
    fun setCurrQueuePosIndex(mediaItem: MediaItem?) {
        val currentQueue = getCurrentQueue()
        if (mediaItem == null || currentQueue == null) {
            return
        }

        if (currentQueue.shuffled) {
            currentQueue.queuePos = currentQueue.queue.indexOf(mediaItem.metadata)
        } else {
            currentQueue.queuePos = currentQueue.unShuffled.indexOf(mediaItem.metadata)
        }
    }

    companion object {
        const val TAG = "QueueBoard"
    }

}