# Changelog

Everything that changed, newest first.

## v1.11

- **The widget has a size** (small, medium, big), in the settings. The slot follows the size the widget asks
  for, so a widget is never cut.
- **The space screen shows every app** (the biggest first), not only eight.
- **The reading mode**: a black screen with the clock small in the corner and the apps, nothing else (the
  theme "Leitura"). Good for reading at night.
- **"Open today's note"**: opens the Obsidian with the file of the day's quick note, in the folder you chose.
- **The Obsidian address was wrong**: the slashes of the path were being escaped, so a note inside a folder
  never opened (the widget and the vault search had the same problem).
- The theme "read" was being ignored (the value of the theme only knew "light" and "dark").

## v1.10

- **The notes of the vault are read right.** The app was asking the system for the id of the tree in a
  document, and the paths came out wrong (and the notes of the subfolders never came at all).
- **The quick note goes to the folder that was chosen** (the "Pasta da nota"): the folder is made when it does
  not exist yet, and the note lands inside it.
- **The search in the vault opens right away** and the notes show up as they are found (the walk through the
  vault is slow: the system asks about every folder). The list is kept for a while, so the second time is
  instant.
- **The "light" theme is warm paper, and the text is dark on it** (the clock, the names, the weather, the note
  field). The "automatic" theme still follows the sun.
- **The notice of the home screen is shown once.** It used to come back every time the screen was opened.
- The name of the file of the choices is "free-my-tablet.json" (the hidden one, with a dot at the front, the
  system did not show it in the vault).

## v1.9

- **The list of all the apps has a search.** The field on top of the list filters as you type.
- **The apps opened lately** have their own row on top of that list.
- **The quick note can have its own folder** in the vault (the folder is made if it does not exist).
- **The notes of the vault can be searched** (by name), and a tap opens the note in Obsidian.
- **The choices can be saved and read back**, so they can be copied to another tablet: the file goes into the vault.
- **The "light" theme is now warm paper**, for reading (the "automatic" one still follows the sun).
- The paths of the notes were wrong when the vault had folders inside folders: the app asked for the id of
  the tree in a document, and got a strange answer.

## v1.8

- **The panel of the tablet.** In the settings, the new *Tablet* line shows what the tablet is doing right
  now: the memory (total, in use, free, swap), the battery (level and temperature), how many processes are
  alive, who is using the processor and who is using the memory. The numbers are the ones the system itself
  reports — the same ones the developer options show.

## v1.7

- **Choosing a widget keeps the slot.** The system gives the id of the widget in the data of the answer
  ("appwidget://13"), not in an extra, so the new widget was never put in the slot (and the old one stayed
  there too).
- The update screen says what to do when the Google Play Protect holds the installation.

## v1.6

- The path of the downloaded APK is written at once: the installer reads the file right away, and the other
  process has to see it right away too.

## v1.5

- **The installer finds the APK.** The provider of the app runs in the main process and the screen in the ":home"
  one: the path of the downloaded file was in a variable of one process and the other could not see it. It is in
  the settings now.

## v1.4

- **The update opens the installer directly.** It went through the system's "open with" chooser, and the chooser
  can lose the permission to read the file: the installer then said the package was broken.

## v1.3

- **The update works end to end.** The installer could not read the downloaded APK (the provider was too strict
  about the mode it was asked for): the update now installs without a problem.
- The notes of the release lose their markdown marks, so they read as text.

## v1.2

- **The list of all the apps can be used with a widget on the screen.** The widget and the note field step aside
  while the list is open (they were on top of it, and the apps under them could not be tapped).
- The build takes the version from the tag, so the app knows when there is a new one (before, the version was
  fixed in the manifest and the app could never see an update).

## v1.1

- **The app updates itself.** It looks for a new release on its own and says so on the home screen; in the
  settings, *Updates* downloads it and opens the system's installer. No APK to fetch by hand, no repository to
  watch.
- **The space report.** What is using the tablet (the apps, the cache) and two buttons to get some of it
  back: clear the cache of every app, and clear the data of the apps that are turned off. On the tablet this
  was measured on, the cache alone was 2.6 GB and the storage was at 14% free.
- **Who talks to you.** The apps that sent the most notifications, with a tap that opens the system screen to
  turn the notification off.
- **Samsung's diagnostics** (1.7 GB, and it reports the use of the tablet) joined the list of what is turned
  off; the text-to-speech engine, Maps, Drive and Samsung Weather joined the optional ones.
- A short guide to install it ([`docs/instalar.md`](docs/instalar.md)).

## v1.0

The first release: a home screen for the tablet, with the things the person actually uses.

- **The home screen**: the clock, the weather (the temperature, the symbol and the city) and the apps you picked.
  Nothing else — no grid of every app, no suggestions, no ads. The rest of the apps are behind one small button.
- **The quick note**: write on the home screen and the note goes straight into your Obsidian vault, as a
  Markdown file, one per day (the folder is yours to choose, through the system's own picker).
- **The sync button**: the `git pull` and the copy over the folder Obsidian reads, in one tap — no Termux, no
  copying folders by hand. (Termux does the work: that is where git is.)
- **The widget**: the last notes of your vault, and a tap opens them in Obsidian. Every other widget of the
  tablet works too — the picker is the system's own.
- **The switch**: Free My Tablet ⇄ the manufacturer's home screen, with the system's own dialog (no root, no
  keys). The preinstalled apps you never use can be turned off in one tap and come back with one button.
  Nothing is ever uninstalled.
- **Light on an old tablet**: drawn by hand on one Canvas, no libraries, a 1.9 MB APK, **0.0% of the CPU**
  when the screen is at rest and 7 MB of memory (the stock launcher: 46 MB).
- In Portuguese, English and Spanish.
