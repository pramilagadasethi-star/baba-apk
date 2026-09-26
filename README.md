# Text History Keyboard

A custom Android keyboard that saves what you type, locally on the device.

## Features

- Dark-themed QWERTY keyboard with number row and symbols layout
- Saves typed text history locally (SharedPreferences)
- Never saves passwords or incognito input
- Main screen to view, copy, and clear history
- No internet permission, no external libraries

## Requirements

- JDK 17
- Android SDK with compileSdk 35
- Gradle 8.9

## Build

```bash
gradle assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

To generate the Gradle wrapper (optional):

```bash
gradle wrapper --gradle-version 8.9
```

## Install

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Setup

1. Open the **Text History Keyboard** app
2. Tap **Enable Keyboard** and enable "Text History Keyboard" in Settings
3. Tap **Switch Keyboard** and select "Text History Keyboard"
4. Start typing in any app — your text will be saved!

## Privacy

- Passwords and incognito fields are **never** saved
- All data is stored locally on the device
- No internet permission is requested
