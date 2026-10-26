package io.freemytablet;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The weather where you are (Open-Meteo, no key), for the card on the home screen: the temperature now, the
 * symbol and the city. It is fetched in the background every half hour and kept in the settings; the home
 * screen never waits for the network (it shows the last values it has).
 */
final class Weather {
    private Weather() {}

    private static final String QUERY = "&current=temperature_2m,weather_code,is_day";
    private static final String PREFS = "weather";
    private static final long FRESH = 30 * 60_000L;   // the card is refreshed twice an hour
    private static volatile boolean fetching;

    /** The temperature now (in the unit the person chose), or 0 when there is nothing yet. */
    static float temperature(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // The value is kept in Celsius, so changing the unit shows the right number at once (no new fetch).
        if (sp.contains("tempC")) return toUnit(sp.getFloat("tempC", 0f));
        return sp.getFloat("temp", 0f);   // an older install: the value was already saved in the unit then
    }

    /** The weather code (Open-Meteo WMO), or -1. */
    static int code(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("code", -1);
    }

    /** Whether it is day where you are (for the symbol). */
    static boolean day(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("day", 1) == 1;
    }

    /** When the values were fetched (0 when never). */
    static long at(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("at", 0);
    }

    /** Fetches the weather in the background, if the last one is old. */
    static void refresh(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - sp.getLong("at", 0) < FRESH) return;
        if (fetching) return;
        fetching = true;
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                JSONObject o = new JSONObject(get(url()));
                JSONObject current = o.getJSONObject("current");
                float celsius = (float) current.getDouble("temperature_2m");
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putFloat("tempC", celsius)   // the source value: the unit is applied when it is shown
                        .putFloat("temp", toUnit(celsius))
                        .putInt("code", current.optInt("weather_code", -1))
                        .putInt("day", current.optInt("is_day", 1))
                        .putLong("at", System.currentTimeMillis())
                        .apply();
            } catch (Exception e) {
                AppLog.w("could not fetch the weather: " + e.getMessage());
            } finally {
                fetching = false;
            }
        }, "weather").start();
    }

    /** A temperature in Celsius, in the unit the person chose ("C" or "F"). */
    static float toUnit(float celsius, String unit) {
        return "F".equals(unit) ? celsius * 9f / 5f + 32f : celsius;
    }

    private static float toUnit(float celsius) {
        return toUnit(celsius, Prefs.temperature());
    }

    private static String url() {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + Prefs.latitude() + "&longitude="
                + Prefs.longitude() + QUERY;
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(6000);
        c.setRequestProperty("User-Agent", "FreeMyTablet");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }
}
