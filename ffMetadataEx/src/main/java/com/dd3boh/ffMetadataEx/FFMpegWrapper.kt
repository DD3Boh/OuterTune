package com.dd3boh.ffMetadataEx

/**
 * Pain and suffering.
 */
class FFMpegWrapper {
    external fun getAudioMetadata(filePath: String): String

    external fun getFullAudioMetadata(filePath: String): String
}