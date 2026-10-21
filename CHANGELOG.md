# Changelog

Everything that changed, newest first.

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
