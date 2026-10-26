package io.freemytablet;

import android.content.Context;

/**
 * The sync: the button on the home screen that does the git pull and the copy, without going to Termux.
 *
 * The work happens in Termux (that is where git is). The app writes a small script in the Obsidian folder (the
 * only place both can reach), Termux runs it, and the script leaves a log that the app reads.
 * The pull and the copy are what the person does by hand today: git pull in the working folder, then the copy
 * over the folder Obsidian reads, replacing the files.
 */
final class Sync {
    private Sync() {}

    static final String SCRIPT = ".freemy-sync.sh";
    static final String LOG = ".freemy-sync.log";
    /**
     * The files the last sync copied from the repo. The next sync deletes only the ones that are gone from the
     * repo: the notes made in the Obsidian and the attachments are not ours to delete.
     */
    static final String MANIFEST = ".freemy-sync-files";

    interface Result {
        void done(boolean ok, String text);
    }

    /** The script: the pull in the working folder, then the copy over the vault (the folder the script is in). */
    static String script(String work, String pull, String run) {
        String w = work.startsWith("~") ? "$HOME" + work.substring(1) : work;
        return "#!/data/data/com.termux/files/usr/bin/bash\n"
                + "set -o pipefail\n"
                + "DIR=\"$(dirname \"$0\")\"\n"
                + "LOG=\"$DIR/" + LOG + "\"\n"
                + "MANIFEST=\"$DIR/" + MANIFEST + "\"\n"
                + "NOW=\"$MANIFEST.new\"\n"
                + "{\n"
                // The mark of this run: an old log (or one left by a provider that did not delete it) is ignored.
                + "  echo \"FREEMY-SYNC-RUN: " + run + "\"\n"
                + "  echo \"--- Free My Tablet: sincronizando $(date) ---\"\n"
                + "  cd \"" + w + "\" || { echo \"FREEMY-SYNC-ERROR: a pasta " + work + " nao existe\"; exit 1; }\n"
                + "  (" + pull + ") || { echo \"FREEMY-SYNC-ERROR: o pull falhou\"; exit 1; }\n"
                // The files of the repo (the .git, the .obsidian and our own files stay out).
                + "  find . -type f ! -path './.git/*' ! -path './.obsidian/*' ! -name '.freemy-sync*' > \"$NOW\"\n"
                // An empty listing is a broken find, not an empty repo: deleting now would empty the vault.
                + "  [ -s \"$NOW\" ] || { echo \"FREEMY-SYNC-ERROR: nao consegui listar os arquivos do repositorio\"; exit 1; }\n"
                // Delete only what a previous sync copied and the repo no longer has. Never the vault itself.
                + "  if [ -f \"$MANIFEST\" ]; then\n"
                + "    while IFS= read -r f; do\n"
                + "      [ -n \"$f\" ] && ! grep -qxF \"$f\" \"$NOW\" && rm -f \"$DIR/$f\"\n"
                + "    done < \"$MANIFEST\"\n"
                + "  fi\n"
                + "  if [ -s \"$NOW\" ]; then\n"
                + "    tar cf - --no-recursion -T \"$NOW\" | (cd \"$DIR\" && tar xf -)\n"
                + "    [ $? -eq 0 ] || { echo \"FREEMY-SYNC-ERROR: a copia falhou\"; exit 1; }\n"
                + "  fi\n"
                + "  mv -f \"$NOW\" \"$MANIFEST\"\n"
                // The script, the log and the manifest are ours: they must not show up as untracked in either git.
                + "  for ex in \"$PWD/.git/info/exclude\" \"$DIR/.git/info/exclude\"; do\n"
                + "    if [ -f \"$ex\" ] && ! grep -qxF '.freemy-sync*' \"$ex\"; then echo '.freemy-sync*' >> \"$ex\"; fi\n"
                + "  done\n"
                + "  echo \"FREEMY-SYNC-OK\"\n"
                + "} > \"$LOG\" 2>&1\n";
    }

    /**
     * Starts the sync: the script is written in the vault and Termux runs it. The result comes back through
     * {@link #waitForResult}. Returns the mark of this run (null when it could not start).
     */
    static String start(Context c) {
        String dest = Vault.path(c);
        if (dest.isEmpty()) return null;
        String work = Prefs.syncFolder();
        String pull = Prefs.syncCommand();
        String run = java.util.UUID.randomUUID().toString();
        if (!Vault.write(c, SCRIPT, script(work, pull, run))) return null;
        // The old log is deleted before the run: the script writes the new one as soon as Termux starts it.
        Vault.delete(c, LOG);
        if (!Termux.run(c, dest + "/" + SCRIPT)) return null;
        return run;
    }

    /** Waits for the log of this run (in the background) and tells what happened. */
    static void waitForResult(Context c, String run, Result result) {
        final Context app = c.getApplicationContext();
        final String mark = "FREEMY-SYNC-RUN: " + run;
        new Thread(() -> {
            long start = System.currentTimeMillis();
            long until = start + 180_000;
            boolean seen = false;
            while (System.currentTimeMillis() < until) {
                String log = Vault.read(app, LOG);
                // Only this run's log counts: an old one (or one the provider refused to delete) is not an answer.
                boolean ours = log != null && log.contains(mark);
                if (ours) {
                    seen = true;
                    if (log.contains("FREEMY-SYNC-OK")) {
                        result.done(true, last(log));
                        return;
                    }
                    if (log.contains("FREEMY-SYNC-ERROR")) {
                        result.done(false, last(log));
                        return;
                    }
                } else if (!seen && System.currentTimeMillis() - start > 20_000) {
                    // The script never ran: Termux is refusing the commands of other apps.
                    result.done(false, Ui.tr("O Termux ainda não aceita comandos de outros apps. No Termux, rode: "
                                    + "echo 'allow-external-apps = true' >> ~/.termux/termux.properties",
                            "Termux does not accept commands from other apps yet. In Termux, run: "
                                    + "echo 'allow-external-apps = true' >> ~/.termux/termux.properties"));
                    return;
                }
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    return;
                }
            }
            result.done(false, Ui.tr("A sincronização não respondeu.", "The sync did not answer."));
        }, "sync").start();
    }

    /** The last line that says something (the error, or the last line of the log). */
    private static String last(String log) {
        String[] lines = log.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("FREEMY-SYNC-ERROR:")) return line.substring("FREEMY-SYNC-ERROR:".length()).trim();
            if (line.startsWith("FREEMY-SYNC-OK")) return "";
            return line;
        }
        return "";
    }
}
