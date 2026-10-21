<div align="center">

<img src="app/res/drawable-nodpi/icon.png" width="96" alt="">

# Free My Tablet

**A fast, ad-free home screen for your Android tablet.**

The clock, your apps and nothing else. No ads, no banners, no suggestions — and the apps you never use
turned off (never uninstalled). One button brings everything back.

Open source. Built with Claude Code.

</div>

## Why

A tablet from 2019 is slow, and the manufacturer's home screen is half of the reason: it wants you to install
more apps, it shows you what it wants you to see, and it takes a while to open. Free My Tablet replaces it with
one screen that draws fast and shows only what you chose.

This is the sibling of [Free My TV](https://freemytv.io): the same ideas and the same code, for a tablet.

## What you get

- **Your apps, and only yours.** A grid you swipe through and a dock at the bottom that you fill yourself.
- **Nothing extra.** No ads, no sponsored rows, no promo widgets, no analytics, no account.
- **Light by day, dark by night.** The screen follows the real sunrise and sunset where you are.
- **The bloat turned off.** The apps the manufacturer preinstalled (Microsoft Office, Facebook, the Galaxy Store,
  Bixby…) can be switched off in one tap. They are never uninstalled: *Original* brings them all back.
- **Safe.** No root. Nothing is uninstalled. One button brings back the factory home screen.
- **Light on an old tablet.** Drawn by hand on one Canvas, no libraries, a 1.1 MB APK.

## Install

About ten minutes. You need an Android tablet (Android 9 or newer) and a computer on the same Wi-Fi.

### The easy way: Claude Code

1. **On the tablet**, turn on developer mode: Settings → About tablet → tap *Build number* 7 times. Then Settings →
   *Developer options* → turn on **USB debugging**.
2. **On your computer**:
   ```bash
   git clone https://github.com/T3lo5/free-my-tablet && cd free-my-tablet
   ```
3. **Open [Claude Code](https://claude.com/claude-code)** in that folder and say:
   > Install Free My Tablet on my tablet at 192.168.1.50. I live in Londrina.
4. **On the tablet**, open *Free My Tablet* → *Home screen* → *Free My Tablet*, and accept the debugging prompt
   (tick *Always allow*).

### The manual way

1. **Tools:** a JDK (17+), the Android SDK command-line tools (build-tools 34, platform android-30) and adb. On a Mac:
   ```bash
   brew install --cask temurin android-commandlinetools android-platform-tools
   sdkmanager "build-tools;34.0.0" "platforms;android-30"
   ```
   On Linux, get OpenJDK 17, Android's command-line tools and adb from your package manager. On Windows, use WSL.
   No JDK? Docker works: `docker run --rm -v "$PWD:/work" -v "$HOME/Android/Sdk:/opt/android-sdk" -w /work
   -e ANDROID_HOME=/opt/android-sdk freemytablet-build bash ./build.sh`.
2. **Build:**
   ```bash
   ./build.sh
   ```
   The first run creates `config.properties`: put your city's coordinates there (optional).
3. **Install:**
   ```bash
   adb connect 192.168.1.50:5555
   adb install -r FreeMyTablet.apk
   ```
4. **On the tablet**, open *Free My Tablet* → *Home screen* → *Free My Tablet*.

## Settings

| Where | What |
|---|---|
| *Home screen* | Free My Tablet ⇄ Original (the factory home screen) |
| *Apps to turn off* | The preinstalled apps you never use (Bixby, Samsung Internet, the Play apps…) |
| *Dock* | The apps at the bottom of the screen |
| *Language* | Automatic, Português, English, Español |
| *Theme* | Automatic (follows the sun), Light, Dark |

## Going back

On the tablet: *Free My Tablet* → *Home screen* → *Original*. The factory home screen and every app come back
exactly as they were. If the new home screen ever crashes three times in five minutes, it switches back by itself.

To remove it for good, switch to *Original* first, then uninstall *Free My Tablet* (Settings → Apps).

## How it works

- **A plain Android app, no Gradle, no libraries.** [`build.sh`](build.sh) runs aapt2 → javac → d8 → zipalign →
  apksigner and makes a 1.1 MB APK.
- **It uses the tablet's own ADB, from inside the tablet.** The app connects to `127.0.0.1:5555` with its own key
  (that's the *Allow USB debugging?* prompt) and runs the same commands you would: `cmd package set-home-activity`,
  `pm disable-user`. Nothing listens to the network.
- **Drawn by hand on one Canvas**, with no GPU shaders (the tablet stops at Android 11): the sky of the day, the
  icons and the glass, all in one view.
- **A safety net.** A watchdog in a separate process brings back the stock home screen after three crashes in five
  minutes, and a boot check keeps your choice after an update.
- **Data** from Open-Meteo only: the sunrise and the sunset, where you are. No keys, no account, no tracking.

| File | What it is |
|---|---|
| [`HomeView.java`](app/src/io/freemytablet/HomeView.java) | The home screen |
| [`HomeMode.java`](app/src/io/freemytablet/HomeMode.java) | Switching between Free My Tablet and Original |
| [`Adb.java`](app/src/io/freemytablet/Adb.java) | The tablet's own ADB, from inside the tablet |
| [`HomeApps.java`](app/src/io/freemytablet/HomeApps.java) | The apps, their icons and the dock |
| [`SettingsActivity.java`](app/src/io/freemytablet/SettingsActivity.java) | The settings screen |
| [`Sky.java`](app/src/io/freemytablet/Sky.java) | The colors of the day |
| [`Sun.java`](app/src/io/freemytablet/Sun.java) | The sunrise and the sunset where you are |
| [`Prefs.java`](app/src/io/freemytablet/Prefs.java) | Settings changed on the tablet |
| [`Watchdog.java`](app/src/io/freemytablet/Watchdog.java) | The safety net |

## Privacy

No accounts, no analytics, no tracking. The app talks to Open-Meteo (with the coordinates you set) and to
nothing else. Your settings stay on your tablet.

## Credits

- Built with [Claude Code](https://claude.com/claude-code).
- Weather data by [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0).
- Icons: [Material Symbols](https://fonts.google.com/icons) (Apache 2.0). Font: [Inter](https://rsms.me/inter/)
  (SIL OFL 1.1).
- Not affiliated with Samsung, Google or any app. App names and logos belong to their owners.

## License

[MIT](LICENSE). Fork it, change it, ship it.
