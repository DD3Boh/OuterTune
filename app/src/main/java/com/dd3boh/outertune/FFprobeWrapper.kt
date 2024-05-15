package com.dd3boh.outertune

/**
 * Pain and suffering.
 */
class FFprobeWrapper {
    external fun getAudioMetadata(filePath: String): String

    external fun getFullAudioMetadata(filePath: String): String
}