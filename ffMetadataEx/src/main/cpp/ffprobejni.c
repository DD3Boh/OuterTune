#include <jni.h>
#include <string.h>
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>

JNIEXPORT jstring JNICALL
Java_wah_mikooomich_ffMetadataEx_FFprobeWrapper_getAudioMetadata(JNIEnv* env, jobject obj, jstring filePath) {
    const char* file_path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!file_path) {
        return (*env)->NewStringUTF(env, "Error getting file path");
    }

    AVFormatContext* format_context = NULL;
    if (avformat_open_input(&format_context, file_path, NULL, NULL) != 0) {
        (*env)->ReleaseStringUTFChars(env, filePath, file_path);
        return (*env)->NewStringUTF(env, "Error opening file");
    }

    // Retrieve stream information
    if (avformat_find_stream_info(format_context, NULL) < 0) {
        avformat_close_input(&format_context);
        (*env)->ReleaseStringUTFChars(env, filePath, file_path);
        return (*env)->NewStringUTF(env, "Error finding stream information");
    }

    // get audio stream
    int audio_stream_index = -1;
    for (int i = 0; i < format_context->nb_streams; i++) {
        if (format_context->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            audio_stream_index = i;
            break;
        }
    }

    char string[10000] = "";

    // container tags (audio containers e.g. flac, mp3)
    AVDictionaryEntry* tag = NULL;
    while ((tag = av_dict_get(format_context->metadata, "", tag, AV_DICT_IGNORE_SUFFIX))) {
        strcat(string, tag->key);
        strcat(string, ": ");
        strcat(string, tag->value);
        strcat(string, "\n");
    }

    // audio stream tags (ex. ogg)
    if (audio_stream_index >= 0) {
        AVStream* audio_stream = format_context->streams[audio_stream_index];
        tag = NULL;
        while ((tag = av_dict_get(audio_stream->metadata, "", tag, AV_DICT_IGNORE_SUFFIX))) {
            strcat(string, tag->key);
            strcat(string, ": ");
            strcat(string, tag->value);
            strcat(string, "\n");
        }
    }

    avformat_close_input(&format_context);
    (*env)->ReleaseStringUTFChars(env, filePath, file_path);

    return (*env)->NewStringUTF(env, string);
}


JNIEXPORT jstring JNICALL
Java_wah_mikooomich_ffMetadataEx_FFprobeWrapper_getFullAudioMetadata(JNIEnv* env, jobject obj, jstring filePath) {
    const char* file_path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!file_path) {
        return (*env)->NewStringUTF(env, "Error getting file path");
    }

    AVFormatContext* format_context = NULL;
    if (avformat_open_input(&format_context, file_path, NULL, NULL) != 0) {
        (*env)->ReleaseStringUTFChars(env, filePath, file_path);
        return (*env)->NewStringUTF(env, "Error opening file");
    }

    // Retrieve stream information
    if (avformat_find_stream_info(format_context, NULL) < 0) {
        avformat_close_input(&format_context);
        (*env)->ReleaseStringUTFChars(env, filePath, file_path);
        return (*env)->NewStringUTF(env, "Error finding stream information");
    }

    // get audio stream
    int audio_stream_index = -1;
    for (int i = 0; i < format_context->nb_streams; i++) {
        if (format_context->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            audio_stream_index = i;
            break;
        }
    }

    char string[10000] = "";

    // container tags (audio containers e.g. flac, mp3)
    AVDictionaryEntry* tag = NULL;
    while ((tag = av_dict_get(format_context->metadata, "", tag, AV_DICT_IGNORE_SUFFIX))) {
        strcat(string, tag->key);
        strcat(string, ": ");
        strcat(string, tag->value);
        strcat(string, "\n");
    }

    // bitrate
    strcat(string, "\nbitrate: ");
    char bitrate[20];
    sprintf(bitrate, "%lld", format_context->bit_rate);
    strcat(string, bitrate);

    // audio stream tags (mixed containers e.g. ogg)
    if (audio_stream_index >= 0) {
        AVStream* audio_stream = format_context->streams[audio_stream_index];
        AVCodecParameters* codecpar = audio_stream->codecpar;

        // Add codec information
        const char* codec_type = av_get_media_type_string(codecpar->codec_type);
        if (codec_type != NULL) {
            strcat(string, "\ntype: ");
            strcat(string, codec_type);
        }

        const AVCodec* codec = avcodec_find_decoder(codecpar->codec_id);
        if (codec != NULL) {
            strcat(string, "\ncodec: ");
            strcat(string, codec->long_name);
        } else {
            strcat(string, "\ncodec: Unknown");
        }

        // other stream data
        strcat(string, "\nduration: ");
        char duration[20];
        sprintf(duration, "%lld", audio_stream->duration);
        strcat(string, duration);

        strcat(string, "\nsampleRate: ");
        char sample_rate[20];
        sprintf(sample_rate, "%d", codecpar->sample_rate);
        strcat(string, sample_rate);

        // Add number of channels
        strcat(string, "\nchannels: ");
        char channels[10];
        sprintf(channels, "%d", codecpar->ch_layout.nb_channels);
        strcat(string, channels);

        // these show up as 0
        /*
         * codecpar->bits_per_raw_sample
         * codecpar->bits_per_coded_sample
         * codecpar->frame_size
         * codecpar->bit_rate (use container bitrate instead
         */

        strcat(string, "\n");

        // add audio stream tags (ID3 metadata)
        tag = NULL;
        while ((tag = av_dict_get(audio_stream->metadata, "", tag, AV_DICT_IGNORE_SUFFIX))) {
            strcat(string, tag->key);
            strcat(string, ": ");
            strcat(string, tag->value);
            strcat(string, "\n");
        }
    }

    avformat_close_input(&format_context);
    (*env)->ReleaseStringUTFChars(env, filePath, file_path);

    return (*env)->NewStringUTF(env, string);
}
