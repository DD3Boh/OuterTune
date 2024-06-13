package com.dd3boh.outertune.utils

import android.graphics.Bitmap

const val MAX_IMAGE_CACHE = 300 // max cached images to hold

/**
 * Cached image
 */
data class CachedBitmap(var path: String?, var image: Bitmap?, var resizedImage: Bitmap?)

var bitmapCache = ArrayDeque<CachedBitmap>()

/**
 * Retrieves an image from the cache
 */
fun retrieveImage(path: String): CachedBitmap? {
    return bitmapCache.firstOrNull {
        // don't listen to Kotlin, if you remove the null check, you break images.
        it?.path == path
    }
}

/**
 * Adds an image to the cache
 */
fun cache(path: String, image: Bitmap?, resize: Boolean) {
    if (image == null) {
        return
    }

    // adhere to limit
    if (bitmapCache.size >= MAX_IMAGE_CACHE) {
        bitmapCache.removeFirst()
    }

    val existingCached = retrieveImage(path)
    if (existingCached == null) {
        // add the image
        if (resize) {
            bitmapCache.addLast(CachedBitmap(path, null, image))
        } else {
            bitmapCache.addLast(CachedBitmap(path, image, null))
        }
    } else {
        if (resize) {
            existingCached.resizedImage = image
        } else {
            existingCached.image = image
        }
    }
}

/**
 * Removes all cached images
 */
fun purgeCache() {
    bitmapCache = ArrayDeque()
}