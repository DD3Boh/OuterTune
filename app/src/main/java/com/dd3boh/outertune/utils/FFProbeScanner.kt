package com.dd3boh.outertune.utils


import android.util.Log
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformationSession
import com.arthenica.ffmpegkit.MediaInformationSessionCompleteCallback
import com.dd3boh.outertune.ui.utils.ExtraMetadataWrapper
import org.json.JSONException
import org.json.JSONObject


const val TAG = "FFMetadataExtractor"


class fuckYouFFMpegKit : MediaInformationSessionCompleteCallback {
    override fun apply(p0: MediaInformationSession?) {
        if (p0 != null) {
            p0.mediaInformation.allProperties
        }
    }

}

/**
 * Given a path to a file, return the artist string
 */
fun getExtraMetadata(path: String): ExtraMetadataWrapper {
    val mediaInformation = FFprobeKit.getMediaInformation(path)

    try {
//    Log.d(TAG, "Full output: " + mediaInformation.mediaInformation.allProperties.toString(4))
        var data: JSONObject

        try {
            data = mediaInformation.mediaInformation.allProperties.getJSONObject("format").getJSONObject("tags")
        } catch (e: JSONException) {
            Log.d(TAG, "Try reeading fom stream")
            // for containers that support multiple streams, it is in a different location
            val tmpData = mediaInformation.mediaInformation.allProperties.getJSONArray("streams")
            // get first stream, assume no one puts a video file in here...
            data = tmpData.getJSONObject(0).getJSONObject("tags")
        }

        var artists = try {
            data.getString("ARTIST")
        } catch (e: Exception) {
            try {
                data.getString("artist")
            } catch (e: Exception) {
                ""
            }
        }

        val genres = try {
            data.getString("GENRE")
        } catch (e: Exception) {
            try {
                data.getString("genre")
            } catch (e: Exception) {
                ""
            }
        }

        val date = try {
            data.getString("DATE")
        } catch (e: Exception) {
            try {
                data.getString("date")
            } catch (e: Exception) {
                ""
            }
        }

//    Log.d(TAG, "Artists found by FFProbe: $artists")

        return ExtraMetadataWrapper(artists, genres, date)
    } catch (e: Exception) {
        Log.d(TAG, "CRITICAL ERROR: " + mediaInformation.mediaInformation.allProperties.toString(4))
        throw Exception("weh")
    }
}