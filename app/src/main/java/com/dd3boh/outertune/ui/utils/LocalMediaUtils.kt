package com.dd3boh.outertune.ui.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import com.dd3boh.outertune.models.DirectoryTree
import com.dd3boh.outertune.utils.cache
import com.dd3boh.outertune.utils.retrieveImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

const val TAG = "LocalMediaUtils"

/**
 * For easier debugging, set SCANNER_CRASH_AT_FIRST_ERROR to stop at first error
 */
const val SCANNER_CRASH_AT_FIRST_ERROR = false // crash at ffprobe errors only
const val SYNC_SCANNER = false // true will not use multithreading for scanner
const val MAX_CONCURRENT_JOBS = 16
const val SCANNER_DEBUG = false

@OptIn(ExperimentalCoroutinesApi::class)
val scannerSession = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_JOBS)

// stuff to make this work
const val storageRoot = "/storage/"
const val DEFAULT_SCAN_PATH = "/tree/primary:Music\n"
val ARTIST_SEPARATORS = Regex("\\s*;\\s*|\\s*ft\\.\\s*|\\s*feat\\.\\s*|\\s*&\\s*", RegexOption.IGNORE_CASE)
private var cachedDirectoryTree: DirectoryTree? = null


// useful metadata
val projection = arrayOf(
    MediaStore.Audio.Media._ID,
    MediaStore.Audio.Media.DISPLAY_NAME,
    MediaStore.Audio.Media.TITLE,
    MediaStore.Audio.Media.DURATION,
    MediaStore.Audio.Media.ARTIST,
    MediaStore.Audio.Media.ARTIST_ID,
    MediaStore.Audio.Media.ALBUM,
    MediaStore.Audio.Media.ALBUM_ID,
    MediaStore.Audio.Media.DATE_MODIFIED,
    MediaStore.Audio.Media.YEAR,
//    MediaStore.Audio.Media.GENRE, // Need API R
    MediaStore.Audio.Media.RELATIVE_PATH,
    MediaStore.Audio.Media.VOLUME_NAME,
    MediaStore.Audio.Media.MIME_TYPE,
    MediaStore.Audio.Media.BITRATE,
    MediaStore.Audio.Media.SIZE,
)


/**
 * ==========================
 * Various misc helpers
 * ==========================
 */


/**
 * Extract the album art from the audio file. The image is not resized
 * (did you mean to use getLocalThumbnail(path: String?, resize: Boolean)?).
 *
 * @param path Full path of audio file
 */
fun getLocalThumbnail(path: String?): Bitmap? = getLocalThumbnail(path, false)

/**
 * Extract the album art from the audio file
 *
 * @param path Full path of audio file
 * @param resize Whether to resize the Bitmap to a thumbnail size (300x300)
 */
fun getLocalThumbnail(path: String?, resize: Boolean): Bitmap? {
    if (path == null) {
        return null
    }
    // try cache lookup
    val cachedImage = if (resize) {
        retrieveImage(path)?.resizedImage
    } else {
        retrieveImage(path)?.image
    }

    if (cachedImage == null) {
//        Timber.tag(TAG).d("Cache miss on $path")
    } else {
        return cachedImage
    }

    val mData = MediaMetadataRetriever()
    mData.setDataSource(path)

    var image: Bitmap = try {
        val art = mData.embeddedPicture
        BitmapFactory.decodeByteArray(art, 0, art!!.size)
    } catch (e: Exception) {
        cache(path, null, resize)
        null
    } ?: return null

    if (resize) {
        image = Bitmap.createScaledBitmap(image, 300, 300, false)
    }

    cache(path, image, resize)
    return image
}


/**
 * Get cached DirectoryTree
 */
fun getDirectoryTree(): DirectoryTree? {
    if (cachedDirectoryTree == null) {
        return null
    }
    return cachedDirectoryTree
}

/**
 * Cache a DirectoryTree
 */
fun cacheDirectoryTree(new: DirectoryTree?) {
    cachedDirectoryTree = new
}