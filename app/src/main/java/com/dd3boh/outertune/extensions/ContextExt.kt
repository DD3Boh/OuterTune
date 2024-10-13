package com.dd3boh.outertune.extensions

import android.content.Context
import com.dd3boh.outertune.constants.InnerTubeCookieKey
import com.dd3boh.outertune.constants.YtmSyncKey
import com.dd3boh.outertune.utils.dataStore
import com.zionhuang.innertube.utils.parseCookieString
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

fun Context.isSyncEnabled(): Boolean {
    return runBlocking {
        val ytmSync = dataStore.data.map { it[YtmSyncKey] ?: true }.first()
        val cookie = dataStore.data.map { it[InnerTubeCookieKey] ?: "" }.first()
        ytmSync && "SAPISID" in parseCookieString(cookie)
    }
}