package io.freemytablet;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * The bridge to Termux, which is where git lives on this tablet.
 *
 * Termux has a service that runs a command for another app (RunCommandService). The app writes a small script in
 * the Obsidian folder (the only place both can reach) and asks Termux to run it: git pull, then the copy over the
 * vault. The person gives the "run commands" permission once, in the system's own dialog.
 */
final class Termux {
    private Termux() {}

    /** The permission Termux asks for the apps that run commands on it. */
    static final String PERMISSION = "com.termux.permission.RUN_COMMAND";

    static final String PACKAGE = "com.termux";
    static final String SERVICE = "com.termux.app.RunCommandService";

    /** Whether the person gave the permission (and Termux is installed). */
    static boolean allowed(Context c) {
        if (!installed(c)) return false;
        return c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Whether Termux is installed on the tablet at all. */
    static boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo(PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Asks Termux to run a script (a path in the Obsidian folder).
     *
     * It has to be a foreground service: Android 11 does not let an app start a service of another app that is
     * in the background (the error is "Background start not allowed"), and Termux turns the command into a
     * foreground service with its own notification.
     */
    static boolean run(Context c, String scriptPath) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(PACKAGE, SERVICE));
        intent.setAction("com.termux.RUN_COMMAND");
        intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
        // The arguments go as an array (one argument each: the script path), and the script goes straight to
        // bash (the file written through the folder picker has no execute bit, so it cannot be run as a program).
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[] {scriptPath});
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        try {
            c.startForegroundService(intent);
            return true;
        } catch (RuntimeException e) {
            AppLog.w("could not ask Termux to run the sync: " + e.getMessage());
            // Android 8 and older: a plain service is the way.
            try {
                intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
                c.startService(intent);
                return true;
            } catch (RuntimeException e2) {
                AppLog.w("could not ask Termux to run the sync: " + e2.getMessage());
                return false;
            }
        }
    }
}
