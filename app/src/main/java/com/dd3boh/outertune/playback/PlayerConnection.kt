package com.dd3boh.outertune.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.LyricsEntity
import com.dd3boh.outertune.extensions.currentMetadata
import com.dd3boh.outertune.extensions.getCurrentQueueIndex
import com.dd3boh.outertune.extensions.getQueueWindows
import com.dd3boh.outertune.extensions.metadata
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.models.QueueBoard
import com.dd3boh.outertune.models.isShuffleEnabled
import com.dd3boh.outertune.playback.MusicService.MusicBinder
import com.dd3boh.outertune.playback.queues.Queue
import com.dd3boh.outertune.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    binder: MusicBinder,
    val database: MusicDatabase,
    scope: CoroutineScope,
) : Player.Listener {
    val service = binder.service
    val player = service.player

    val playbackState = MutableStateFlow(player.playbackState)
    private val playWhenReady = MutableStateFlow(player.playWhenReady)
    val isPlaying = combine(playbackState, playWhenReady) { playbackState, playWhenReady ->
        playWhenReady && playbackState != STATE_ENDED
    }.stateIn(scope, SharingStarted.Lazily, player.playWhenReady && player.playbackState != STATE_ENDED)
    val mediaMetadata = MutableStateFlow(player.currentMetadata)
    val currentSong = mediaMetadata.flatMapLatest {
        database.song(it?.id)
    }
    val currentLyrics = mediaMetadata.flatMapLatest { mediaMetadata ->
        // local songs will always look at lrc files first
        if (mediaMetadata?.isLocal == true) {
            val lyrics = service.lyricsHelper.getLocalLyrics(mediaMetadata)
            if (lyrics != null) {
                return@flatMapLatest flowOf(
                    LyricsEntity(
                        id = mediaMetadata.id,
                        lyrics = lyrics
                    )
                )
            }
        }
        database.lyrics(mediaMetadata?.id)
    }
    val currentFormat = mediaMetadata.flatMapLatest { mediaMetadata ->
        database.format(mediaMetadata?.id)
    }
    val currentPlayCount = mediaMetadata.flatMapLatest { mediaMetadata ->
        database.getLifetimePlayCount(mediaMetadata?.id)
    }

    private val currentMediaItemIndex = MutableStateFlow(-1)

    val queueTitle = MutableStateFlow<String?>(null)
    val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())

    var queuePlaylistId = MutableStateFlow<String?>(null)
    val currentWindowIndex = MutableStateFlow(-1)

    val repeatMode = MutableStateFlow(REPEAT_MODE_OFF)

    val canSkipPrevious = MutableStateFlow(true)
    val canSkipNext = MutableStateFlow(true)

    val error = MutableStateFlow<PlaybackException?>(null)

    init {
        player.addListener(this)

        playbackState.value = player.playbackState
        playWhenReady.value = player.playWhenReady
        mediaMetadata.value = player.currentMetadata
        queueTitle.value = service.queueTitle
        queuePlaylistId.value = service.queuePlaylistId
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        currentMediaItemIndex.value = player.currentMediaItemIndex
        repeatMode.value = player.repeatMode
    }

    fun playQueue(queue: Queue, replace: Boolean = true, title: String? = null) {
        service.playQueue(queue, replace = replace, title = title)
    }

    fun playNext(item: MediaItem) = playNext(listOf(item))
    fun playNext(items: List<MediaItem>) {
        service.playNext(items)
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))
    fun addToQueue(items: List<MediaItem>) {
        service.addToQueue(items)
    }

    fun toggleLike() {
        service.toggleLike()
    }

    fun toggleLibrary() {
        service.toggleLibrary()
    }

    override fun onPlaybackStateChanged(state: Int) {
        playbackState.value = state
        error.value = player.playerError
    }

    override fun onPlayWhenReadyChanged(newPlayWhenReady: Boolean, reason: Int) {
        playWhenReady.value = newPlayWhenReady
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        mediaMetadata.value = mediaItem?.metadata
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        queueWindows.value = player.getQueueWindows()
        queueTitle.value = service.queueTitle
        queuePlaylistId.value = service.queuePlaylistId
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    /**
     * Shuffles the queue
     */
    fun triggerShuffle() {
        val oldIndex = player.currentMediaItemIndex
        queueBoard.setCurrQueuePosIndex(oldIndex, service)

        // shuffle and update player playlist
        if (!isShuffleEnabled.value) {
            queueBoard.shuffleCurrent()
            queueBoard.getCurrentQueue()?.let { mq ->
                player.moveMediaItem(oldIndex, 0)
                val newItems = mq.getCurrentQueueShuffled()
                player.replaceMediaItems(1, Int.MAX_VALUE,
                    newItems.subList(1, newItems.size).map { it.toMediaItem() })
            }
        } else {
            val unshuffledPos = queueBoard.unShuffleCurrent()
            queueBoard.getCurrentQueue()?.let { mq ->
                player.moveMediaItem(oldIndex, unshuffledPos)
                val newItems = mq.getCurrentQueueShuffled()
                // replace items up to current playing, then replace items after current
                player.replaceMediaItems(0, unshuffledPos,
                    newItems.subList(0, unshuffledPos).map { it.toMediaItem() })
                player.replaceMediaItems(unshuffledPos + 1, Int.MAX_VALUE,
                    newItems.subList(unshuffledPos + 1, newItems.size).map { it.toMediaItem() })
            }
        }

        updateCanSkipPreviousAndNext()
        service.updateNotification()
    }

    override fun onRepeatModeChanged(mode: Int) {
        repeatMode.value = mode
        updateCanSkipPreviousAndNext()
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
        if (playbackError != null) {
            reportException(playbackError)
        }
        error.value = playbackError
    }

    private fun updateCanSkipPreviousAndNext() {
        if (!player.currentTimeline.isEmpty) {
            val window = player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
            canSkipPrevious.value = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    || !window.isLive()
                    || player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            canSkipNext.value = window.isLive() && window.isDynamic
                    || player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        } else {
            canSkipPrevious.value = false
            canSkipNext.value = false
        }
    }

    fun dispose() {
        player.removeListener(this)
    }

    companion object {
        var queueBoard = QueueBoard()
    }
}
