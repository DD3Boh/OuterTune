package uk.akane.libphonograph.items

import android.net.Uri
import androidx.core.os.BundleCompat
import androidx.media3.common.MediaMetadata


const val EXTRA_AUTHOR = "Author"
const val EXTRA_ARTIST_ID = "ArtistId"
const val EXTRA_ALBUM_ID = "AlbumId"
const val EXTRA_ALBUM_YEAR = "AlbumYear"
const val EXTRA_ADD_DATE = "AddDate"
const val EXTRA_MODIFIED_DATE = "ModifiedDate"
const val EXTRA_CD_TRACK_NUMBER = "CdTrackNumber"
const val EXTRA_HD_ARTWORK_URI = "HdArtworkUri"
const val EXTRA_FILE = "File"

val MediaMetadata.author: String?
    get() = extras?.getString(EXTRA_AUTHOR)

val MediaMetadata.artistId: Long?
    get() = extras?.getLong(EXTRA_ARTIST_ID, -1).let { if (it == -1L) null else it }

val MediaMetadata.albumId: Long?
    get() = extras?.getLong(EXTRA_ALBUM_ID, -1).let { if (it == -1L) null else it }

val MediaMetadata.albumYear: Long?
    get() = extras?.getLong(EXTRA_ALBUM_YEAR, -1).let { if (it == -1L) null else it }

val MediaMetadata.addDate: Long?
    get() = extras?.getLong(EXTRA_ADD_DATE, -1).let { if (it == -1L) null else it }

val MediaMetadata.modifiedDate: Long?
    get() = extras?.getLong(EXTRA_MODIFIED_DATE, -1).let { if (it == -1L) null else it }

val MediaMetadata.cdTrackNumber: String?
    get() = extras?.getString(EXTRA_CD_TRACK_NUMBER)
