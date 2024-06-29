package com.dd3boh.outertune.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.GridThumbnailHeight
import com.dd3boh.outertune.ui.component.IconButton
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.YouTubeGridItem
import com.dd3boh.outertune.ui.component.shimmer.GridItemPlaceHolder
import com.dd3boh.outertune.ui.component.shimmer.ShimmerHost
import com.dd3boh.outertune.ui.menu.YouTubeAlbumMenu
import com.dd3boh.outertune.ui.menu.YouTubeArtistMenu
import com.dd3boh.outertune.ui.menu.YouTubePlaylistMenu
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.viewmodels.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current

    val coroutineScope = rememberCoroutineScope()

    val playlists by viewModel.playlists.collectAsState()

    val albums by viewModel.albums.collectAsState()

    val artists by viewModel.artists.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    ) {
        items(
            items = playlists.orEmpty(),
            key = { it.id }
        ) { item ->
            YouTubeGridItem(
                item = item,
                fillMaxWidth = true,
                modifier = Modifier
                    .combinedClickable(
                        onClick = {
                            navController.navigate("online_playlist/${item.id}")
                        },
                        onLongClick = {
                            menuState.show {
                                YouTubePlaylistMenu(
                                    playlist = item,
                                    coroutineScope = coroutineScope,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
            )
        }

        items(
            items = albums.orEmpty(),
            key = { it.id }
        ) { item ->
            YouTubeGridItem(
                item = item,
                fillMaxWidth = true,
                modifier = Modifier
                    .combinedClickable(
                        onClick = {
                            navController.navigate("album/${item.id}")
                        },
                        onLongClick = {
                            menuState.show {
                                YouTubeAlbumMenu(
                                    albumItem = item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
            )
        }

        items(
            items = artists.orEmpty(),
            key = { it.id }
        ) { item ->
            YouTubeGridItem(
                item = item,
                fillMaxWidth = true,
                modifier = Modifier
                    .combinedClickable(
                        onClick = {
                            navController.navigate("artist/${item.id}")
                        },
                        onLongClick = {
                            menuState.show {
                                YouTubeArtistMenu(
                                    artist = item,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
            )
        }

        if (playlists == null) {
            items(8) {
                ShimmerHost {
                    GridItemPlaceHolder(fillMaxWidth = true)
                }
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.account)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}
