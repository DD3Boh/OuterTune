package com.dd3boh.outertune.ui.screens.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.DarkModeKey
import com.dd3boh.outertune.constants.DefaultOpenTabKey
import com.dd3boh.outertune.constants.DefaultOpenTabNewKey
import com.dd3boh.outertune.constants.DialogCornerRadius
import com.dd3boh.outertune.constants.DynamicThemeKey
import com.dd3boh.outertune.constants.EnabledTabsKey
import com.dd3boh.outertune.constants.FlatSubfoldersKey
import com.dd3boh.outertune.constants.NewInterfaceKey
import com.dd3boh.outertune.constants.PlayerBackgroundStyleKey
import com.dd3boh.outertune.constants.PureBlackKey
import com.dd3boh.outertune.constants.ThumbnailCornerRadius
import com.dd3boh.outertune.extensions.move
import com.dd3boh.outertune.ui.component.EnumListPreference
import com.dd3boh.outertune.ui.component.IconButton
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.utils.decodeTabString
import com.dd3boh.outertune.utils.encodeTabString
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

/**
 * H: Home
 * S: Songs
 * F: Folders
 * A: Artists
 * B: Albums
 * L: Playlists
 *
 * Not/won't implement
 * P: Player
 * Q: Queue
 * E: Search
 */
