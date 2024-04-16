package com.dd3boh.outertune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.PlaylistItem
import com.dd3boh.outertune.utils.reportException
import com.zionhuang.innertube.utils.completedAlbumPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor() : ViewModel() {
    val playlists = MutableStateFlow<List<PlaylistItem>?>(null)
    val albums = MutableStateFlow<List<AlbumItem>?>(null)
    val artists = MutableStateFlow<List<ArtistItem>?>(null)

    init {
        viewModelScope.launch {
            YouTube.likedPlaylists().onSuccess {
                playlists.value = it
            }.onFailure {
                reportException(it)
            }
            YouTube.libraryAlbums().completedAlbumPage().onSuccess {
                albums.value = it.albums
            }.onFailure {
                reportException(it)
            }
            YouTube.libraryArtistsSubscriptions().onSuccess {
                artists.value = it
            }.onFailure {
                reportException(it)
            }
        }
    }
}
