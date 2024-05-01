package com.dd3boh.outertune.utils


import android.util.Log
import com.arthenica.ffmpegkit.FFprobeKit
import com.dd3boh.outertune.ui.utils.ExtraMetadataWrapper
import org.json.JSONException
import org.json.JSONObject


const val TAG = "FFMetadataExtractor"

/**
 * Given a path to a file, return the artist string
 */
fun getExtraMetadata(path: String): ExtraMetadataWrapper {
    val mediaInformation = FFprobeKit.getMediaInformation(path)
//    Log.d(TAG, "Full output: " + mediaInformation.mediaInformation.allProperties.toString(4))
    var data: JSONObject

    try {
        data = mediaInformation.mediaInformation.allProperties.getJSONObject("format").getJSONObject("tags")
    } catch (e: JSONException) {
        // for containers that support multiple streams, it is in a different location
        val tmpData = mediaInformation.mediaInformation.allProperties.getJSONArray("streams")
        // get first stream, assume no one puts a video file in here...
        data = tmpData.getJSONObject(0).getJSONObject("tags")
    }

    val artists = data.getString("ARTIST")
    val genres = data.getString("GENRE")
    val date = data.getString("DATE")

//    Log.d(TAG, "Artists found by FFProbe: $artists")

    return ExtraMetadataWrapper(artists, genres, date)
}