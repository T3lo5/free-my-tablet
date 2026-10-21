package io.freemytablet;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The apps on the tablet: the favorites on the home screen, all the others in "all the apps", and the ones that
 * stay out of both.
 * The list comes from the system (the apps that can be opened), sorted by label; the favorites are the person's
 * choice (Prefs.favorites) and fall back to a small built-in list.
 * The icons are drawn once into a bitmap of a fixed size and kept: the home screen redraws them every frame
 * and a Drawable is too expensive to draw that often.
 */
final class HomeApps {
    private HomeApps() {}

    /** Side of the icon bitmap (px). The icons are drawn scaled down from here. */
    static final int ICON = 128;

    static final class Item {
        final String pkg;
        final String label;
        Drawable icon;          // the system's icon, drawn once into a bitmap

        Item(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }

    /** Never on the screen: the setup wizards and the apps with no icon. Our own app stays (it opens the settings). */
    private static final Set<String> HIDDEN = new HashSet<>(Arrays.asList(
            "com.samsung.android.setupwizard",
            "com.google.android.tungsten.setupwraith",
            "com.android.vending",       // the Play Store: it stays out of the way
            "com.sec.android.app.launcher"  // Samsung's own home screen (off in Free mode)
    ));

    /**
     * The favorites before the person picks their own: the apps a tablet like this is used for. Only the ones
     * that are installed show up.
     */
    private static final String[] DEFAULT_FAVORITES = {
            "md.obsidian",                       // Obsidian
            "org.readera",                       // ReadEra (reading)
            "com.termux",                        // Termux
            "com.google.android.youtube",        // YouTube
            "org.chromium.webapk.a4cd8e42e8282fcd4_v2",   // Astron Members (a web app)
            "org.chromium.webapk.a638aa1485b7de616_v2",   // CODERS (a web app)
    };

    private static final Map<String, Bitmap> ICONS = new HashMap<>();

    // MARK: - The lists

    /** Every app that can be opened, sorted by label (the language's order, not the system's). */
    static List<Item> all(Context c) {
        PackageManager pm = c.getPackageManager();
        List<Item> out = new ArrayList<>();
        List<ResolveInfo> resolved = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER), 0);
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : resolved) {
            ApplicationInfo app = info.activityInfo.applicationInfo;
            if (app == null) continue;
            String pkg = app.packageName;
            if (HIDDEN.contains(pkg) || !seen.add(pkg)) continue;
            String label = String.valueOf(pm.getApplicationLabel(app));
            Item item = new Item(pkg, label);
            item.icon = info.loadIcon(pm);
            out.add(item);
        }
        Collections.sort(out, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) {
                int c = a.label.compareToIgnoreCase(b.label);
                return c != 0 ? c : a.pkg.compareTo(b.pkg);
            }
        });
        return out;
    }

    /** The favorites before the person picks their own. */
    static List<String> defaultFavorites() {
        return Arrays.asList(DEFAULT_FAVORITES);
    }

    /** The favorites: the person's choice, or the built-in list (only the apps that are there). */
    static List<Item> favorites(Context c, List<Item> all) {
        List<String> wanted = Prefs.favorites();
        if (wanted == null) wanted = defaultFavorites();
        return of(wanted, all);
    }

    /** The apps of a list of packages (the ones that are still installed). */
    static List<Item> of(List<String> wanted, List<Item> all) {
        Map<String, Item> byPkg = new HashMap<>();
        for (Item item : all) byPkg.put(item.pkg, item);
        List<Item> out = new ArrayList<>();
        if (wanted == null) return out;
        for (String pkg : wanted) {
            Item item = byPkg.get(pkg);
            if (item != null) out.add(item);
        }
        return out;
    }

    /** The apps that are not on the home screen (for the picker in the settings). */
    static List<Item> others(Context c, List<Item> all) {
        Set<String> inFavorites = new HashSet<>();
        for (Item item : favorites(c, all)) inFavorites.add(item.pkg);
        List<Item> out = new ArrayList<>();
        for (Item item : all) if (!inFavorites.contains(item.pkg)) out.add(item);
        return out;
    }

    // MARK: - Icons

    /** The icon of an app, drawn once into a bitmap (null while it is being drawn in the background). */
    static Bitmap icon(Context c, Item item) {
        String key = item.pkg + "@" + ICON;
        synchronized (ICONS) {
            Bitmap b = ICONS.get(key);
            if (b != null) return b;
        }
        if (item.icon == null) return null;
        Bitmap b = Bitmap.createBitmap(ICON, ICON, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        Drawable d = item.icon;
        // The icon keeps its own size: a small one is drawn in the middle, a big one fills the square.
        int w = d.getIntrinsicWidth(), h = d.getIntrinsicHeight();
        if (w <= 0 || h <= 0) { w = h = ICON; }
        float scale = Math.min((float) ICON / w, (float) ICON / h);
        int dw = Math.max(1, Math.round(w * scale)), dh = Math.max(1, Math.round(h * scale));
        d.setBounds((ICON - dw) / 2, (ICON - dh) / 2, (ICON + dw) / 2, (ICON + dh) / 2);
        d.draw(canvas);
        synchronized (ICONS) {
            ICONS.put(key, b);
            if (ICONS.size() > 120) ICONS.clear();
        }
        return b;
    }

    // MARK: - Opening

    /** Opens an app (the person's own icon, the one they tapped). */
    static boolean open(Activity a, Item item) {
        if (item == null) return false;
        Intent intent = a.getPackageManager().getLaunchIntentForPackage(item.pkg);
        if (intent == null) {
            intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(item.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            a.startActivity(intent);
            Prefs.addRecent(item.pkg);   // the apps opened lately
            return true;
        } catch (RuntimeException e) {
            AppLog.w("could not open " + item.pkg + ": " + e.getMessage());
            return false;
        }
    }

    /** The system's own page for the app (long press on an icon). */
    static void info(Activity a, Item item) {
        if (item == null) return;
        try {
            a.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", item.pkg, null)));
        } catch (RuntimeException e) {
            AppLog.w("could not open the app info: " + e.getMessage());
        }
    }
}
