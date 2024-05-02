package com.dd3boh.outertune.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dd3boh.outertune.constants.LibraryViewType
import com.dd3boh.outertune.constants.LibraryViewTypeKey
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.viewmodels.LibraryViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current

    var viewType by rememberEnumPreference(LibraryViewTypeKey, LibraryViewType.GRID)
}
