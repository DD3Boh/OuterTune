package com.dd3boh.outertune.utils.scanners


import android.util.Log
import com.arthenica.ffmpegkit.FFprobeKit
import com.dd3boh.outertune.utils.ExtraMetadataWrapper
import com.dd3boh.outertune.utils.MetadataScanner
import org.json.JSONException
import org.json.JSONObject

class FFProbeKitScanner : MetadataScanner {


    private val TAG = "FFProbeKitScanner"

    /**
     * Given a path to a file, extract necessary metadata MediaStore fails to
     * deliver upon. Extracts artists, genres, and date
     *
     * @param path Full file path
     */
    override fun getMediaStoreSupplement(path: String): ExtraMetadataWrapper {
        val mediaInformation = FFprobeKit.getMediaInformation(path)

        try {
//            Log.d(TAG, "Full output: " + mediaInformation.mediaInformation.allProperties.toString(4))
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

//            Log.d(TAG, "Artists found by FFProbe: $artists")

            return ExtraMetadataWrapper(artists, genres, date)
        } catch (e: Exception) {
            Log.d(TAG, "CRITICAL ERROR: " + mediaInformation.mediaInformation.allProperties.toString(4))
            throw Exception("weh")
        }
    }

}