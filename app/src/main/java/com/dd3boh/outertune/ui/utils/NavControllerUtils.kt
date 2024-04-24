package com.dd3boh.outertune.ui.utils

import androidx.compose.ui.util.fastAny
import androidx.navigation.NavController
import com.dd3boh.outertune.ui.screens.Screens

val NavController.canNavigateUp: Boolean
    get() = currentBackStackEntry?.destination?.parent?.route != null

fun NavController.backToMain() {
    while (canNavigateUp && !Screens.MainScreens.fastAny { it.route == currentBackStackEntry?.destination?.route }) {
        navigateUp()
    }
}