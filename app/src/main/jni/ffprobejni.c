#include <jni.h>
#include <string.h>
#include <libavformat/avformat.h>

JNIEXPORT jstring JNICALL
Java_com_dd3boh_outertune_FFprobeWrapper_getAudioMetadata(JNIEnv* env, jobject obj, jstring filePath) {
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

    // container tags (ex flac, mp3)
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
