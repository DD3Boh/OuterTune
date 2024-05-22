package wah.mikooomich.ffMetadataEx

/**
 * Pain and suffering.
 */
class FFprobeWrapper {
    external fun getAudioMetadata(filePath: String): String

    external fun getFullAudioMetadata(filePath: String): String


//    companion object {
//        // Used to load the 'ffmpegex' library on application startup.
//        init {
//            System.loadLibrary("ffmpegex")
//        }
//    }
}