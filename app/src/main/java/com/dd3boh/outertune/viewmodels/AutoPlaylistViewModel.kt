package com.dd3boh.outertune.viewmodels

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd3boh.outertune.constants.SongSortType
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AutoPlaylistViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!

    val thumbnail: StateFlow<ImageVector> = MutableStateFlow(
        when (playlistId) {
            "liked" -> Icons.Rounded.Favorite
            else -> Icons.AutoMirrored.Rounded.QueueMusic
        }
    ).asStateFlow()

    val songs: StateFlow<List<Song>> = when (playlistId) {
        "liked" -> database.likedSongs(SongSortType.CREATE_DATE, true)
        else -> MutableStateFlow(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
