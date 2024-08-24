package com.dd3boh.outertune.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.NoCell
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.AudioNormalizationKey
import com.dd3boh.outertune.constants.AudioQuality
import com.dd3boh.outertune.constants.AudioQualityKey
import com.dd3boh.outertune.constants.KeepAliveKey
import com.dd3boh.outertune.constants.PersistentQueueKey
import com.dd3boh.outertune.constants.SkipOnErrorKey
import com.dd3boh.outertune.constants.SkipSilenceKey
import com.dd3boh.outertune.constants.SwipeToDismissKey
import com.dd3boh.outertune.constants.minPlaybackDurKey
import com.dd3boh.outertune.playback.KeepAlive
import com.dd3boh.outertune.ui.component.ActionPromptDialog
import com.dd3boh.outertune.ui.component.EnumListPreference
import com.dd3boh.outertune.ui.component.IconButton
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.utils.reportException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current

    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(key = AudioQualityKey, defaultValue = AudioQuality.AUTO)
    val (persistentQueue, onPersistentQueueChange) = rememberPreference(key = PersistentQueueKey, defaultValue = true)
    val (swipeToDismiss, onSwipeToDismissChange) = rememberPreference(key = SwipeToDismissKey, defaultValue = true)
    val (skipSilence, onSkipSilenceChange) = rememberPreference(key = SkipSilenceKey, defaultValue = false)
    val (skipOnErrorKey, onSkipOnErrorChange) = rememberPreference(key = SkipOnErrorKey, defaultValue = true)
    val (audioNormalization, onAudioNormalizationChange) = rememberPreference(key = AudioNormalizationKey, defaultValue = true)
    val (minPlaybackDur, onMinPlaybackDurChange) = rememberPreference(minPlaybackDurKey, defaultValue = 30)
    val (keepAlive, onKeepAliveChange) = rememberPreference(key = KeepAliveKey, defaultValue = false)

    var showMinPlaybackDur by remember {
        mutableStateOf(false)
    }
    var tempminPlaybackDur by remember {
        mutableIntStateOf(minPlaybackDur)
    }

    fun toggleKeepAlive(newValue: Boolean) {
        // disable and request if disabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            onKeepAliveChange(false)
            Toast.makeText(
                context,
                "Notification permission is required",
                Toast.LENGTH_SHORT
            ).show()

            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf( Manifest.permission.POST_NOTIFICATIONS), PackageManager.PERMISSION_GRANTED
            )
            return
        }

        if (keepAlive != newValue) {
            onKeepAliveChange(newValue)
            // start/stop service accordingly
            if (newValue) {
                try {
                    context.startService(Intent(context, KeepAlive::class.java))
                } catch (e: Exception) {
                    reportException(e)
                }
            } else {
                try {
                    context.stopService(Intent(context, KeepAlive::class.java))
                } catch (e: Exception) {
                    reportException(e)
                }
            }
        }
    }

    // reset if no permission
    LaunchedEffect(keepAlive) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            onKeepAliveChange(false)
        }
    }


    if (showMinPlaybackDur) {
        ActionPromptDialog(
            title = "Minimum playback duration",
            onDismiss = { showMinPlaybackDur = false },
            onConfirm = {
                showMinPlaybackDur = false
                onMinPlaybackDurChange(tempminPlaybackDur)
            },
            onCancel = {
                showMinPlaybackDur = false
                tempminPlaybackDur = minPlaybackDur
            }
        ) {
            Text(
                text = "The minimum amount of a song that must be played before it is considered \"played\"",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${tempminPlaybackDur}%",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Slider(
                    value = tempminPlaybackDur.toFloat(),
                    onValueChange = { tempminPlaybackDur = it.toInt() },
                    valueRange = 0f..100f
                )
            }
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroupTitle(
            title = "Interface"
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.persistent_queue)) },
            icon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
            checked = persistentQueue,
            onCheckedChange = onPersistentQueueChange
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.swipe_to_dismiss_title)) },
            description = stringResource(R.string.swipe_to_dismiss_description),
            icon = { Icon(Icons.Rounded.PlaylistRemove, null) },
            checked = swipeToDismiss,
            onCheckedChange = onSwipeToDismissChange
        )
        // lyrics settings
        PreferenceEntry(
            title = { Text(stringResource(R.string.lyrics_settings_title)) },
            icon = { Icon(Icons.Rounded.Lyrics, null) },
            onClick = { navController.navigate("settings/player/lyrics") }
        )
        PreferenceEntry(
            title = { Text("Minimum playback duration") },
            icon = { Icon(Icons.Rounded.Sync, null) },
            onClick = { showMinPlaybackDur = true }
        )

        PreferenceGroupTitle(
            title = "Audio"
        )
        EnumListPreference(
            title = { Text(stringResource(R.string.audio_quality)) },
            icon = { Icon(Icons.Rounded.GraphicEq, null) },
            selectedValue = audioQuality,
            onValueSelected = onAudioQualityChange,
            valueText = {
                when (it) {
                    AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                    AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                    AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
                }
            }
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.audio_normalization)) },
            icon = { Icon(Icons.AutoMirrored.Rounded.VolumeUp, null) },
            checked = audioNormalization,
            onCheckedChange = onAudioNormalizationChange
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.skip_silence)) },
            icon = { Icon(painterResource(R.drawable.skip_next), null) },
            checked = skipSilence,
            onCheckedChange = onSkipSilenceChange
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.skip_on_error)) },
            icon = { Icon(Icons.Rounded.FastForward, null) },
            checked = skipOnErrorKey,
            onCheckedChange = onSkipOnErrorChange
        )

        PreferenceGroupTitle(
            title = "Advanced"
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.keep_alive_title)) },
            description = stringResource(R.string.keep_alive_description),
            icon = { Icon(Icons.Rounded.NoCell, null) },
            checked = keepAlive,
            onCheckedChange = { toggleKeepAlive(it) }
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.player_and_audio)) },
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
