#include <jni.h>
#include <string>

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavutil/avutil.h>
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_dd3boh_ffMetadataEx_FFMpegWrapper_getFullAudioMetadata(JNIEnv* env, jobject obj, jstring filePath) {
    const char* file_path = env->GetStringUTFChars(filePath, nullptr);
    if (!file_path) {
        return env->NewStringUTF("Error getting file path");
    }

    AVFormatContext* format_context = nullptr;
    if (avformat_open_input(&format_context, file_path, nullptr, nullptr) != 0) {
        env->ReleaseStringUTFChars(filePath, file_path);
        return env->NewStringUTF("Error opening file");
    }

    // Retrieve stream information
    if (avformat_find_stream_info(format_context, nullptr) < 0) {
        avformat_close_input(&format_context);
        env->ReleaseStringUTFChars(filePath, file_path);
        return env->NewStringUTF("Error finding stream information");
    }

    // get audio stream
    int audio_stream_index = -1;
    for (int i = 0; i < format_context->nb_streams; i++) {
        if (format_context->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            audio_stream_index = i;
            break;
        }
    }

    std::string result;

    // container tags (audio containers e.g. flac, mp3)
    AVDictionaryEntry* tag = nullptr;
    while ((tag = av_dict_get(format_context->metadata, "", tag, AV_DICT_IGNORE_SUFFIX))) {
        result += tag->key;
        result += ": ";
        result += tag->value;
        result += "\n";
    }

    // bitrate
    result += "\nbitrate: " + std::to_string(format_context->bit_rate);

    // audio stream tags (for mixed containers e.g. ogg)
    if (audio_stream_index >= 0) {
        AVStream* audio_stream = format_context->streams[audio_stream_index];
        AVCodecParameters* codecpar = audio_stream->codecpar;

        // add codec information
        const char* codec_type = av_get_media_type_string(codecpar->codec_type);
        if (codec_type != nullptr) {
            result += "\ntype: ";
            result += codec_type;
        }

        const AVCodec* codec = avcodec_find_decoder(codecpar->codec_id);
        if (codec != nullptr) {
            result += "\ncodec: ";
            result += codec->long_name;
        } else {
            result += "\ncodec: Unknown";
        }

        // misc stream data
        result += "\nduration: " + std::to_string(format_context->duration);
        result += "\nsampleRate: " + std::to_string(codecpar->sample_rate);
        result += "\nchannels: " + std::to_string(codecpar->ch_layout.nb_channels);

        // these show up as 0
        /*
         * codecpar->bits_per_raw_sample
         * codecpar->bits_per_coded_sample
         * codecpar->frame_size
         * codecpar->bit_rate (use container bitrate instead
         */
        result += "\n";

        // add audio stream tags (ID3 result)
        tag = nullptr;
        while ((tag = av_dict_get(audio_stream->metadata, "", tag, AV_DICT_IGNORE_SUFFIX))) {
            result += tag->key;
            result += ": ";
            result += tag->value;
            result += "\n";
        }
    }

    avformat_close_input(&format_context);
    env->ReleaseStringUTFChars(filePath, file_path);

    return env->NewStringUTF(result.c_str());
}
