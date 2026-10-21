package io.freemytablet;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings changed on the tablet itself (Free My Tablet > Settings). config.properties only gives the first
 * values; anything the user changes here wins. Read from the :home process (home screen and settings).
 */
final class Prefs {
    private Prefs() {}

    /** Bumped by every change: screens compare it to know when to redraw. */
    static volatile int version;

    private static Context app;

    static void init(Context c) {
        app = c.getApplicationContext();
        Ui.applyLanguage();   // the language picked on the tablet, now that it can be read
    }

    private static SharedPreferences sp() {
        return app.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    private static void put(String key, String value) {
        sp().edit().putString(key, value).apply();
        version++;
    }

    private static void put(String key, int value) {
        sp().edit().putInt(key, value).apply();
        version++;
    }

    private static void put(String key, boolean value) {
        sp().edit().putBoolean(key, value).apply();
        version++;
    }

    // MARK: - Language and units

    /** "auto", "pt", "es" or "en". */
    static String language() {
        String v = app == null ? Config.LANGUAGE : sp().getString("language", Config.LANGUAGE);
        v = v.trim().toLowerCase(Locale.ROOT);
        return v.startsWith("pt") ? "pt" : v.startsWith("es") ? "es" : v.startsWith("en") ? "en" : "auto";
    }

    static void setLanguage(String v) { put("language", v); Ui.applyLanguage(); }

    /** "auto", "C" or "F". */
    static String temperature() {
        String v = sp().getString("temperature", Config.TEMPERATURE).trim().toUpperCase(Locale.ROOT);
        return v.startsWith("F") ? "F" : v.startsWith("C") ? "C" : "auto";
    }

    static void setTemperature(String v) { put("temperature", v); }

    /** "auto" (dark from sunset to sunrise), "light" or "dark". */
    static String theme() {
        String v = sp().getString("theme", "auto");
        return v.equals("light") || v.equals("dark") ? v : "auto";
    }

    static void setTheme(String v) { put("theme", v); }

    // MARK: - City (from config.properties)

    static String city() { return Config.CITY.trim(); }
    static String latitude() { return Config.LATITUDE; }
    static String longitude() { return Config.LONGITUDE; }

    // MARK: - The weather card

    /** The card with the weather on the home screen (the person can turn it off). */
    static boolean weatherCard() { return sp().getBoolean("weatherCard", true); }
    static void setWeatherCard(boolean on) { put("weatherCard", on); }

    // MARK: - The sync (git pull in Termux, then the copy over the vault)

    /** The working folder in Termux (the one with the git clone). */
    static String syncFolder() {
        String v = sp().getString("syncFolder", "");
        return v.isEmpty() ? "~/pos-coders" : v;
    }

    static void setSyncFolder(String v) { put("syncFolder", v.trim()); }

    /** The command that pulls (git pull, with the arguments the person wants). */
    static String syncCommand() {
        String v = sp().getString("syncCommand", "");
        return v.isEmpty() ? "git pull" : v;
    }

    static void setSyncCommand(String v) { put("syncCommand", v.trim()); }

    // MARK: - The favorites (the row of apps on the home screen)

    /** The apps on the home screen, in order; null until the person edits it (then the built-in list applies). */
    static List<String> favorites() {
        String v = sp().getString("favorites", null);
        if (v == null) return null;
        List<String> out = new ArrayList<>();
        for (String p : v.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    static void setFavorites(List<String> packages) {
        StringBuilder sb = new StringBuilder();
        for (String p : packages) sb.append(sb.length() == 0 ? "" : ",").append(p);
        put("favorites", sb.toString());
    }

    // MARK: - The apps turned off (the optional ones)

    /** The optional apps the user turned off (packages from HomeMode.OPTIONAL). */
    static List<String> optionalOff() {
        List<String> out = new ArrayList<>();
        String v = sp().getString("optionalOff", "");
        for (String p : v.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    static void setOptionalOff(List<String> packages) {
        StringBuilder sb = new StringBuilder();
        for (String p : packages) sb.append(sb.length() == 0 ? "" : ",").append(p);
        put("optionalOff", sb.toString());
    }

    // MARK: - The widget on the home screen

    /** The id of the widget in the slot (0 when there is none). */
    static int widgetId() { return sp().getInt("widgetId", 0); }
    static void setWidgetId(int id) { put("widgetId", id); }

    // MARK: - The vault (the Obsidian folder for the quick note)

    /** The folder the quick note is saved in (a SAF tree URI), or "" when there is none yet. */
    static String vault() {
        return sp().getString("vault", "");
    }

    static void setVault(String uri) { put("vault", uri); }
}
