package io.freemytablet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * What the tablet is doing right now: the memory, the processor, the battery and the processes.
 *
 * It is read with the tablet's own ADB (the same connection the switch uses), in a thread: none of this
 * belongs on the screen. The numbers are the ones the system itself reports — the same ones the developer
 * options show.
 */
final class Panel {
    private Panel() {}

    /** One app (or service) and what it is using. */
    static final class Entry {
        final String name;
        final int percent;      // the CPU, in percent (0 when it is about the memory)
        long size;              // the memory, in bytes

        Entry(String name, int percent, long size) {
            this.name = name;
            this.percent = percent;
            this.size = size;
        }
    }

    /** Everything the panel shows. */
    static final class Data {
        long ramTotal, ramUsed, ramFree, swapTotal, swapUsed, cache, apps;
        int battery, temperature, processes;
        long freeSpace, totalSpace, uptime;
        String ssid = "", ip = "", speed = "";
        int signal;
        final List<Entry> cpu = new ArrayList<>();
        final List<Entry> memory = new ArrayList<>();

        /** A size in a language people read ("1,2 GB"). */
        String size(long bytes) {
            if (bytes >= 1024L * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024 * 1024));
            if (bytes >= 1024L * 1024) return String.format(Locale.getDefault(), "%.0f MB", bytes / (1024.0 * 1024));
            return String.format(Locale.getDefault(), "%.0f kB", bytes / 1024.0);
        }
    }

    /** Reads the panel. */
    static Data read(Adb adb) throws IOException {
        Data data = new Data();
        memory(adb, data);
        processor(adb, data);
        battery(adb, data);
        tablet(adb, data);
        data.processes = adb.shell("ps -A | wc -l").trim().split("\\s+")[0].length() > 0
                ? Integer.parseInt(adb.shell("ps -A | wc -l").trim()) : 0;
        return data;
    }

    /** The memory: the totals, the swap and the biggest apps. */
    private static void memory(Adb adb, Data data) throws IOException {
        String out = adb.shell("dumpsys meminfo");
        // The system writes them in kB, with the marks of thousands ("1,892,156K").
        data.ramTotal = number(out, "Total RAM: ") * 1024;
        data.ramUsed = number(out, "Used RAM: ") * 1024;
        data.ramFree = number(out, "Free RAM: ") * 1024;
        // The swap: what the system says about it (in kB).
        String meminfo = adb.shell("cat /proc/meminfo");
        long swapTotal = value(meminfo, "SwapTotal:");
        long swapFree = value(meminfo, "SwapFree:");
        data.swapTotal = swapTotal * 1024;
        data.swapUsed = Math.max(0, (swapTotal - swapFree) * 1024);
        // The apps: "   119,752K: system (pid 32393)".
        for (String line : out.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.endsWith(")") || !trimmed.contains(":")) continue;
            int colon = trimmed.indexOf(':');
            if (colon < 0) continue;
            long kb = plain(trimmed.substring(0, colon));
            if (kb < 20000) continue;   // less than 20 MB does not matter
            String name = trimmed.substring(colon + 1).trim();
            int space = name.indexOf(' ');
            if (space > 0) name = name.substring(0, space);
            // A process with a colon in the name (a webview) shows only the app.
            int cut = name.indexOf(':');
            if (cut > 0) name = name.substring(0, cut);
            if (name.length() > 30) name = name.substring(0, 29) + "…";
            // The system lists an app more than once (the summary and the details): the biggest one wins.
            boolean known = false;
            for (Entry entry : data.memory) {
                if (!entry.name.equals(name)) continue;
                known = true;
                if (entry.size < kb * 1024) entry.size = kb * 1024;
                break;
            }
            if (!known) data.memory.add(new Entry(name, 0, kb * 1024));
        }
        Collections.sort(data.memory, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                return Long.compare(b.size, a.size);
            }
        });
    }

    /** The processor: who is using it (the system's own measure, over the last seconds). */
    private static void processor(Adb adb, Data data) throws IOException {
        String out = adb.shell("dumpsys cpuinfo");
        for (String line : out.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.contains("%")) continue;
            int space = trimmed.indexOf(' ');
            if (space < 0) continue;
            int percent;
            try {
                percent = Integer.parseInt(trimmed.substring(0, space).replace("%", "").trim());
            } catch (NumberFormatException e) {
                continue;
            }
            int slash = trimmed.indexOf('/');
            int colon = trimmed.indexOf(':', slash < 0 ? 0 : slash);
            if (slash < 0 || colon < 0) continue;
            String name = trimmed.substring(slash + 1, colon);
            if (percent < 1) continue;
            data.cpu.add(new Entry(name, percent, 0));
        }
        Collections.sort(data.cpu, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                return b.percent - a.percent;
            }
        });
    }

    /** The storage, the network and how long the tablet has been on. */
    private static void tablet(Adb adb, Data data) throws IOException {
        String df = adb.shell("df /data | tail -1");
        String[] parts = df.trim().split("\\s+");
        if (parts.length >= 4) {
            try {
                data.totalSpace = Long.parseLong(parts[1]) * 1024;
                data.freeSpace = Long.parseLong(parts[3]) * 1024;
            } catch (NumberFormatException ignored) {
            }
        }
        try {
            String up = adb.shell("cat /proc/uptime").trim();
            data.uptime = (long) Double.parseDouble(up.split("\\s+")[0]);
        } catch (Exception ignored) {
        }
        try {
            String wifi = adb.shell("dumpsys wifi | grep -i mWifiInfo | head -1");
            int ssid = wifi.indexOf("SSID: ");
            if (ssid >= 0) {
                int end = wifi.indexOf(',', ssid);
                data.ssid = wifi.substring(ssid + 6, end < 0 ? wifi.length() : end).trim();
            }
            int rssi = wifi.indexOf("RSSI: ");
            if (rssi >= 0) {
                int end = wifi.indexOf(',', rssi);
                data.signal = Integer.parseInt(wifi.substring(rssi + 6, end < 0 ? wifi.length() : end).trim());
            }
            int speed = wifi.indexOf("Link speed: ");
            if (speed >= 0) {
                int end = wifi.indexOf(',', speed);
                data.speed = wifi.substring(speed + 12, end < 0 ? wifi.length() : end).trim();
            }
            String ip = adb.shell("ip -4 addr show wlan0 | grep inet");
            int at = ip.indexOf("inet ");
            if (at >= 0) {
                int end = ip.indexOf('/', at);
                data.ip = ip.substring(at + 5, end < 0 ? ip.length() : end).trim();
            }
        } catch (Exception ignored) {
        }
    }

    /** The battery: the level and the temperature. */
    private static void battery(Adb adb, Data data) throws IOException {
        String out = adb.shell("dumpsys battery");
        data.battery = (int) value(out, "level:");
        data.temperature = (int) (value(out, "temperature:") / 10.0);
    }

    /** The first number of a text, ignoring the marks of thousands ("1,892,156K" → 1892156). */
    private static long plain(String text) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) digits.append(c);
            else if (c == ',' || c == ' ' || c == '\t') continue;   // the marks of thousands and the spaces
            else break;
        }
        try {
            return Long.parseLong(digits.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** A number after some text ("Total RAM: 1,892,156K" → 1892156). */
    /** The number after that text, with the marks of thousands ("1,892,156K" → 1892156). */
    static long number(String text, String start) {
        int at = text.indexOf(start);
        if (at < 0) return 0;
        return plain(text.substring(at + start.length()));
    }

    /** A number after some text ("SwapTotal:  1539496 kB"). */
    /** The number after that text (0 when it is not there). */
    static long value(String text, String start) {
        int at = text.indexOf(start);
        if (at < 0) return 0;
        String rest = text.substring(at + start.length()).trim();
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (Character.isDigit(c)) digits.append(c);
            else if (digits.length() > 0) break;
        }
        try {
            return Long.parseLong(digits.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
