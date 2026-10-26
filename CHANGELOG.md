# Changelog

Everything that changed, newest first.

## v1.17

- **The quick note stopped erasing the day's earlier notes.** The file of the day was opened in a mode that
  truncated it: every note replaced the ones before. Now an existing file is opened in append mode (and, on a
  folder provider that does not support it, read and written again with the new note at the end).
- **The sync no longer empties the vault.** It used to delete everything in the Obsidian folder before copying
  the repo over it — including the notes made on the tablet. Now it keeps a manifest of what it copied and
  deletes only the files that the repo itself no longer has. The notes and the attachments made in Obsidian stay.
  And an empty listing (a broken `find`, not an empty repo) now stops the sync instead of deleting the vault.
- **The sync no longer says "the Termux refused" when it worked.** The old log was deleted after the script
  started, so a fast run could lose its own log. The log is deleted before the run and carries a mark of that
  run; only the matching log is accepted.
- **The files of the sync are hidden from git.** The script, the log and the manifest live in the vault (the
  only place the app and Termux both reach); they are now added to the local exclude of the vault and of the
  working folder, so they do not show up as untracked notes.
- **The search opens the app it shows.** The grid drew the filtered list, but the tap used the index of the whole
  list: searching for "camera" and tapping the first result opened another app.
- **"Clear the data of the apps that are off" asks first**, and only touches the apps Free My Tablet itself
  turned off. Apps disabled by hand are left alone. (The data of an app cannot be brought back.)
- **The reading mode survives closing the app drawer.** The drawer's callback made the note and the widget
  visible again without checking the theme.
- **The widgets keep updating after the home screen is paused.** The app stopped listening to the system on
  pause and never started again: a clock widget would freeze after the first time the screen went away.
- **Back from a long settings screen returns to the top of the list**, instead of leaving it scrolled past the
  end.
- **The optional apps come back with "Original".** Turning one off after entering Free My Tablet did not record
  it in the switch state; turning them off also skipped the check that protects the app that saved the Wi-Fi
  network (disabling it would erase the saved networks).
- **The note of the day keeps its name when the language changes.** The prefix was translated, so the same day
  had a note in each language. The prefix is now kept in the settings; an existing note of the day with the old
  name is reused. "Open today's note" in the settings also creates the file before opening Obsidian.
- **A widget that has its own settings no longer reopens them forever**: the configuration screen has a request
  code of its own, so its answer is not mistaken for another pick.
- **The backup brings the whole home screen back**: the work areas, the size of the widget and the prefix of
  the note of the day were left out of the file. Now they are saved too, as well as the temperature unit, the
  recent apps and a list of optional apps that is empty ("none off").
- **Restoring the choices asks first**, and says that the notes are not touched.
- **The notes widget sees a note saved on the home screen.** The widget lives in the main process and the note
  is saved in the home one: the home now tells the main process, which forgets its copy and redraws.
- **The widget follows the language of the app.** Its title, "today"/"yesterday" and the footer came from the
  Android resources (always Portuguese, whatever the app's language); now they use the same text as the rest of
  the app, and the widget rereads the language when it is built (the main process could be older than the choice).
- **With no widget, the home screen uses the space.** The empty slot no longer leaves a band between the clock
  and the apps.
- **The tabs of the work areas no longer sit on the first row of apps.**
- **The search box no longer shows the typed text twice.** The box itself draws the text; the canvas only
  draws the hint while it is empty.
- **The apps opened lately show up on the first opening** of the complete list.
- **Changing the temperature unit shows the right number at once**: the value is kept in Celsius and converted
  when it is drawn, instead of keeping the number already converted.
- **The card no longer disappears at 0 °C.** The "nothing fetched yet" sentinel was the same 0, so a real zero
  hid the weather; the card now checks whether there was ever a fetch.
- **A second tap on "Sync" no longer overwrites the script of the running sync** (and the first waiter no longer
  reports that Termux refused). The button says the sync is already running until the log comes back.
- **The Spanish covers the whole app**: the 148 strings drawn by the custom UI that were still in English (the
  settings, the space and panel screens, the update messages, the sync errors, the restore dialog) have their
  Spanish now. The dynamic messages (how many apps were turned off, how many stayed, how many were cleaned) have
  the Spanish in the call itself.
- **The first opening shows the three steps** (set Free My Tablet as the home screen, choose the Obsidian folder,
  and where the app opens the settings), with a button that goes straight there. It is shown once; an update over
  a working install does not show it. The steps can be read again from *Settings → First steps*.
- The tests: 63 checks, including the sync script (it must not empty the vault, it stops on a broken `find`, it
  hides its own files from git), the guide of the first opening, the name of the note of the day, the list of apps
  whose data may be cleared, the Celsius/Fahrenheit conversion, the version comparison and a few Spanish strings.

## v1.16

- **The search in the vault looks inside the notes**, not only at the name: it reads the text of the notes
  (twenty thousand bytes of each) and finds the word there. The notes come as they are read.
- **The settings screen was burning the processor for nothing**: it redrew the screen sixty times a second
  even with nothing changing. Now it is drawn only when something changes (6% of a core → almost nothing).
- **The tests**: a runner without Gradle and without JUnit (javac and java, like the build) with 34 checks of
  the pure logic — the reading of the system's numbers, the addresses of the Obsidian, the dates, the search.
  The CI runs them before building.

## v1.15

- **A new screen: "O que o Free My Tablet ganhou"** — the tablet now, and the home screen side by side with
  the tablet's own: the size of the app, the permissions it asks for, the memory it takes and the processor
  it uses. Everything measured, nothing guessed.
- **The panel of the tablet** shows the storage, the network (the name, the address, the signal, the speed) and
  how long the tablet has been on.
- **The Space screen remembers how much the cleanups freed**, and the gain screen shows it.
- **The back button goes back to the list** of the settings (it used to close the app from any sub-screen).

## v1.14

- **The widget says when each note was changed** ("hoje", "ontem" or the date) and shows more notes when
  the slot is big enough: three in the medium one, four and the "open the vault" line in the big one.
- **The title of the widget opens the vault** in the Obsidian, and the line at the bottom does the same.
- **The note of the day is one tap away**: a button in the home screen opens it in the Obsidian (the file is
  made first, otherwise the Obsidian opens another note).

## v1.13

- **The widget no longer stays loading forever.** The app was not telling the system how big the slot is,
  so the widget received a size of zero by zero and never finished loading (the tablet's own launcher
  tells it, and the widget works there).
- The widgets left over from before are removed: the slot holds one.

## v1.12

- **Work areas**: groups of apps (study, work, reading…) that take the place of the row of the home screen.
  The area is chosen with one tap on the tab, above the apps; the areas are made in the settings.

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
