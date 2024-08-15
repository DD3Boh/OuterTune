/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * ApiService.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */
package com.my.kizzy.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Modified by Zion Huang
 */
class ApiService {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }

    suspend fun getImage(url: String) = client.get {
        url("$baseUrl/image")
        parameter("url", url)
    }

    suspend fun uploadImage(file: File) = client.post {
        url("$baseUrl/upload")
        setBody(MultiPartFormDataContent(
            formData {
                append("temp", file.readBytes(), Headers.build {
                    append(HttpHeaders.ContentType, "image/*")
                    append(HttpHeaders.ContentDisposition, "filename=${file.name}")
                })
            }
        ))
    }

    companion object {
        const val baseUrl = "https://kizzyapi-1-z9614716.deta.app"
    }
}
