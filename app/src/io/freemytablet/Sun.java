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
 * The sunrise and the sunset where you are (Open-Meteo, no key), so the home screen can follow the day.
 * The times are fetched once a day in the background and kept in the settings: without them, the defaults
 * (5:30 and 17:40) are used. Nothing else is fetched, and no data leaves the tablet but the coordinates.
 */
final class Sun {
    private Sun() {}

    private static final String QUERY = "&daily=sunrise,sunset";
    private static final String PREFS = "sun";
    private static volatile boolean fetching;

    static long sunrise(Context c) { return times(c)[0]; }
    static long sunset(Context c) { return times(c)[1]; }

    /** Today's sunrise and sunset (epoch ms); 0 when there is no data yet. */
    private static long[] times(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long day = sp.getLong("day", 0);
        long now = System.currentTimeMillis();
        if (day > 0 && sameDay(day, now)) return new long[] {sp.getLong("rise", 0), sp.getLong("set", 0)};
        fetch(c);
        return new long[] {sp.getLong("rise", 0), sp.getLong("set", 0)};
    }

    private static boolean sameDay(long a, long b) {
        java.util.Calendar ca = java.util.Calendar.getInstance();
        ca.setTimeInMillis(a);
        java.util.Calendar cb = java.util.Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
                && ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR);
    }

    /** Fetches the times in the background: the home screen never waits for the network. */
    private static void fetch(Context c) {
        if (fetching) return;
        fetching = true;
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                String body = get(url());
                JSONObject o = new JSONObject(body);
                TimeZone zone = TimeZone.getTimeZone(o.optString("timezone", TimeZone.getDefault().getID()));
                JSONObject daily = o.getJSONObject("daily");
                long rise = localTime(daily.getJSONArray("sunrise").getString(0), zone);
                long set = localTime(daily.getJSONArray("sunset").getString(0), zone);
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putLong("day", System.currentTimeMillis()).putLong("rise", rise).putLong("set", set).apply();
            } catch (Exception e) {
                AppLog.w("could not fetch the sun times: " + e.getMessage());
            } finally {
                fetching = false;
            }
        }, "sun").start();
    }

    private static String url() {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + Prefs.latitude() + "&longitude=" + Prefs.longitude() + QUERY;
    }

    private static long localTime(String iso, TimeZone zone) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        f.setTimeZone(zone);
        return f.parse(iso).getTime();
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
