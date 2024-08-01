package com.dd3boh.outertune.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.AccountChannelHandleKey
import com.dd3boh.outertune.constants.AccountEmailKey
import com.dd3boh.outertune.constants.AccountNameKey
import com.dd3boh.outertune.constants.DiscordTokenKey
import com.dd3boh.outertune.constants.DiscordUsernameKey
import com.dd3boh.outertune.constants.DiscordNameKey
import com.dd3boh.outertune.constants.EnableDiscordRPCKey
import com.dd3boh.outertune.constants.InnerTubeCookieKey
import com.dd3boh.outertune.constants.LanguageCodeToName
import com.dd3boh.outertune.constants.LyricTrimKey
import com.dd3boh.outertune.constants.ProxyEnabledKey
import com.dd3boh.outertune.constants.ProxyTypeKey
import com.dd3boh.outertune.constants.ProxyUrlKey
import com.dd3boh.outertune.constants.SYSTEM_DEFAULT
import com.dd3boh.outertune.constants.ShowArtistRPCKey
import com.dd3boh.outertune.ui.component.EditTextPreference
import com.dd3boh.outertune.ui.component.IconButton
import com.dd3boh.outertune.ui.component.ListPreference
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.zionhuang.innertube.utils.parseCookieString
import java.net.Proxy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordSettings(
        navController: NavController,
        scrollBehavior: TopAppBarScrollBehavior,
) {
    var discordToken by rememberPreference(DiscordTokenKey, "")
    var discordUsername by rememberPreference(DiscordUsernameKey, "")
    var discordName by rememberPreference(DiscordNameKey, "")

    val (discordRPC, onDiscordRPCChange) = rememberPreference(key = EnableDiscordRPCKey, defaultValue = true)
    val (showArtist, onShowArtistChange) = rememberPreference(key = ShowArtistRPCKey, defaultValue = true)


    var isLoggedIn = remember(discordToken) {
        discordToken != ""
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroupTitle(
                title = "ACCOUNT"
        )
        PreferenceEntry(
                title = { Text(if (isLoggedIn) discordName else stringResource(R.string.login)) },
                description = if (isLoggedIn) {
                    "@$discordUsername"
                } else null,
                icon = { Icon(painterResource(R.drawable.discord), null) },
                onClick = { navController.navigate("settings/discord/login") }
        )
        PreferenceEntry(
                title = { Text(stringResource(R.string.logout)) },
                description = null,
                icon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
                onClick = {
                    discordName = ""
                    discordToken = ""
                    discordUsername = ""
                    isLoggedIn = false

                }
        )

        PreferenceGroupTitle(
                title = "OPTIONS"
        )
        SwitchPreference(
                title = { Text(stringResource(R.string.enable_rpc)) },
                checked = discordRPC,
                onCheckedChange = onDiscordRPCChange,
                isEnabled = isLoggedIn
        )
        SwitchPreference(
                title = { Text(stringResource(R.string.show_artist_icon)) },
                description = stringResource(R.string.unstable_warning),
                icon = { Icon(Icons.Rounded.Person, null) },
                checked = showArtist,
                onCheckedChange = onShowArtistChange,
                isEnabled = isLoggedIn && discordRPC
        )
    }
    TopAppBar(
            title = { Text(stringResource(R.string.discord)) },
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
            },
            scrollBehavior = scrollBehavior
    )
}
