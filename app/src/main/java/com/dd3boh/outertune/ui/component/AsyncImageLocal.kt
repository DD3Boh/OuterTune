package com.dd3boh.outertune.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Non-blocking image
 */
@Composable
fun AsyncLocalImage(
    image: () -> Bitmap?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    var imageBitmapState by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(image) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = image.invoke()
                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        imageBitmapState = bitmap.asImageBitmap()
                    }
                }
            } catch (e: Exception) {
//                e.printStackTrace()
                // this probably won't be an issue when debugging...
            }
        }
    }

    imageBitmapState.let { imageBitmap ->
        if (imageBitmap == null) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(16.dp))
                    .aspectRatio(ratio = 1f)
            )
        } else {
            Image(
                bitmap = imageBitmap,
                contentDescription = contentDescription,
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}