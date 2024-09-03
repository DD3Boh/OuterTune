# Building
For most users, we recommend importing and building through Android Studio.

## Build variants
There are the following build variants
```
universal (all architectures)
arm64 (arm64-v8a)
uncommonabi (armeabi-v7a, x86, x86_64)
x86_64
```
**For most users, the `universal` variant is sufficient.** The other build varients may reduce file size, however at the cost of compatibility.


## Building with FFmpeg binaries

By default, we ship a prebuilt library (`/app/prebuilt/ffMetadataEx.arr`), and you *DO NOT* need to care about this.
However, should you choose to opt for self built libraries and/or work on the extractor itself, keep reading:

1. First you will need to setup the [Android NDK](https://developer.android.com/studio/projects/install-ndk)

2. We use FFMpeg to extract metadata from local files. The FFMpeg binaries must be resolved in one of two ways:
   
   - a) Build libraries yourself. Clone [ffmpeg-android-maker](https://github.com/Javernaut/ffmpeg-android-maker) into `/ffMetadataEx/src/main/cpp/ffmpeg-android-maker`, run the build script. Note: It may be helpful to modify the FFmpeg build script disable uneeded FFmpeg fetaures to reduce app size, see [here](https://github.com/mikooomich/ffmpeg-android-maker/blob/master/scripts/ffmpeg/build.sh) for an example.
  
   - b) Use prebuilt FFmpeg libraries. Clone [prebuilt ffmpeg-android-maker](https://github.com/mikooomich/ffmpeg-android-maker-prebuilt) into `/ffMetadataEx/src/main/cpp/ffmpeg-android-maker`.

3. Modify `app/build.gradle.kts` and `settings.gradle.kts` to switch to the self built version, with the instructions being in both of the files.

4. start the build are you normally would.


<br/><br/>

# Contributing to OuterTune

## Submitting a pull request
- One pull request for one feature/issue, do not tackle unrelated features/issues in one pull request
- Write a descriptive title and a meaningful description
- Upload images/video for any UI changes
- In the event of merge conflicts, you may be required to rebase onto the current `dev` branch
- **You are required to build and test the app before submitting a pull request**

## Translations

Follow the [instructions](https://developer.android.com/guide/topics/resources/localization) and
create a pull request. **You are also required to build the app beforehand** and make sure there is no error
before you create a pull request.