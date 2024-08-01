package com.dd3boh.outertune.ui.screens.settings

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.AccountChannelHandleKey
import com.dd3boh.outertune.constants.AccountEmailKey
import com.dd3boh.outertune.constants.AccountNameKey
import com.dd3boh.outertune.constants.DiscordNameKey
import com.dd3boh.outertune.constants.DiscordTokenKey
import com.dd3boh.outertune.constants.DiscordUsernameKey
import com.dd3boh.outertune.constants.InnerTubeCookieKey
import com.dd3boh.outertune.constants.VisitorDataKey
import com.dd3boh.outertune.ui.component.IconButton
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.utils.reportException
import com.my.kizzyrpc.KizzyRPC
import com.my.kizzyrpc.model.Activity
import com.my.kizzyrpc.model.Assets
import com.my.kizzyrpc.model.Metadata
import com.my.kizzyrpc.model.Timestamps
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readText
import io.ktor.http.ContentType.Application.Json
import kotlinx.coroutines.*

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun DiscordLoginScreen(
        navController: NavController,
) {

    var discordToken by rememberPreference(DiscordTokenKey, "")
    var discordUsername by rememberPreference(DiscordUsernameKey, "")
    var discordName by rememberPreference(DiscordNameKey, "")

    var webView: WebView? = null

    val url = "https://discord.com/login"
    AndroidView(
            factory = {
                WebView(it).apply {
                    layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    webViewClient = object : WebViewClient() {

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(
                                webView: WebView,
                                url: String,
                        ): Boolean {
                            stopLoading()
                            if (url.endsWith("/app")) {
                                loadUrl("javascript:alert((webpackChunkdiscord_app.push([[''],{},e=>{m=[];for(let c in e.c)m.push(e.c[c])}]),m).find(m=>m?.exports?.default?.getToken!==void 0).exports.default.getToken());")
                            }
                            return false
                        }
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.removeAllCookies(null)
                    cookieManager.flush()

                    WebStorage.getInstance().deleteAllData()
                    webChromeClient = object : WebChromeClient() {
                        override fun onJsAlert(
                                view: WebView,
                                url: String,
                                message: String,
                                result: JsResult,
                        ): Boolean {
                            discordToken = message

                            val client = HttpClient()
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val response: HttpResponse = client.get("https://discord.com/api/v9/users/@me") {
                                        headers {
                                            append("Authorization", message)
                                        }
                                    }
                                    val responseBody: String = response.bodyAsText()

                                    // Parse the JSON response
                                    val json = JSONObject(responseBody)
                                    discordUsername = json.getString("username")
                                    discordName = json.getString("global_name")



                                } catch (e: Exception) {
                                    println("Error: ${e.message}")
                                } finally {
                                    client.close()
                                }
                            }

                            navController::navigateUp
                            return true
                        }
                    }
                    loadUrl(url)
                }
            }
    )

    TopAppBar(
            title = { Text(stringResource(R.string.login)) },
            navigationIcon = {
                IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain
                ) {
                    Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                    )
                }
            }
    )

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}

