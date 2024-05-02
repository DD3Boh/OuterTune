package com.dd3boh.outertune.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Non-blocking image
 */
@Composable
fun AsyncLocalImage(
    image: () -> Deferred<Bitmap?>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    var imageBitmapState by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(Unit) {
        // Call the suspend function to get the Deferred<Bitmap>
        val deferred = image.invoke()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = deferred.await()
                if (bitmap != null) {
                    imageBitmapState = bitmap.asImageBitmap()
                }
            } catch (e: Exception) {
//                e.printStackTrace()
                // this probably won't be an issue when debugging...
            }
        }
    }

    imageBitmapState?.let { imageBitmap ->
        Image(
            bitmap = imageBitmap,
            contentDescription = contentDescription,
            modifier = modifier.fillMaxSize(),
        )
    }
}