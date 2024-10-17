package com.dd3boh.outertune.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.dd3boh.outertune.R
import kotlin.math.absoluteValue

fun formatFileSize(sizeBytes: Long): String {
    val prefix = if (sizeBytes < 0) "-" else ""
    var result: Long = sizeBytes.absoluteValue
    var suffix = "B"
    if (result > 900) {
        suffix = "KB"
        result /= 1024
    }
    if (result > 900) {
        suffix = "MB"
        result /= 1024
    }
    if (result > 900) {
        suffix = "GB"
        result /= 1024
    }
    if (result > 900) {
        suffix = "TB"
        result /= 1024
    }
    if (result > 900) {
        suffix = "PB"
        result /= 1024
    }
    return "$prefix$result $suffix"
}

@Composable
fun getNSongsString(songCount :Int, downloadCount: Int = 0): String {
    return if (downloadCount > 0)
        "$downloadCount / " + pluralStringResource(R.plurals.n_song, songCount, songCount)
    else
        pluralStringResource(R.plurals.n_song, songCount, songCount)
}