# Free My Tablet: notes for Claude

A home screen for Android tablets that replaces the stock one, plus a settings app that switches between
Free My Tablet and the tablet's original home screen. A plain Android app in Java, built without Gradle by
`build.sh`. It is the sibling of **Free My TV** (`../free-my-tv`): the same ideas, the same code style, the same
safety net — but for a tablet, with the touch instead of the remote.

The first tablet is a **Galaxy Tab A 8.0 (2019), SM-T290** (Brazil): Android 11, 8" 1280×800 at 213 dpi, 2 GB of
RAM, Exynos 7904. Everything is built for that: an old, slow tablet that has to feel light.
The website is not in this repo yet.

The person you're helping may not be technical. Keep it short and friendly, say what you're about to do on their
tablet before you do it, and let them do the taps on the tablet.

## What is on the tablet (read with adb, don't guess)

- Android 11 (API 30), security patch 2023-05-01. No AGSL, no GPU shaders: the sky is drawn with plain fills.
- Screen 800×1280, density 213 (so 601×961 dp). The stock home: `com.sec.android.app.launcher`.
- The apps: Obsidian, ReadEra, Termux, YouTube, two web apps installed as WebAPKs (Astron Members, CODERS),
  Chrome, Drive, Gmail, Bitwarden, YouCine, Samsung Notes, My Files.
- No Bixby, no Samsung Internet, no Microsoft Office, no Facebook: this tablet is already clean.

## Installing it on a tablet

1. **Ask** for the tablet's IP address (if it is over USB, skip this) and their city, if they didn't say.
2. **Tools:** a JDK 17+, the Android SDK command-line tools (build-tools 34+, platform android-30) and adb. Check
   what's already there (`java -version`, `$ANDROID_HOME`, `adb version`) and install only what's missing.
   - macOS: `brew install --cask temurin android-commandlinetools android-platform-tools`, then
     `sdkmanager "build-tools;34.0.0" "platforms;android-30"`.
   - Linux: OpenJDK 17, Android's command-line tools and adb from the package manager, then the same `sdkmanager` line.
   - Windows: WSL, then as on Linux.
   - **No JDK on the machine?** Docker works: `docker run --rm -v "$PWD:/work" -v "$HOME/Android/Sdk:/opt/android-sdk"
     -w /work -e ANDROID_HOME=/opt/android-sdk freemytablet-build bash ./build.sh` (the image has a JDK 17 and `zip`).
3. **Settings:** run `./build.sh` once; it creates `config.properties`. Fill in `latitude` and `longitude` (decimal
   degrees) and `city` for their city. Nothing else is needed: there are no keys.
4. **Build:** `./build.sh` → `FreeMyTablet.apk`. Keep `freemytablet.keystore`: updates must be signed with the same key.
5. **Install:** `adb install -r FreeMyTablet.apk` (over USB) or `adb connect <IP>:5555` and then the same.
6. **Open the ADB port of the tablet** (from the computer, with the tablet plugged in):
   ```bash
   adb tcpip 5555
   ```
   This is what lets the app turn the preinstalled apps off **from inside the tablet**. The port closes when the
   tablet reboots: run it again then. It is not needed to change the home screen (see below).
