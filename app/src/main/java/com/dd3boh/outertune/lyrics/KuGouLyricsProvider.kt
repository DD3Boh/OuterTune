package com.dd3boh.outertune.lyrics

import android.content.Context
import com.zionhuang.kugou.KuGou
import com.dd3boh.outertune.constants.EnableKugouKey
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.get

object KuGouLyricsProvider : LyricsProvider {
    override val name = "Kugou"
    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableKugouKey] ?: true

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): Result<String> =
        KuGou.getLyrics(title, artist, duration)

    override suspend fun getAllLyrics(id: String, title: String, artist: String, duration: Int, callback: (String) -> Unit) {
        KuGou.getAllPossibleLyricsOptions(title, artist, duration, callback)
    }
}
