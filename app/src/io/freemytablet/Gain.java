package io.freemytablet;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.io.IOException;

/**
 * What Free My Tablet is worth on this tablet: the numbers now, and the comparison with the tablet's own
 * home screen (the size of the app, the memory it takes, the permissions it asks for).
 *
 * The numbers of the tablet come from the tablet's own ADB (the same connection the switch uses), in a
 * thread: none of this belongs on the screen. The comparison is measured, not guessed: the size of the two
 * APKs, the memory each one takes and the permissions each one asks for.
 */
final class Gain {
    private Gain() {}

    /** The home screen of the tablet (the one we replace). */
    static final String SYSTEM_HOME = "com.sec.android.app.launcher";

    /** Everything the screen shows. */
    static final class Data {
        // Ours.
        long apkSize, ramPss;
        int permissions, processes;
        double homeCpu;
        // The tablet's own home screen.
        long sysApkSize, sysRamPss;
        int sysPermissions;
        // The tablet.
        long freeSpace, totalSpace;
        long ramTotal, ramUsed, ramFree, swapUsed;
        int appsOff;          // the factory apps that are turned off
        int appsGone;         // the factory apps that are not installed for the user anymore
        long freedSpace;      // what the cleanups of the Space screen freed
    }

    /** Reads the numbers (the ADB is the tablet's own). */
    static Data read(Adb adb, Context c) throws IOException {
        Data d = new Data();
        // The comparison: the size of the app and the permissions it asks for (the system's API).
        d.apkSize = sizeOf(c, c.getPackageName());
        d.permissions = permissionsOf(c, c.getPackageName());
        d.sysApkSize = sizeOf(c, SYSTEM_HOME);
        d.sysPermissions = permissionsOf(c, SYSTEM_HOME);
        try {
            // The storage.
            String df = adb.shell("df /data | tail -1");
            String[] parts = df.trim().split("\\s+");
            if (parts.length >= 4) {
                d.totalSpace = Long.parseLong(parts[1]) * 1024;
                d.freeSpace = Long.parseLong(parts[3]) * 1024;
            }
            // The memory.
            String mem = adb.shell("dumpsys meminfo");
            d.ramTotal = Panel.number(mem, "Total RAM: ") * 1024;
            d.ramUsed = Panel.number(mem, "Used RAM: ") * 1024;
            d.ramFree = Panel.number(mem, "Free RAM: ") * 1024;
            String info = adb.shell("cat /proc/meminfo");
            long swapTotal = Panel.number(info, "SwapTotal:");
            long swapFree = Panel.number(info, "SwapFree:");
            d.swapUsed = Math.max(0, (swapTotal - swapFree) * 1024);
            // The processes.
            String ps = adb.shell("ps -A | wc -l").trim();
            try {
                d.processes = Integer.parseInt(ps);
            } catch (NumberFormatException ignored) {
            }
            // The memory of the two home screens.
            d.ramPss = pss(adb, c.getPackageName());
            d.sysRamPss = pss(adb, SYSTEM_HOME);
        } catch (Exception e) {
            AppLog.w("could not read the numbers of the tablet: " + e.getMessage());
        }
        try {
            java.util.Set<String> all = packages(adb, "");
            java.util.Set<String> more = packages(adb, "-u");
            d.appsGone = more.size() - all.size();     // the ones that are not installed for the user
            d.appsOff = packages(adb, "-d").size();
        } catch (Exception e) {
            d.appsOff = HomeMode.disabledCount(c);
        }
        d.freedSpace = Prefs.freedSpace();
        d.homeCpu = ownCpu();
        return d;
    }

    /** The packages the system lists (with that flag: "-d" = the ones that are off, "-u" = the uninstalled). */
    private static java.util.Set<String> packages(Adb adb, String flag) throws IOException {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String line : adb.shell("pm list packages " + flag).split("\n")) {
            line = line.trim();
            if (line.startsWith("package:")) out.add(line.substring(8));
        }
        return out;
    }

    /** The size of the app (in bytes). */
    static long sizeOf(Context c, String pkg) {
        try {
            PackageInfo info = c.getPackageManager().getPackageInfo(pkg, 0);
            File file = new File(info.applicationInfo.sourceDir);
            return file.length();
        } catch (Exception e) {
            return 0;
        }
    }

    /** How many permissions the app asks for. */
    static int permissionsOf(Context c, String pkg) {
        try {
            PackageInfo info = c.getPackageManager().getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
            if (info.requestedPermissions == null) return 0;
            return info.requestedPermissions.length;
        } catch (Exception e) {
            return 0;
        }
    }

    /** The memory the app takes (the system's own measure, in bytes). */
    private static long pss(Adb adb, String pkg) {
        try {
            String out = adb.shell("dumpsys meminfo " + pkg);
            return Panel.value(out, "TOTAL PSS:") * 1024;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * The processor the app itself is using: the difference of the process's own counters in a second.
     * It is the number that matters for a home screen: what it costs to be on the screen, doing nothing.
     */
    private static double ownCpu() {
        long a = ticks();
        if (a < 0) return 0;
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }
        long b = ticks();
        if (b < 0) return 0;
        return (b - a) / 100.0;   // 100 ticks = one second of one core
    }

    /** The processor's time of this process (in ticks). */
    private static long ticks() {
        try {
            java.io.File file = new java.io.File("/proc/self/stat");
            byte[] data = new byte[(int) file.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(file);
            int read = in.read(data);
            in.close();
            if (read <= 0) return -1;
            String text = new String(data, 0, read);
            // The fields after the name (which may have spaces): utime and stime are the 12th and 13th.
            int end = text.lastIndexOf(')');
            if (end < 0) return -1;
            String[] parts = text.substring(end + 1).trim().split("\\s+");
            if (parts.length < 13) return -1;
            return Long.parseLong(parts[11]) + Long.parseLong(parts[12]);
        } catch (Exception e) {
            return -1;
        }
    }
}
