package io.freemytablet;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * The choices of the person (the apps on the home screen, the theme, the sync, the folder of the note) written
 * in a file inside the vault: it can be copied to another tablet, and read back to restore everything.
 */
final class Backup {
    private Backup() {}

    static final String NAME = "free-my-tablet.json";

    /** The choices, as JSON. */
    static String json(Context c) {
        JSONObject out = new JSONObject();
        try {
            out.put("version", 2);
            out.put("favorites", new JSONArray(Prefs.favorites()));
            out.put("recent", new JSONArray(Prefs.recent()));
            out.put("theme", Prefs.theme());
            out.put("language", Prefs.language());
            out.put("weatherCard", Prefs.weatherCard());
            out.put("syncFolder", Prefs.syncFolder());
            out.put("syncCommand", Prefs.syncCommand());
            out.put("noteFolder", Prefs.noteFolder());
            out.put("notePrefix", Prefs.notePrefix());
            out.put("widgetSize", Prefs.widgetSize());
            out.put("temperature", Prefs.temperature());
            List<String> off = Prefs.optionalOff();
            out.put("appsOff", new JSONArray(off));
            // The work areas are choices too: without them the home screen comes back with only the main row.
            JSONArray areas = new JSONArray();
            for (Prefs.Area area : Prefs.areas()) {
                areas.put(new JSONObject().put("name", area.name).put("apps", new JSONArray(area.apps)));
            }
            out.put("areas", areas);
        } catch (Exception e) {
            AppLog.w("could not write the choices: " + e.getMessage());
        }
        return out.toString();
    }

    /** Writes the choices in the vault. Returns the name of the file, or null. */
    static String save(Context c) {
        if (!Vault.ready(c)) return null;
        if (!Vault.write(c, NAME, json(c), "application/json")) return null;
        return NAME;
    }

    /** Reads the choices back from the vault (the file the app itself wrote). */
    static boolean restore(Context c) {
        String text = Vault.read(c, NAME);
        if (text == null || text.isEmpty()) return false;
        try {
            JSONObject in = new JSONObject(text);
            List<String> favorites = strings(in.optJSONArray("favorites"));
            if (!favorites.isEmpty()) Prefs.setFavorites(favorites);
            String theme = in.optString("theme", "");
            if (!theme.isEmpty()) Prefs.setTheme(theme);
            String language = in.optString("language", "");
            if (!language.isEmpty()) Prefs.setLanguage(language);
            if (in.has("weatherCard")) Prefs.setWeatherCard(in.optBoolean("weatherCard", true));
            String folder = in.optString("syncFolder", "");
            if (!folder.isEmpty()) Prefs.setSyncFolder(folder);
            String command = in.optString("syncCommand", "");
            if (!command.isEmpty()) Prefs.setSyncCommand(command);
            if (in.has("noteFolder")) Prefs.setNoteFolder(in.optString("noteFolder", ""));
            String prefix = in.optString("notePrefix", "");
            if (!prefix.isEmpty()) Prefs.setNotePrefix(prefix);
            int widgetSize = in.optInt("widgetSize", 0);
            if (widgetSize >= 80 && widgetSize <= 400) Prefs.setWidgetSize(widgetSize);
            String temperature = in.optString("temperature", "");
            if ("C".equals(temperature) || "F".equals(temperature) || "auto".equals(temperature)) {
                Prefs.setTemperature(temperature);
            }
            // The choice may be "none off": an empty list has to clear the old one, not be ignored.
            if (in.has("appsOff")) Prefs.setOptionalOff(strings(in.optJSONArray("appsOff")));
            if (in.has("recent")) Prefs.setRecent(strings(in.optJSONArray("recent")));
            if (in.has("areas")) Prefs.setAreas(areas(in.optJSONArray("areas")));
            return true;
        } catch (Exception e) {
            AppLog.w("could not read the choices: " + e.getMessage());
            return false;
        }
    }

    /** The work areas of the backup file. */
    private static List<Prefs.Area> areas(JSONArray list) {
        List<Prefs.Area> out = new java.util.ArrayList<>();
        if (list == null) return out;
        for (int i = 0; i < list.length(); i++) {
            JSONObject one = list.optJSONObject(i);
            if (one == null) continue;
            Prefs.Area area = new Prefs.Area(one.optString("name", ""));
            area.apps.addAll(strings(one.optJSONArray("apps")));
            out.add(area);
        }
        return out;
    }

    private static List<String> strings(JSONArray array) {
        List<String> out = new java.util.ArrayList<>();
        if (array == null) return out;
        for (int i = 0; i < array.length(); i++) out.add(array.optString(i));
        return out;
    }
}
