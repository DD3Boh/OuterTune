package com.zionhuang.music.utils

import android.content.Context
import com.my.kizzy.data.rpc.KizzyRPC
import com.my.kizzy.data.rpc.RpcImage
import com.zionhuang.music.R
import com.zionhuang.music.db.entities.Song

class DiscordRPC(
    val context: Context,
    token: String,
) : KizzyRPC(token) {
    suspend fun updateSong(song: Song) = runCatching {
        setActivity(
            name = context.getString(R.string.app_name).removeSuffix(" Debug"),
            details = song.song.title,
            state = song.artists.joinToString { it.name },
            largeImage = song.song.thumbnailUrl?.let { RpcImage.ExternalImage(it) },
            smallImage = song.artists.firstOrNull()?.thumbnailUrl?.let { RpcImage.ExternalImage(it) },
            largeText = song.album?.title,
            smallText = song.artists.firstOrNull()?.name,
            buttons = listOf(
                "Listen on YouTube Music" to "https://music.youtube.com/watch?v=${song.song.id}",
                "Visit InnerTune" to "https://github.com/z-huang/InnerTune"
            ),
            type = Type.LISTENING,
            applicationId = APPLICATION_ID
        )
    }

    companion object {
        private const val APPLICATION_ID = "1271273225120125040"
    }
}