package com.dd3boh.outertune.utils

import android.graphics.Bitmap

/**
 * Cached image
 */
class CachedBitmap(var path: String?, var image: Bitmap?, var resizedImage: Bitmap?)

/**
 * TODO: Fix the root cause of the miniplayer constantly needing reloading
 * TODO: Clear the cache on library re-scan
 */
// memory leak? who cares? speed is king!
var bitmapCache = ArrayList<CachedBitmap>()

/**
 * Retrieves an image from the cache
 */
fun retrieveImage(path: String): CachedBitmap? {
    return bitmapCache.firstOrNull() { it.path == path }
}

/**
 * Adds an image to the cache
 */
fun cache(path: String, image: Bitmap?, resize: Boolean) {
    if (image == null) {
        return
    }

    val existingCached = retrieveImage(path)
    if (existingCached == null) {
        // add the image
        if (resize) {
            bitmapCache.add(CachedBitmap(path, null, image))
        } else {
            bitmapCache.add(CachedBitmap(path, image, null))
        }
    } else {
        if (resize) {
            existingCached.resizedImage = image
        } else {
            existingCached.image = image
        }
    }
}