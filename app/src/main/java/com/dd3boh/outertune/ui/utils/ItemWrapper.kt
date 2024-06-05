package com.dd3boh.outertune.ui.utils

import androidx.compose.runtime.mutableStateOf

class ItemWrapper<T>(val item: T) {
    private val _isSelected = mutableStateOf(false)

    var isSelected: Boolean
        get() = _isSelected.value
        set(value) {
            _isSelected.value = value
        }
}
