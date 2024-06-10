# OuterTune

<img src="./assets/outertune.webp" height="72">

A Material 3 YouTube Music client for Android

> This is a fork of [InnerTune](https://github.com/z-huang/InnerTune)

[![Latest release](https://img.shields.io/github/v/release/DD3Boh/OuterTune?include_prereleases)](https://github.com/DD3Boh/OuterTune/releases)
[![License](https://img.shields.io/github/license/DD3Boh/OuterTune)](https://www.gnu.org/licenses/gpl-3.0)
[![Downloads](https://img.shields.io/github/downloads/DD3Boh/OuterTune/total)](https://github.com/DD3Boh/OuterTune/releases)

[<img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Get it on GitHub" height="80">](https://github.com/DD3Boh/OuterTune/releases/latest)

## Features

- Play, search, and save all your songs, videos, albums, and playlists from YouTube Music
- Background playback & AD free
- Advanced account sync
    - YouTube Music account login support
    - Fully fledged syncing of songs, playlists and albums — both to and from your account
- Library management
    - Cache and download songs for offline playback
    - Personalized quick picks
- Local media playback
    - Play local and Youtube Music songs at the same time
    - We don't use MediaStore's broken metadata extractor!
- Synchronized lyrics (LRC format, also includes multi-line support)
- Audio normalization, tempo/pitch adjustment, and various other audio effects
- Dynamic Material theme & localization
    - New integrated library screen design
- Android Auto support

## Screenshots

<img src="./assets/main-interface.webp" width="600" alt="Main player interface" />
<br/><br/>
<img src="./assets/player.webp" width="600" alt="Player interface"/>
<br/><br/>
<img src="./assets/ytm-sync.webp" width="600" alt="Sync with YouTube Music"/>

> **Warning**
>
>If you're in a region where YouTube Music is not supported, you won't be able to use this app
***unless*** you have a proxy or VPN to connect to a YTM supported region.

## FAQ

### Q: How to scrobble music to LastFM, LibreFM, ListenBrainz or GNU FM?

Use other music scrobbler apps. I
recommend [Pano Scrobbler](https://play.google.com/store/apps/details?id=com.arn.scrobble).

### Q: Why OuterTune isn't showing in Android Auto?

1. Go to Android Auto's settings and tap multiple times on the version in the bottom to enable
   developer settings
2. In the three dots menu at the top-right of the screen, click "Developer settings"
3. Enable "Unknown sources"

## Contributing Translations

Follow the [instructions](https://developer.android.com/guide/topics/resources/localization) and
create a pull request. If possible, please build the app beforehand and make sure there is no error
before you create a pull request.
./app/src/main/java/com/dd3boh/outertune/utils/scanners/jni/ffmpeg-android-maker
## Building with FFmpeg (non-kit)

By default, we ship a prebuilt library (`/app/prebuilt/ffMetadataEx.arr`), and you *do not* need to care about this.
However, should you choose to opt for self built libraries and/or work on the extractor itself, keep reading:

1. First you will need to setup the [Android NDK](https://developer.android.com/studio/projects/install-ndk)

2. We use FFMpeg to extract metadata from local files. The FFMpeg (non-kit) implementation must be resolved in one of two ways:
   
   - a) Build libraries. Clone [ffmpeg-android-maker](https://github.com/Javernaut/ffmpeg-android-maker) into `/ffMetadataEx/src/main/cpp/ffmpeg-android-maker`, run the build script. Note: It may be helpful to modify the FFmpeg build script disable uneeded FFmpeg fetaures to reduce app size, see [here](https://github.com/mikooomich/ffmpeg-android-maker/blob/master/scripts/ffmpeg/build.sh) for an example.
  
   - b) Use prebuilt FFmpeg libraries. Clone [prebuilt ffmpeg-android-maker](https://github.com/mikooomich/ffmpeg-android-maker-prebuilt) into `/ffMetadataEx/src/main/cpp/ffmpeg-android-maker`.

3. Modify `app/build.gradle.kts` and `settings.gradle.kts` to switch to the self built version, with the instructions being in both of the files

Then start the build are you normally would.

## Donate

If you like OuterTune, you're welcome to send a donation. Donations will support the development,
including bug fixes and new features.

<a href="https://paypal.me/DD3Boh"><img src="./assets/paypal.png" alt="PayPal" height="60" ></a>

## Credit

I want to give credit to [z-huang/InnerTune](https://github.com/z-huang/InnerTune) for providing
an awesome base for this fork, none of this would have been possible without it.
