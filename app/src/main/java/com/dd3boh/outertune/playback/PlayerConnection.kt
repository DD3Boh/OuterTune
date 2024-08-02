package com.dd3boh.outertune.playback
import android.content.Context
import android.annotation.SuppressLint
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import com.dd3boh.outertune.constants.DiscordTokenKey
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.extensions.currentMetadata
import com.dd3boh.outertune.extensions.getCurrentQueueIndex
import com.dd3boh.outertune.extensions.getQueueWindows
import com.dd3boh.outertune.extensions.metadata
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
import kotlinx.coroutines.flow.stateIn
import com.dd3boh.outertune.constants.PlayerVolumeKey
import com.dd3boh.outertune.extensions.mediaItems
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.get
import kotlinx.coroutines.FlowPreview

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PlayerConnection(
    binder: MusicBinder,
    val database: MusicDatabase,
    scope: CoroutineScope,
    ctx: Context
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
    val ctx = ctx
    var previousSong = ""

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
        if (previousSong != mediaItem?.mediaId.toString()) {
            createDiscordRPC(player = player, ctx = ctx)
        }
        previousSong = mediaItem?.mediaId.toString()
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
        queueBoard.setCurrQueuePosIndex(player.currentMediaItemIndex)

        // actual shuffling
        val newQueuePos = if (!isShuffleEnabled.value) {
            queueBoard.shuffleCurrent()
        } else {
            queueBoard.unShuffleCurrent()
        }
        val pos = player.currentPosition
        // load into player
        queueBoard.setCurrQueue(this, false)
        queueBoard.getCurrentQueue()?.let {
            player.seekTo(newQueuePos, pos)
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