const val DEFAULT_ENABLED_TABS = "HSABLF"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (dynamicTheme, onDynamicThemeChange) = rememberPreference(DynamicThemeKey, defaultValue = true)
    val (playerBackground, onPlayerBackgroundChange) = rememberEnumPreference(key = PlayerBackgroundStyleKey, defaultValue = PlayerBackgroundStyle.DEFAULT)
    val (darkMode, onDarkModeChange) = rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val (pureBlack, onPureBlackChange) = rememberPreference(PureBlackKey, defaultValue = false)
    val (enabledTabs, onEnabledTabsChange) = rememberPreference(EnabledTabsKey, defaultValue = DEFAULT_ENABLED_TABS)
    val (defaultOpenTab, onDefaultOpenTabChange) = rememberEnumPreference(DefaultOpenTabKey, defaultValue = NavigationTab.HOME)
    val (defaultOpenTabNew, onDefaultOpenTabNewChange) = rememberEnumPreference(DefaultOpenTabNewKey, defaultValue = NavigationTabNew.HOME)
    val (newInterfaceStyle, onNewInterfaceStyleChange) = rememberPreference(key = NewInterfaceKey, defaultValue = true)
    val (flatSubfolders, onFlatSubfoldersChange) = rememberPreference(FlatSubfoldersKey, defaultValue = true)

    val availableBackgroundStyles = PlayerBackgroundStyle.entries.filter {
        it != PlayerBackgroundStyle.BLUR || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    // configurable tabs
    var showTabArrangement by rememberSaveable {
        mutableStateOf(false)
    }
    val mutableTabs = remember { mutableStateListOf<NavigationTab>() }
    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            mutableTabs.move(from.index, to.index)
        }
    )

    fun updateTabs() {
        mutableTabs.apply {
            clear()

            val enabled = decodeTabString(enabledTabs)
            addAll(enabled)
            add(NavigationTab.NULL)
            addAll(NavigationTab.entries.filter { item -> enabled.none { it == item || item == NavigationTab.NULL } })
        }
    }

    LaunchedEffect(showTabArrangement) {
        updateTabs()
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroupTitle(
            title = "Theme"
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_dynamic_theme)) },
            icon = { Icon(Icons.Rounded.Palette, null) },
            checked = dynamicTheme,
            onCheckedChange = onDynamicThemeChange
        )
        EnumListPreference(
            title = { Text(stringResource(R.string.player_background_style)) },
            icon = { Icon(Icons.Rounded.BlurOn, null) },
            selectedValue = playerBackground,
            onValueSelected = onPlayerBackgroundChange,
            valueText = {
                when (it) {
                    PlayerBackgroundStyle.DEFAULT -> stringResource(R.string.player_background_default)
                    PlayerBackgroundStyle.GRADIENT -> stringResource(R.string.player_background_gradient)
                    PlayerBackgroundStyle.BLUR -> stringResource(R.string.player_background_blur)
                }
            },
            values = availableBackgroundStyles
        )
        EnumListPreference(
            title = { Text(stringResource(R.string.dark_theme)) },
            icon = { Icon(Icons.Rounded.DarkMode, null) },
            selectedValue = darkMode,
            onValueSelected = onDarkModeChange,
            valueText = {
                when (it) {
                    DarkMode.ON -> stringResource(R.string.dark_theme_on)
                    DarkMode.OFF -> stringResource(R.string.dark_theme_off)
                    DarkMode.AUTO -> stringResource(R.string.dark_theme_follow_system)
                }
            }
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.pure_black)) },
            icon = { Icon(Icons.Rounded.Contrast, null) },
            checked = pureBlack,
            onCheckedChange = onPureBlackChange
        )

        PreferenceGroupTitle(
            title = "Layout"
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.new_interface)) },
            icon = { Icon(Icons.Rounded.Palette, null) },
            checked = newInterfaceStyle,
            onCheckedChange = onNewInterfaceStyleChange
        )

        AnimatedVisibility(visible = !newInterfaceStyle) {
            PreferenceEntry(
                title = { Text("Tab arrangement") },
                icon = { Icon(Icons.Rounded.Reorder, null) },
                onClick = {
                    showTabArrangement = true
                }
            )

            if (showTabArrangement)
                BasicAlertDialog(
                    onDismissRequest = {
                        showTabArrangement = false
                    },
                    content = {
                        Column(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.background,
                                    RoundedCornerShape(DialogCornerRadius)
                                )
                                .padding(16.dp)
                        ) {
                            // main content
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Arrange tabs",
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                }
                                // tabs list
                                LazyColumn(
                                    state = reorderableState.listState,
                                    modifier = Modifier
                                        .padding(vertical = 12.dp)
                                        .border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            RoundedCornerShape(ThumbnailCornerRadius)
                                        )
                                        .reorderable(reorderableState)
                                ) {
                                    itemsIndexed(
                                        items = mutableTabs,
                                        key = { _, item -> item.hashCode() }
                                    ) { index, tab ->
                                        ReorderableItem(
                                            reorderableState = reorderableState,
                                            key = tab.hashCode()
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .padding(horizontal = 24.dp, vertical = 8.dp)
                                                    .fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = when (tab) {
                                                        NavigationTab.HOME -> stringResource(R.string.home)
                                                        NavigationTab.SONG -> stringResource(R.string.songs)
                                                        NavigationTab.FOLDERS -> stringResource(R.string.folders)
                                                        NavigationTab.ARTIST -> stringResource(R.string.artists)
                                                        NavigationTab.ALBUM -> stringResource(R.string.albums)
                                                        NavigationTab.PLAYLIST -> stringResource(R.string.playlists)
                                                        else -> { "--- Drag below here to disable ---" }
                                                    }
                                                )
                                                Icon(
                                                    imageVector = Icons.Rounded.DragHandle,
                                                    contentDescription = null,
                                                    modifier = Modifier.detectReorder(reorderableState)
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "The Home tab is required.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }

                            // bottom options
                            Row {
                                Row(modifier = Modifier.weight(1f)) {
                                    TextButton(
                                        onClick = {
                                            onEnabledTabsChange(DEFAULT_ENABLED_TABS)
                                            updateTabs()
                                        },
                                    ) {
                                        Text(stringResource(R.string.reset))
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        var encoded = encodeTabString(mutableTabs)

                                        // reset defaultOpenTab if it got disabled
                                        if (!mutableTabs.contains(defaultOpenTab)) {
                                            onDefaultOpenTabChange(NavigationTab.HOME)
                                        }

                                        // home is required
                                        if (!encoded.contains('H')) {
                                            encoded += "H"
                                        }

                                        onEnabledTabsChange(encoded)
                                        showTabArrangement = false
                                    }
                                ) {
                                    Text(stringResource(android.R.string.ok))
                                }

                                TextButton(
                                    onClick = {
                                        showTabArrangement = false
                                    }
                                ) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                            }
                        }
                    }
                )
        }
        if (newInterfaceStyle) {
            EnumListPreference(
                title = { Text(stringResource(R.string.default_open_tab)) },
                icon = { Icon(Icons.Rounded.Tab, null) },
                selectedValue = defaultOpenTabNew,
                onValueSelected = onDefaultOpenTabNewChange,
                valueText = {
                    when (it) {
                        NavigationTabNew.HOME -> stringResource(R.string.home)
                        NavigationTabNew.LIBRARY -> stringResource(R.string.library)
                    }
                }
            )
        } else {
            EnumListPreference(
                title = { Text(stringResource(R.string.default_open_tab)) },
                icon = { Icon(Icons.Rounded.Tab, null) },
                selectedValue = defaultOpenTab,
                onValueSelected = onDefaultOpenTabChange,
                valueText = {
                    when (it) {
                        NavigationTab.HOME -> stringResource(R.string.home)
                        NavigationTab.SONG -> stringResource(R.string.songs)
                        NavigationTab.FOLDERS -> stringResource(R.string.folders)
                        NavigationTab.ARTIST -> stringResource(R.string.artists)
                        NavigationTab.ALBUM -> stringResource(R.string.albums)
                        NavigationTab.PLAYLIST -> stringResource(R.string.playlists)
                        else -> { "" }
                    }
                }
            )
        }
    }

    // flatten subfolders
    SwitchPreference(
        title = { Text(stringResource(R.string.flat_subfolders_title)) },
        description = stringResource(R.string.flat_subfolders_description),
        icon = { Icon(Icons.Rounded.FolderCopy, null) },
        checked = flatSubfolders,
        onCheckedChange = onFlatSubfoldersChange
    )

    TopAppBar(
        title = { Text(stringResource(R.string.appearance)) },
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

enum class DarkMode {
    ON, OFF, AUTO
}

enum class PlayerBackgroundStyle {
    DEFAULT, GRADIENT, BLUR
}

/**
 * NULL is used to separate enabled and disabled tabs. It should be ignored in regular use
 */
enum class NavigationTab {
    HOME, SONG, FOLDERS, ARTIST, ALBUM, PLAYLIST, NULL
}
enum class NavigationTabNew {
    HOME, LIBRARY
}

enum class LyricsPosition {
    LEFT, CENTER, RIGHT
}