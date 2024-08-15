/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * KizzyRPC.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.rpc

import com.my.kizzy.data.gateway.DiscordWebSocket
import com.my.kizzy.data.gateway.entities.presence.Activity
import com.my.kizzy.data.gateway.entities.presence.Assets
import com.my.kizzy.data.gateway.entities.presence.Metadata
import com.my.kizzy.data.gateway.entities.presence.Presence
import com.my.kizzy.data.gateway.entities.presence.Timestamps
import com.my.kizzy.data.repository.KizzyRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import org.json.JSONObject

/**
 * Modified by Zion Huang
 */
open class KizzyRPC(token: String) {
    private val kizzyRepository = KizzyRepository()
    private val discordWebSocket = DiscordWebSocket(token)

    fun closeRPC() {
        discordWebSocket.close()
    }

    fun isRpcRunning(): Boolean {
        return discordWebSocket.isWebSocketConnected()
    }

    suspend fun setActivity(
        name: String,
        state: String?,
        details: String?,
        largeImage: RpcImage?,
        smallImage: RpcImage?,
        largeText: String? = null,
        smallText: String? = null,
        buttons: List<Pair<String, String>>? = null,
        timestamps: Timestamps? = null,
        type: Type = Type.LISTENING,
        streamUrl: String? = null,
        applicationId: String? = null,
        status: String? = "online",
    ) {
        if (!isRpcRunning()) {
            discordWebSocket.connect()
        }
        val presence = Presence(
            activities = listOf(
                Activity(
                    name = name,
                    state = state,
                    details = details,
                    type = type.value,
                    timestamps = timestamps,
                    assets = Assets(
                        largeImage = largeImage?.resolveImage(kizzyRepository),
                        smallImage = smallImage?.resolveImage(kizzyRepository),
                        largeText = largeText,
                        smallText = smallText
                    ),
                    buttons = buttons?.map { it.first },
                    metadata = Metadata(buttonUrls = buttons?.map { it.second }),
                    applicationId = applicationId.takeIf { !buttons.isNullOrEmpty() },
                    url = streamUrl
                )
            ),
            afk = true,
            since = timestamps?.start ?: System.currentTimeMillis(),
            status = status ?: "online"
        )
        discordWebSocket.sendActivity(presence)
    }

    enum class Type(val value: Int) {
        PLAYING(0),
        STREAMING(1),
        LISTENING(2),
        WATCHING(3),
        COMPETING(5)
    }

    companion object {
        suspend fun getUserInfo(token: String): Result<UserInfo> = runCatching {
            val client = HttpClient()
            val response = client.get("https://discord.com/api/v9/users/@me") {
                header("Authorization", token)
            }.bodyAsText()
            val json = JSONObject(response)
            val username = json.getString("username")
            val name = json.getString("global_name")
            client.close()

            UserInfo(username, name)
        }
    }
}
