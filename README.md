# Audio Recorder App

A simple Android application that provides a floating button overlay to record audio. The app is built using Jetpack Compose and follows modern Android development practices.

## Features

- Floating, draggable button that overlays other apps
- One-tap audio recording start/stop
- Automatic file naming with timestamps
- Permission handling for audio recording and overlay
- Material Design 3 theming
- Foreground service for reliable recording

## Technical Details

- Built with Jetpack Compose
- Targets Android API 34 (Android 14)
- Minimum SDK version: 24 (Android 7.0)
- Uses MediaRecorder for audio capture
- Implements proper permission handling
- Saves recordings in MP3 format

## Required Permissions

- `RECORD_AUDIO`: For capturing audio
- `WRITE_EXTERNAL_STORAGE`: For saving recordings (Android < 33)
- `SYSTEM_ALERT_WINDOW`: For the floating button overlay
- `FOREGROUND_SERVICE`: For the recording service
- `POST_NOTIFICATIONS`: For the foreground service notification (Android 13+)

## Setup

1. Clone the repository
2. Open in Android Studio
3. Build and run on your device
4. Grant the required permissions when prompted

## Usage

1. Launch the app
2. Grant all required permissions
3. The floating button will appear on your screen
4. Tap the button to start recording (turns red when recording)
5. Tap again to stop recording
6. Recordings are saved in the app's music directory

## File Storage

Recordings are saved in the app's external files directory under the Music folder. The files are named using the format:
`yyyy-MM-dd-HH-mm-ss.mp3`

## Development

The app is structured following modern Android architecture principles:

- Jetpack Compose for UI
- Service-based background processing
- Permission handling with Accompanist
- Material Design 3 theming 