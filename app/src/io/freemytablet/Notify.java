package io.freemytablet;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The noise: the notifications the manufacturer and the stores send (the Play Store announcing itself, the
 * Google app with the news feed, the Galaxy Store with the offers).
 *
 * Android does not let an app silence another one: only the person can. So the app measures who talks the most
 * and takes them straight to the screen where the notification is turned off (one tap, then the switch).
 */
final class Notify {
    private Notify() {}

    /** An app and how many notifications it sent. */
    static final class Talker {
        final String pkg;
        final int count;

        Talker(String pkg, int count) {
            this.pkg = pkg;
            this.count = count;
        }
    }

    /** The apps that sent the most notifications, the chattiest first. */
    static List<Talker> top(Adb adb, int limit) throws IOException {
        String out = adb.shell("dumpsys notification --noredact");
        Map<String, Integer> counts = new HashMap<>();
        int at = 0;
        while ((at = out.indexOf("pkg=", at)) >= 0) {
            int end = at + 4;
            while (end < out.length() && (Character.isLetterOrDigit(out.charAt(end)) || out.charAt(end) == '.' || out.charAt(end) == '_')) end++;
            String pkg = out.substring(at + 4, end);
            if (pkg.contains(".")) {
                Integer old = counts.get(pkg);
                counts.put(pkg, old == null ? 1 : old + 1);
            }
            at = end;
        }
        List<Talker> out2 = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() < 2) continue;
            out2.add(new Talker(entry.getKey(), entry.getValue()));
        }
        Collections.sort(out2, new Comparator<Talker>() {
            @Override public int compare(Talker a, Talker b) {
                return b.count - a.count;
            }
        });
        return out2.size() > limit ? out2.subList(0, limit) : out2;
    }

    /** The system's screen where the notifications of an app are turned off. */
    static Intent settings(String pkg) {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
        intent.setData(Uri.parse("package:" + pkg));
        return intent;
    }
}
