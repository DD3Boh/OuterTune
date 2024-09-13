# CMakeLists.txt
cmake_minimum_required(VERSION 3.10.2)
project(ffMetadataEx)


add_library(avformat SHARED IMPORTED)
set_target_properties( # Specifies the target library.
        avformat

        # Specifies the parameter you want to define.
        PROPERTIES IMPORTED_LOCATION

        # Provides the path to the library you want to import.
        ${CMAKE_SOURCE_DIR}/ffmpeg-android-maker/output/lib/${ANDROID_ABI}/libavformat.so )


add_library(avutil SHARED IMPORTED)
set_target_properties( # Specifies the target library.
        avutil

        # Specifies the parameter you want to define.
        PROPERTIES IMPORTED_LOCATION

        # Provides the path to the library you want to import.
        ${CMAKE_SOURCE_DIR}/ffmpeg-android-maker/output/lib/${ANDROID_ABI}/libavutil.so )

add_library(avcodec SHARED IMPORTED)
set_target_properties( # Specifies the target library.
        avcodec

        # Specifies the parameter you want to define.
        PROPERTIES IMPORTED_LOCATION

        # Provides the path to the library you want to import.
        ${CMAKE_SOURCE_DIR}/ffmpeg-android-maker/output/lib/${ANDROID_ABI}/libavcodec.so )


# Include FFmpeg headers
include_directories(${CMAKE_SOURCE_DIR}/ffmpeg-android-maker/output/include/${ANDROID_ABI})

add_library(ffmetaexjni SHARED ffMetaExJni.cpp)

# Link FFmpeg libraries
target_link_libraries(ffmetaexjni
        avformat
        avutil
        avcodec
)

# Set the output directory for the .so file
set_target_properties(ffmetaexjni PROPERTIES LIBRARY_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI})