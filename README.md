# AURA

A modern, user-friendly video and audio downloader for Android.

## Features

- **Download Videos & Audio**: Download videos and audio files from 1000+ websites supported by [yt-dlp](https://github.com/yt-dlp/yt-dlp)
- **Simple Interface**: Clean, intuitive Material Design 3 UI with dark theme support
- **Format Selection**: Automatically selects the best quality based on your preferences, or choose manually
- **Download Queue**: Manage all your downloads in one place with progress tracking
- **Playlist Support**: Download entire playlists with a single click
- **Subtitle Support**: Download and embed subtitles into your videos
- **Customizable Settings**: Configure download directory, format preferences, and more
- **Background Downloads**: Continue downloading even when the app is in the background
- **No Ads**: Completely free and open source with no advertisements

## Download

Get the latest release from [GitHub Releases](https://github.com/kefamgaya/AuraDownloader/releases/latest).

## Requirements

- Android 8.0 (API level 26) or higher
- Internet connection for downloading videos

## Usage

1. Open the app and paste a video URL in the input field
2. Click "Search" to fetch video information
3. Select your preferred format and quality
4. Click "Download" to start the download
5. Monitor progress in the Downloads section

## Settings

AURA offers a simplified settings experience:

- **General**: Configure notifications, thumbnails, and download behavior
- **Format**: Set default video/audio format and quality preferences
- **Network**: Manage cellular data usage and cookies
- **Appearance**: Customize theme and language

## Building from Source

### Prerequisites

- Android Studio Hedgehog or later
- JDK 21 or later
- Android SDK with API level 26+

### Build Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/kefamgaya/AuraDownloader.git
   cd AuraDownloader/Seal
   ```

2. Open the project in Android Studio

3. Sync Gradle files

4. Build the APK:
   ```bash
   ./gradlew assembleDebug
   ```

   Or build a release APK:
   ```bash
   ./gradlew assembleRelease
   ```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

For bug reports and feature requests, please open an issue on [GitHub Issues](https://github.com/kefamgaya/AuraDownloader/issues).

## Credits

AURA is built using:

- [yt-dlp](https://github.com/yt-dlp/yt-dlp) - Video downloader engine
- [youtubedl-android](https://github.com/yausername/youtubedl-android) - Android wrapper for yt-dlp
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern Android UI toolkit
- [Material Design 3](https://m3.material.io/) - Design system

AURA is a fork of [Seal](https://github.com/JunkFood02/Seal), redesigned with a focus on simplicity and user experience.

## License

This project is licensed under the GPLv3 License - see the [LICENSE](LICENSE) file for details.

## Disclaimer

AURA is for personal use only. Please respect copyright laws and terms of service of the websites you download from. The developers are not responsible for any misuse of this software.

---

Made with ❤️ for the open source community
