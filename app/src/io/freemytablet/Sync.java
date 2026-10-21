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

    interface Result {
        void done(boolean ok, String text);
    }

    /** The script: the pull in the working folder, then the copy over the vault. */
    static String script(String work, String dest, String pull) {
        String w = work.startsWith("~") ? "$HOME" + work.substring(1) : work;
        return "#!/data/data/com.termux/files/usr/bin/bash\n"
                + "DIR=\"$(dirname \"$0\")\"\n"
                + "LOG=\"$DIR/" + LOG + "\"\n"
                + "{\n"
                + "  echo \"--- Free My Tablet: sincronizando $(date) ---\"\n"
                + "  cd \"" + w + "\" || { echo \"FREEMY-SYNC-ERROR: a pasta " + work + " nao existe\"; exit 1; }\n"
                + "  (" + pull + ") || { echo \"FREEMY-SYNC-ERROR: o pull falhou\"; exit 1; }\n"
                // The script and the log stay: the log is being written while this runs.
                + "  find \"" + dest + "\" -mindepth 1 -maxdepth 1 ! -name '.git' ! -name '.obsidian'"
                + " ! -name '.freemy-sync*' -exec rm -rf {} +\n"
                + "  tar cf - --exclude=.git --exclude=.obsidian . | (cd \"" + dest + "\" && tar xf -)\n"
                + "  echo \"FREEMY-SYNC-OK\"\n"
                + "} > \"$LOG\" 2>&1\n";
    }

    /**
     * Starts the sync: the script is written in the vault and Termux runs it. The result comes back through
     * {@link #waitForResult}.
     */
    static boolean start(Context c) {
        String dest = Vault.path(c);
        if (dest.isEmpty()) return false;
        String work = Prefs.syncFolder();
        String pull = Prefs.syncCommand();
        if (!Vault.write(c, SCRIPT, script(work, dest, pull))) return false;
        String path = dest + "/" + SCRIPT;
        if (!Termux.run(c, path)) return false;
        Vault.delete(c, LOG);   // the old log, so we know the new one when it comes
        return true;
    }

    /** Waits for the log (in the background) and tells what happened. */
    static void waitForResult(Context c, Result result) {
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            long start = System.currentTimeMillis();
            long until = start + 180_000;
            boolean seen = false;
            while (System.currentTimeMillis() < until) {
                String log = Vault.read(app, LOG);
                if (log != null) {
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
