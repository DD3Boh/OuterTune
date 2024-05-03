package com.dd3boh.outertune.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.AutomaticScannerKey
import com.dd3boh.outertune.constants.ScannerSensitivity
import com.dd3boh.outertune.constants.ScannerSensitivityKey
import com.dd3boh.outertune.constants.ScannerStrictExtKey
import com.dd3boh.outertune.constants.ScannerType
import com.dd3boh.outertune.constants.ScannerTypeKey
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.ui.component.EnumListPreference
import com.dd3boh.outertune.ui.component.IconButton
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SwitchPreference

import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.ui.utils.scanLocal
import com.dd3boh.outertune.ui.utils.syncDB
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlayerSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    context: Context,
    database: MusicDatabase,
) {
    val mediaPermissionLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

    val coroutineScope = rememberCoroutineScope()
    var isScannerActive by remember { mutableStateOf(false) }
    var isScanFinished by remember { mutableStateOf(false) }
    var mediaPermission by remember { mutableStateOf(true) }

    val (scannerType, onScannerTypeChange) = rememberEnumPreference(
        key = ScannerTypeKey,
        defaultValue = ScannerType.MEDIASTORE
    )
    val (scannerSensitivity, onScannerSensitivityChange) = rememberEnumPreference(
        key = ScannerSensitivityKey,
        defaultValue = ScannerSensitivity.LEVEL_2
    )
    val (strictExtensions, onStrictExtensionsChange) = rememberPreference(ScannerStrictExtKey, defaultValue = false)
    val (autoScan, onAutoScanChange) = rememberPreference(AutomaticScannerKey, defaultValue = false)

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
    ) {
        // automatic scanner
        SwitchPreference(
            title = { Text(stringResource(R.string.auto_scanner_title)) },
            description = stringResource(R.string.auto_scanner_description),
            icon = { Icon(Icons.Rounded.Autorenew, null) },
            checked = autoScan,
            onCheckedChange = onAutoScanChange
        )


        PreferenceGroupTitle(
            title = stringResource(R.string.manual_scanner_title)
        )

        // scanner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically, // WHY WON'T YOU CENTER

        ) {
            Button(
                enabled = !isScannerActive,
                onClick = {
                    if (isScannerActive) {
                        return@Button
                    }

                    // check permission
                    if (context.checkSelfPermission(mediaPermissionLevel)
                        != PackageManager.PERMISSION_GRANTED) {

                        Toast.makeText(
                            context,
                            "The scanner requires storage permissions",
                            Toast.LENGTH_SHORT
                        ).show()

                        requestPermissions(context as Activity,
                            arrayOf(mediaPermissionLevel), PackageManager.PERMISSION_GRANTED
                        )

                        mediaPermission = false
                        return@Button
                    } else if (context.checkSelfPermission(mediaPermissionLevel)
                        == PackageManager.PERMISSION_GRANTED) {
                        mediaPermission = true
                    }

                    isScanFinished = false
                    isScannerActive = true

                    Toast.makeText(
                        context,
                        "Starting full library scan this may take a while...",
                        Toast.LENGTH_SHORT
                    ).show()
                    coroutineScope.launch(Dispatchers.IO) {
                        val directoryStructure = scanLocal(context, database, scannerType).value
                        syncDB(database, directoryStructure.toList(), scannerSensitivity, strictExtensions)

                        isScannerActive = false
                        isScanFinished = true
                    }
                }
            ) {
                Text(
                    text = if (isScannerActive) {
                        "Scanning..."
                    } else if (isScanFinished) {
                        "Scan complete"
                    } else if (!mediaPermission) {
                        "No Permission"
                    } else {
                        "Scan"
                    }
                )
            }


            // progress indicator
            if (!isScannerActive) {
                return@Row
            }

            // padding hax
            VerticalDivider(
                modifier = Modifier.padding(5.dp)
            )

            CircularProgressIndicator(
                modifier = Modifier
                    .size(32.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )

            Text(
                stringResource(R.string.scanner_warning),
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }


        PreferenceGroupTitle(
            title = stringResource(R.string.scanner_settings_title)
        )

        // scanner type
        EnumListPreference(
            title = { Text(stringResource(R.string.scanner_type_title)) },
            icon = { Icon(Icons.Rounded.Speed, null) },
            selectedValue = scannerType,
            onValueSelected = onScannerTypeChange,
            valueText = {
                when (it) {
                    ScannerType.MEDIASTORE -> stringResource(R.string.scanner_type_mediastore)
                    ScannerType.FFPROBEKIT_ASYNC -> stringResource(R.string.scanner_type_ffprobekit_async)
                    ScannerType.FFPROBEKIT_SYNC -> stringResource(R.string.scanner_type_ffprobekit_sync)
                }
            }
        )

        // scanner sensitivity
        EnumListPreference(
            title = { Text(stringResource(R.string.scanner_sensitivity_title)) },
            icon = { Icon(Icons.Rounded.GraphicEq, null) },
            selectedValue = scannerSensitivity,
            onValueSelected = onScannerSensitivityChange,
            valueText = {
                when (it) {
                    ScannerSensitivity.LEVEL_1 -> stringResource(R.string.scanner_sensitivity_L1)
                    ScannerSensitivity.LEVEL_2 -> stringResource(R.string.scanner_sensitivity_L2)
                    ScannerSensitivity.LEVEL_3 -> stringResource(R.string.scanner_sensitivity_L3)
                }
            }
        )


        // strict file ext
        SwitchPreference(
            title = { Text(stringResource(R.string.scanner_strict_file_name_title)) },
            description = stringResource(R.string.scanner_strict_file_name_description),
            icon = { Icon(Icons.Rounded.TextFields, null) },
            checked = strictExtensions,
            onCheckedChange = onStrictExtensionsChange
        )
    }




    TopAppBar(
        title = { Text(stringResource(R.string.local_player_settings_title)) },
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
