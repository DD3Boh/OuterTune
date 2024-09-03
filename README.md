# OuterTune

<img src="./assets/outertune.webp" height="72">

A Material 3 YouTube Music client & local music player for Android

[![Latest release](https://img.shields.io/github/v/release/DD3Boh/OuterTune?include_prereleases)](https://github.com/DD3Boh/OuterTune/releases)
[![License](https://img.shields.io/github/license/DD3Boh/OuterTune)](https://www.gnu.org/licenses/gpl-3.0)
[![Downloads](https://img.shields.io/github/downloads/DD3Boh/OuterTune/total)](https://github.com/DD3Boh/OuterTune/releases)

[<img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Get it on GitHub" height="80">](https://github.com/DD3Boh/OuterTune/releases/latest)

## Features

OuterTune is a supercharged fork of [InnerTune](https://github.com/z-huang/InnerTune), with advanced account synchronization, local media playback, multiple queues, and a new take on UI design.

- Play, search, and save all your songs, videos, albums, and playlists from YouTube Music
    - Song downloading for offline playback
    - Background playback & AD free
- New integrated library screen design & Multiple queues
- Advanced account synchronization
    - YouTube Music account login support
    - Fully fledged syncing of songs, subscriptions, playlists and albums — both to and from your account
- Local audio file playback
    - Play local and Youtube Music songs at the same time
    - We don't use MediaStore's broken metadata extractor! (e.g tags delimited with `\\` now show up properly)
- Synchronized lyrics (LRC format, also includes multi-line support)
- Audio normalization, tempo/pitch adjustment, and various other audio effects
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

## Building & Contributing

We are looking for contrubutors, translators, and maintainers! If you would like to help out, or just wish to build the app yourself, please see the [building and contribution notes](./CONTRIBUTING.md).

## Donate

If you like OuterTune, you're welcome to send a donation. Donations will support the development,
including bug fixes and new features.

<a href="https://paypal.me/DD3Boh"><img src="./assets/paypal.png" alt="PayPal" height="60" ></a>

## Attribution

[z-huang/InnerTune](https://github.com/z-huang/InnerTune) for providing
an awesome base for this fork, none of this would have been possible without it.

[Musicolet](https://play.google.com/store/apps/details?id=in.krosbits.musicolet) for inspiration of a local music player experience done right.