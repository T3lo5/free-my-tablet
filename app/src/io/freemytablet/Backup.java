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

    static final String NAME = ".free-my-tablet.json";

    /** The choices, as JSON. */
    static String json(Context c) {
        JSONObject out = new JSONObject();
        try {
            out.put("version", 1);
            out.put("favorites", new JSONArray(Prefs.favorites()));
            out.put("recent", new JSONArray(Prefs.recent()));
            out.put("theme", Prefs.theme());
            out.put("language", Prefs.language());
            out.put("weatherCard", Prefs.weatherCard());
            out.put("syncFolder", Prefs.syncFolder());
            out.put("syncCommand", Prefs.syncCommand());
            out.put("noteFolder", Prefs.noteFolder());
            List<String> off = Prefs.optionalOff();
            out.put("appsOff", new JSONArray(off));
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
            Prefs.setWeatherCard(in.optBoolean("weatherCard", true));
            String folder = in.optString("syncFolder", "");
            if (!folder.isEmpty()) Prefs.setSyncFolder(folder);
            String command = in.optString("syncCommand", "");
            if (!command.isEmpty()) Prefs.setSyncCommand(command);
            String note = in.optString("noteFolder", "");
            if (!note.isEmpty()) Prefs.setNoteFolder(note);
            List<String> off = strings(in.optJSONArray("appsOff"));
            if (!off.isEmpty()) Prefs.setOptionalOff(off);
            return true;
        } catch (Exception e) {
            AppLog.w("could not read the choices: " + e.getMessage());
            return false;
        }
    }

    private static List<String> strings(JSONArray array) {
        List<String> out = new java.util.ArrayList<>();
        if (array == null) return out;
        for (int i = 0; i < array.length(); i++) out.add(array.optString(i));
        return out;
    }
}
