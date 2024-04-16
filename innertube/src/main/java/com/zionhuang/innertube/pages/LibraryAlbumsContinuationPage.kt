package com.zionhuang.innertube.pages

import com.zionhuang.innertube.models.AlbumItem

data class LibraryAlbumsContinuationPage(
    val albums: List<AlbumItem>,
    val continuation: String?,
)