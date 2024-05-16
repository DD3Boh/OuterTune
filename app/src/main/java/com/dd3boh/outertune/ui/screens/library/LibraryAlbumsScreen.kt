package com.dd3boh.outertune.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.*
import com.dd3boh.outertune.ui.component.ChipsRow
import com.dd3boh.outertune.ui.component.LibraryAlbumGridItem
import com.dd3boh.outertune.ui.component.LibraryAlbumListItem
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.SortHeader
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.viewmodels.LibraryAlbumsViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryAlbumsScreen(
    navController: NavController,
    viewModel: LibraryAlbumsViewModel = hiltViewModel(),
    libraryFilterContent: @Composable() (() -> Unit)? = null,
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    var filter by rememberEnumPreference(AlbumFilterKey, AlbumFilter.LIKED)
    libraryFilterContent?.let { filter = AlbumFilter.LIKED }

    var viewTypeLocal by rememberEnumPreference(AlbumViewTypeKey, LibraryViewType.GRID)
    val libraryViewType by rememberEnumPreference(LibraryViewTypeKey, LibraryViewType.GRID)

    val viewType = if (libraryFilterContent != null) libraryViewType else viewTypeLocal

    val (sortType, onSortTypeChange) = rememberEnumPreference(AlbumSortTypeKey, AlbumSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(AlbumSortDescendingKey, true)

    LaunchedEffect(Unit) { viewModel.sync() }

    val albums by viewModel.allAlbums.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    val filterContent = @Composable {
        Row {
            ChipsRow(
                chips = listOf(
                    AlbumFilter.LIKED to stringResource(R.string.filter_liked),
                    AlbumFilter.LIBRARY to stringResource(R.string.filter_library)
                ),
                currentValue = filter,
                onValueUpdate = { filter = it },
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = {
                    viewTypeLocal = viewTypeLocal.toggle()
                },
                modifier = Modifier.padding(end = 6.dp)
            ) {
                Icon(
                    imageVector =
                        when (viewTypeLocal) {
                            LibraryViewType.LIST -> Icons.AutoMirrored.Rounded.List
                            LibraryViewType.GRID -> Icons.Rounded.GridView
                        },
                    contentDescription = null
                )
            }
        }
    }

    val headerContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            SortHeader(
                sortType = sortType,
                sortDescending = sortDescending,
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                sortTypeText = { sortType ->
                    when (sortType) {
                        AlbumSortType.CREATE_DATE -> R.string.sort_by_create_date
                        AlbumSortType.NAME -> R.string.sort_by_name
                        AlbumSortType.ARTIST -> R.string.sort_by_artist
                        AlbumSortType.YEAR -> R.string.sort_by_year
                        AlbumSortType.SONG_COUNT -> R.string.sort_by_song_count
                        AlbumSortType.LENGTH -> R.string.sort_by_length
                        AlbumSortType.PLAY_TIME -> R.string.sort_by_play_time
                    }
                }
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = pluralStringResource(R.plurals.n_album, albums.size, albums.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when (viewType) {
            LibraryViewType.LIST ->
                LazyColumn(
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                ) {
                    item(
                        key = "filter",
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        libraryFilterContent?.let { it() } ?: filterContent()
                    }

                    item(
                        key = "header",
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        headerContent()
                    }

                    items(
                        items = albums,
                        key = { it.id },
                        contentType = { CONTENT_TYPE_ALBUM }
                    ) { album ->
                        LibraryAlbumListItem(
                            navController = navController,
                            menuState = menuState,
                            album = album,
                            isActive = album.id == mediaMetadata?.album?.id,
                            isPlaying = isPlaying,
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }

            LibraryViewType.GRID ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                ) {
                    item(
                        key = "filter",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        libraryFilterContent?.let { it() } ?: filterContent()
                    }

                    item(
                        key = "header",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        headerContent()
                    }

                    items(
                        items = albums,
                        key = { it.id },
                        contentType = { CONTENT_TYPE_ALBUM }
                    ) { album ->
                        LibraryAlbumGridItem(
                            navController = navController,
                            menuState = menuState,
                            coroutineScope = coroutineScope,
                            album = album,
                            isActive = album.id == mediaMetadata?.album?.id,
                            isPlaying = isPlaying,
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
        }
    }
}
