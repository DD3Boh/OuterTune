package com.zionhuang.innertube.models.body

import com.zionhuang.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class EditPlaylistBody(
    val context: Context,
    val playlistId: String,
    val actions: List<Action>
) {
    @Serializable
    data class Action(
        val action: String = "ACTION_SET_PLAYLIST_NAME",
        val playlistName: String?,
        val addedVideoId: String? = null,
        val removedVideoId: String? = null,
        val setVideoId: String? = null,
    )
}