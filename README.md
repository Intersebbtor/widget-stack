<p align="center">
  <img src="docs/widget.svg" width="400" alt="Widget Stack showing three media players stacked in one widget">
</p>

<h1 align="center">Widget Stack</h1>

<p align="center">
  <b>All your media players in one home screen widget.</b><br>
  Spotify, Audible, Pocket Casts &amp; co., stacked, always in reach, no clutter.
</p>

<p align="center">
  <a href="https://github.com/Intersebbtor/widget-stack/releases/latest"><b>Download APK</b></a> ·
  Android 12+ · No internet permission · MIT
</p>

---

## Why

You listen to music in one app, audiobooks in another and podcasts in a third. Each app ships its own widget, and each one eats a chunk of your home screen. The system media controls only show whatever played last.

**Widget Stack** puts one compact row per app into a single widget. Every row has cover art, title, and back / play-pause / forward controls. Rows stay there even when the app isn't running, so you can pick up the audiobook right where you left off.

### Typical use case

You run a minimal launcher (Niagara, Lawnchair, Nova, Smart Launcher, ...) and want *one* tidy media block instead of three app widgets. Place Widget Stack once, choose which app goes into which row, done.

Some launchers only fit **one widget per popup or slot** (Niagara's widget popups, for example). Widget Stack turns that one slot into a player dashboard: one widget, several players living inside it, all visible at a glance.

## Features

<img src="docs/config.png" width="300" align="right" alt="Widget Stack setup screen">

- **1 to 4 rows per widget**, each bound to an app you choose. Several widgets with different setups are possible.
- **Live state:** cover art, title, artist/show, and a highlighted row for what's currently playing
- **Works with any app that has proper media controls:** Spotify, YouTube Music, Audible, Pocket Casts, AntennaPod, Tidal, Deezer, Amazon Music, Apple Music, ...
- **Smart buttons:** podcast and audiobook apps that don't support "next track" get their own skip back/forward (e.g. 15 s) instead
- **Resume from cold:** the row remembers the last played item. ▶ tries to resume playback even when the app isn't running
- **Tap the cover or title** to jump into the app
- **Material You:** follows your wallpaper colors, light and dark
- **Resizable:** rows share the height you give the widget
- **Private by design:** no internet permission, no analytics, no accounts. Nothing leaves your phone.
- English and German UI

<br clear="right">

## How it works

Android does not allow a widget to contain other apps' widgets. So Widget Stack draws its own player rows and reads the media sessions Android already exposes. That's the same data your lock screen and quick settings use.

To read media sessions, Android requires **notification access**. Widget Stack only uses it to see media sessions. It does not read, store or send notification content.

## Install

> **Why adb?** Android 13+ blocks notification access for apps that are installed by tapping an APK file. The switch stays greyed out. Installing once via adb avoids that. It takes about 5 minutes the first time, and a Play Store release is planned.

### 1. Prepare your phone (one time)

1. **Enable developer options:** Settings → About phone → tap **Build number** 7 times. On some phones it's under Software information or Version.
2. **Enable USB debugging:** Settings → System → Developer options → **USB debugging** on.

### 2. Prepare your computer (one time)

Download Google's **[Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools)** for Windows, macOS or Linux and unzip it. That folder contains `adb`.

Alternatively, install it with a package manager:

```bash
brew install android-platform-tools        # macOS
```

```bash
winget install Google.PlatformTools         # Windows
```

```bash
sudo apt install adb                        # Debian / Ubuntu
```

### 3. Install Widget Stack

1. Download `WidgetStack-x.y.z-release.apk` from [Releases](https://github.com/Intersebbtor/widget-stack/releases/latest).
2. Connect your phone via USB and tap **Allow** on the "Allow USB debugging?" prompt on the phone.
3. Open a terminal in the folder with the APK and run:

```bash
adb devices
```

Your phone should be listed as `device`. If it says `unauthorized`, check the prompt on your phone.

```bash
adb install -r WidgetStack-1.0.0-release.apk
```

`Success` means you're done. Already installed it by tapping the APK? Just run the same command, `-r` reinstalls over it and lifts the block.

> If you used the unzipped Platform-Tools folder instead of a package manager, run the commands from inside that folder (`./adb` on macOS/Linux, `.\adb` on Windows) and pass the full path to the APK.

### 4. Set it up

1. Open **Widget Stack** and tap **Grant** next to *Notification access*. The switch is no longer greyed out.
2. Optional, recommended: tap **Allow** next to *Battery*.
3. Long-press your home screen → **Widgets** → **Widget Stack**, place it and pick your apps.

You can turn USB debugging off again afterwards.

### No cable? Wireless debugging (Android 11+)

Developer options → **Wireless debugging** on → **Pair device with pairing code**. Then on your computer, same Wi-Fi:

```bash
adb pair 192.168.x.x:PAIRING_PORT
```

Enter the 6-digit code shown on the phone, then connect using the IP and port from the main *Wireless debugging* screen (a different port):

```bash
adb connect 192.168.x.x:PORT
```

Continue with `adb install -r ...` as above.

### Without a computer?

Some phones show **⋮ → Allow restricted settings** in Settings → Apps → Widget Stack after you tried to enable the switch once. If your phone has it, that works too. On many devices it doesn't, so adb is the reliable way.

### Battery optimisation

Some vendors (OnePlus, Oppo, Xiaomi, Samsung, ...) kill background services aggressively, which can freeze the widget. The app shows a one-tap button to exempt it from battery optimisation. See [dontkillmyapp.com](https://dontkillmyapp.com) for vendor-specific tips.

## Known limitations

- An app that hasn't been opened since reboot has no media session yet. Widget Stack then shows the last known item, and ▶ tries to resume via the app's media browser service. A few apps refuse that. Tapping the row opens the app instead.
- Some apps provide cover art only as a URL, not as an image. Those rows fall back to the app icon.
- Not every launcher offers "reconfigure widget". You can always change a widget from the Widget Stack app itself.

## Build from source

Requires JDK 17+ and the Android SDK (compileSdk 36).

```bash
./gradlew assembleRelease
```

Without a `keystore.properties` the build is signed with your debug key. For your own signed builds create `keystore.properties` in the project root:

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

No third-party dependencies. Only the Android framework and Kotlin.

## License

[MIT](LICENSE)
