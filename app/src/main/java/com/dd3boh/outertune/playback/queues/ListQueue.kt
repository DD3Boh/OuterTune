package com.dd3boh.outertune.playback.queues

import androidx.media3.common.MediaItem
import com.dd3boh.outertune.models.MediaMetadata

class ListQueue(
    override val playlistId: String? = null,
    val title: String? = null,
    val items: List<MediaItem>,
    val startIndex: Int = 0,
    val position: Long = 0L,
) : Queue {
    override val preloadItem: MediaMetadata? = null
    override suspend fun getInitialStatus() = Queue.Status(title, items, startIndex, position)
    override fun hasNextPage(): Boolean = false
    override suspend fun nextPage() = throw UnsupportedOperationException()
}