7. **They switch on the tablet:** the settings open from the app's icon (in *All apps*, the button at the bottom
   right) or by **holding the home screen** (the empty part). Then *Home screen* → *Free My Tablet*. The system asks
   which home
   app to use (Android's own dialog, the HOME role): pick Free My Tablet. Then the tablet asks to allow debugging
   (the app's own ADB): accept it and tick **"Sempre permitir"** — otherwise the next switch asks again.
   Don't switch modes from the computer.

   **If the debugging prompt does not appear**, the tablet's `adbd` is holding an old prompt that never got an
   answer: run `adb tcpip 5555` again from the computer (it restarts `adbd` and clears it).

   **If the switch stops and asks them to forget the Wi-Fi**, it is the app protecting the network, not a bug. When
   Android turns off an app, it also deletes the Wi-Fi networks that app saved. The switch turns off the setup wizard,
   and that is often the app that saved the network during the setup — so the switch would take the tablet offline for
   good, and *Original* re-enables apps but cannot bring a deleted network back. The app therefore stops before
   changing anything.

   The fix is to make the network belong to someone else. On the tablet: *Settings* → *Connections* → *Wi-Fi* → the
   network → *Forget*, then connect to it again with the password. Now the Settings app owns it, and the Settings app
   is never turned off (it is on the protected list), so the network survives the switch.
8. **Check:** `adb logcat -s FreeMyTablet` shows what the switch did.

## Cutting a release

1. Write what changed in `CHANGELOG.md`, under the version's title (`## v1.1`).
2. Commit and push.
3. Tag and push the tag: **the tag is the release** (the workflow builds, signs and publishes it).
   ```bash
   git tag -a v1.1 -m "Free My Tablet v1.1" && git push origin v1.1
   ```
   The APK lands in the release with the notes of that version and the link to the commits.

The signing key is a secret of the repository (`KEYSTORE_BASE64`): the workflow decodes it and signs with it.
The keystore is the same one the tablet has installed, so the release is an update and not a new app. **Losing
that keystore means the people who installed the app cannot update it** — back it up (it is in the project's own
folder, `freemytablet.keystore`, and it is not in the repository).

The build on the runner uses the Android SDK that GitHub's image already has (the workflow only asks for
`platforms;android-30` and `build-tools;34.0.0`, and goes on if they are not available). The
`android-actions/setup-android` action does not work any more (it looks for the "tools" package, which the SDK
repository does not have).

## Adapting it to another tablet

Read first, change after. Read-only commands that tell you what the tablet has:

```bash
adb shell getprop ro.product.brand; adb shell getprop ro.product.model; adb shell getprop ro.build.version.release
adb shell wm size; adb shell wm density          # the screen and how many dp it has
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell cmd role get-role-holders android.app.role.HOME
adb shell pm list packages -s     # the system apps (the ones the manufacturer preinstalled)
```

Then, in the code:

- `HomeMode.DEBLOAT`: the manufacturer's promo, demo and telemetry apps, one comment per line. Only those.
- `HomeMode.OPTIONAL`: the apps that are bloat for some and useful for others (the person turns them off in the
  settings).
- `HomeMode.PROTECTED`: the system pieces: settings, keyboard, Play Store, the SOS app, the system UI. When in doubt,
  protect it.
- `HomeApps.DEFAULT_FAVORITES`: the apps on the home screen before the person picks their own.
- Build, install, have them switch on the tablet, read `adb logcat -s FreeMyTablet`, then have them switch back to
  *Original* once and confirm everything returned.

## The code

- Java 8 language level, minSdk 28 (Android 9), targetSdk 30 (Android 11, the tablet's last version). No Gradle, no
  libraries: only the Android SDK.
- The home screen is drawn by hand on one Canvas (`HomeView`); the settings screen too (`SettingsActivity`).
  Keep it at 60 fps: no allocations in `onDraw`, no work on the UI thread. The icons are made once, in the
  background (`HomeApps.icon`), and drawn from bitmaps.
- **The tablet is not a TV.** The home screen is not a grid of every app: it is the clock, the row of apps the
  person chose (`Prefs.favorites`), and the quick note. The rest of the apps are behind the small button in the
  corner. The tap opens the app, the long press opens its page in the settings, the arrows and Enter work too (for
  a tablet with a keyboard).
- **The quick note** is a real `EditText` (`HomeActivity`), put exactly on the panel the home screen draws, and it
  is saved in the Obsidian vault (`Vault`): the system's own folder picker (SAF), one Markdown file per day,
  appended. Without AndroidX: `DocumentsContract` and the `ContentResolver` directly. Three traps: the tree URI is
  the folder and the *document* URI is the folder as a document (`createDocument` wants the second one); a new
  file is opened with `"rwt"`, but an existing one **must** be opened with `"wa"` (`"rwt"` truncates and eats the
  day's earlier notes; when the provider has no `"wa"`, read the file and write it again); and the name's prefix
  is kept in `Prefs.notePrefix`, because translating it would split the day's notes after a language change.
- **The sync button** (bottom left) does the git pull and the copy without going to Termux: the app writes a small
  script in the vault (the only place both can reach), asks Termux to run it, and reads the log it leaves. Termux
  needs two things, once: the `com.termux.permission.RUN_COMMAND` permission (the app asks the system) and
  `allow-external-apps=true` in `~/.config/termux/termux.properties` (the person types it in Termux). The
  arguments of the Termux service go as a **String array** (one argument each) and the service is started with
  `startForegroundService` (Android 11 blocks a plain service start from the background). **The vault is never
  emptied**: the script keeps a manifest (`.freemy-sync-files`) of what it copied and deletes only the files that
  the repo no longer has — the quick notes and the attachments made in Obsidian are not the repo's to delete. An
  empty listing stops the run (a broken `find` would empty the vault), and the script, the log and the manifest
  go into the local `.git/info/exclude` of both folders, so git does not see them. The
  log carries the run's UUID, and the app only accepts the log with the mark of the run it started.
- **The widget** (the slot under the clock) is picked in the system's own widget picker, so every widget of the
  tablet works. Ours is "Últimas notas": the last notes of the vault, a tap opens them in Obsidian
  (`obsidian://open?vault=…&file=…`). Two traps: a widget is drawn by the system, so the layout can only have the
  plain attributes (the colors and the sizes go through `setTextColor`/`setTextViewTextSize`), and the
  `AppWidgetHostView` has to be **measured** before it is laid out (without that, its own views stay with no size
  and the widget shows an empty panel). The texts of our widget go through `Ui.tr`, never through `getString`
  (the app has one `values/` folder, in Portuguese; and the widget's process is older than the language choice,
  so the widget calls `Ui.applyLanguage()` when it is built). Our notes widget lives in the main process while
  the note is saved in `:home`: `Vault.notifyChanged` sends `io.freemytablet.NOTES_CHANGED`, and `HomeReceiver`
  drops the cache and redraws (without that the widget keeps showing yesterday's list).
- **The theme** (automatic, light, dark) really changes the background: `Sky.light`/`Sky.dark` instead of the sky
  of the hour.
- **The weather card** (top right): the temperature, the symbol and the city, from Open-Meteo (`Weather`), with a
  switch in the settings. The temperature is kept in Celsius and converted when it is drawn, so changing the unit
  shows the right number without waiting for the next fetch.
- **The home screen switch does not need the ADB.** From Android 10 on, the home app is a role the system gives
  (`RoleManager.createRequestRoleIntent`): the system's own dialog, no root, no keys. **The manifest has to declare
  the HOME intent in `<queries>`** — without it the system cannot see the other home apps and the role is never
  offered (this is why it did not work at first on the Samsung tablet). The app's own ADB (127.0.0.1:5555) is only
  used to turn the preinstalled apps off, and only if the port is open (`adb tcpip 5555` from the computer).
  Without it the home screen still works, the apps just stay on.
- **Never do network on the UI thread.** Android kills the app (`NetworkOnMainThreadException`): the ADB check and
  the note writing are in threads. The same goes for the disk work.
- The tablet's own `adbd` can hold a pending debug prompt and then skip the new ones ("prompt currently pending").
  `adb tcpip 5555` again (from the computer) restarts it and clears that.
- There is no GPU shader here: the SM-T290 stops at Android 11, and AGSL needs Android 13. The background is the
  sky of the day (`Sky`, the same palettes as the TV) with the real sunrise and sunset (`Sun`, Open-Meteo).
- `HomeMode` runs every switch as a background job (`ModeJob`) in the main process; the UI is in the `:home` process.
  `Watchdog` goes back to the stock home screen after 3 crashes in 5 minutes.
- On-screen text: `Ui.tr("pt", "en")`, with the Spanish in `Spanish.java` (keyed by the English); text built at
  runtime uses `Ui.tr(pt, en, es)`. Add all three. Every literal key has its Spanish case; a message with a
  number in the middle passes the Spanish as the third argument (the switch cannot see the number). Comments in English, short, only where the why isn't obvious.
- Settings come from `config.properties` through the generated `out/gen/io/freemytablet/Config.java` (never edit
  that). What the person changes on the tablet (`Prefs`: the apps on the home screen, the vault, the apps to turn
  off, the language, the theme) wins over those first values.
- After any change, `./build.sh` must end with `Built FreeMyTablet.apk`.

## What is not here yet

- The icon is still the TV's (`app/res/drawable-nodpi/icon.png`): the tablet needs its own.
- No widgets, no recents screen, no folders, no search, no app drawer with pages.
- The screensaver is the tablet's own (the TV's `DreamView` was not ported): a dock on the table does not need one.
- The website (`docs/`) is empty: the TV's is in `../free-my-tv/docs`.
- The apps' own "web apps" (Moodle, Canvas) are installed by the browser as real apps: they show up in the list
  like any other, nothing special to do.

## Always

- Never turn off an app that saved a Wi-Fi network: Android deletes its networks and Original can't bring them
  back (`wifiOwners` in `HomeMode`).
- Never commit `config.properties`, `*.keystore` or any key.
- Never uninstall or clear the data of system apps; only `pm disable-user --user 0`, through `HomeMode`, so
  *Original* can bring it back.
- Don't change the tablet's picture, sound, network or account settings.
- Keep the way back working: the *Original* mode and the watchdog.
