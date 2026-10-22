package io.freemytablet;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The space on the tablet: what is using it and how to get it back.
 *
 * A tablet with 2 GB of RAM and a full storage is the usual reason for it to be slow (the system starts
 * paging). The apps the person does not use are the first thing to go: their data and their cache.
 */
final class Space {
    private Space() {}

    /** One app and what it is using. */
    static final class App {
        final String pkg;
        final long data;     // bytes
        final long cache;    // bytes

        App(String pkg, long data, long cache) {
            this.pkg = pkg;
            this.data = data;
            this.cache = cache;
        }

        long total() { return data + cache; }
    }

    /** The apps that use the most space, biggest first. */
    static List<App> biggest(Adb adb, int limit) throws IOException {
        String out = adb.shell("dumpsys diskstats");
        List<App> apps = new ArrayList<>();
        String[] names = array(out, "Package Names: ");
        String[] data = array(out, "App Data Sizes: ");
        String[] cache = array(out, "Cache Sizes: ");
        int count = Math.min(names.length, Math.min(data.length, cache.length));
        for (int i = 0; i < count; i++) {
            long d = number(data[i]), c = number(cache[i]);
            if (d + c < 20L * 1024 * 1024) continue;   // less than 20 MB does not matter
            apps.add(new App(names[i].replace("\"", ""), d, c));
        }
        Collections.sort(apps, new Comparator<App>() {
            @Override public int compare(App a, App b) {
                return Long.compare(b.total(), a.total());
            }
        });
        if (limit > 0 && apps.size() > limit) return apps.subList(0, limit);
        return apps;
    }

    /** The free space and the total, in bytes. */
    static long[] free(Adb adb) throws IOException {
        String out = adb.shell("df -k /data | tail -1");
        String[] parts = out.trim().split("\\s+");
        if (parts.length < 4) return new long[] {0, 0};
        try {
            return new long[] {Long.parseLong(parts[3]) * 1024, Long.parseLong(parts[1]) * 1024};
        } catch (NumberFormatException e) {
            return new long[] {0, 0};
        }
    }

    /**
     * Asks the system to empty the cache of the apps (the same thing that "clear the cache" does in the
     * settings, for every app at once). It is the biggest space win and it costs nothing.
     */
    static boolean trimCaches(Adb adb) throws IOException {
        String out = adb.shell("pm trim-caches 2G");
        AppLog.w("the cache of every app was asked to go: " + (out == null ? "(no answer)" : out.trim()));
        return out != null;
    }

    /** Empties the data of the apps that are turned off (they are not in use: that is the point). */
    static int clearTurnedOff(Adb adb, List<String> packages) throws IOException {
        int done = 0;
        for (String pkg : packages) {
            if (pkg == null || pkg.isEmpty()) continue;
            String out = adb.shell("pm clear " + pkg);
            if (out.contains("Success")) done++;
        }
        return done;
    }

    /** The apps that are turned off right now. */
    static List<String> turnedOff(Adb adb) throws IOException {
        List<String> out = new ArrayList<>();
        for (String line : adb.shell("pm list packages -d").split("\n")) {
            line = line.trim();
            if (line.startsWith("package:")) out.add(line.substring(8));
        }
        return out;
    }

    /** A number written in the diskstats (a string of digits). */
    private static long number(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The array that starts with that text. */
    /** The list of a dumpsys ("Package Names: [a, b, c]"). */
    static String[] array(String text, String start) {
        int at = text.indexOf(start);
        if (at < 0) return new String[0];
        int open = text.indexOf('[', at);
        int close = text.indexOf(']', open);
        if (open < 0 || close < 0) return new String[0];
        String body = text.substring(open + 1, close);
        String[] parts = body.split(",");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim().replace("\"", "");
        return parts;
    }
}
