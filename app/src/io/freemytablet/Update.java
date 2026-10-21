package io.freemytablet;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The updates: the app asks GitHub what the latest release is and, when there is a new one, downloads it and
 * hands it to the system's installer. No APK to download by hand, no repository to watch.
 *
 * The repository is public, so the API answers without a key. The app is installed from that same APK, so the
 * update is a normal update (the same signature): Android asks the person to confirm the installation.
 */
final class Update {
    private Update() {}

    private static final String RELEASES =
            "https://api.github.com/repos/T3lo5/free-my-tablet/releases/latest";

    /** What there is to know about an update. */
    static final class Info {
        final String version;    // "1.1"
        final String notes;      // the release notes
        final String url;        // where the APK is
        final long size;         // the size of the APK, in bytes

        Info(String version, String notes, String url, long size) {
            this.version = version;
            this.notes = notes;
            this.url = url;
            this.size = size;
        }
    }

    /** The version installed on the tablet. */
    static String installed(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /**
     * Asks GitHub for the latest release. Returns null when there is nothing new (or when the network is
     * not there).
     */
    static Info check(Context c) {
        try {
            String body = get(RELEASES);
            JSONObject release = new JSONObject(body);
            String tag = release.optString("tag_name", "");
            String version = tag.startsWith("v") ? tag.substring(1) : tag;
            if (version.isEmpty()) return null;
            if (!newer(version, installed(c))) return null;
            String url = "";
            long size = 0;
            JSONArray assets = release.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset == null) continue;
                    String name = asset.optString("name", "");
                    if (name.endsWith(".apk")) {
                        url = asset.optString("browser_download_url", "");
                        size = asset.optLong("size", 0);
                        break;
                    }
                }
            }
            if (url.isEmpty()) return null;
            return new Info(version, release.optString("body", ""), url, size);
        } catch (Exception e) {
            AppLog.w("could not check for updates: " + e.getMessage());
            return null;
        }
    }

    /** Whether `version` is newer than `current` ("1.10" is newer than "1.9"). */
    static boolean newer(String version, String current) {
        String[] a = version.split("[.\\-]");
        String[] b = (current == null ? "" : current).split("[.\\-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = number(a, i), y = number(b, i);
            if (x != y) return x > y;
        }
        return false;
    }

    private static int number(String[] parts, int i) {
        if (i >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[i].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Downloads the APK and opens the system's installer. The person confirms the installation there.
     * Returns the file that was downloaded, or null when it could not be.
     */
    static File download(Context c, String url) throws IOException {
        File file = new File(c.getCacheDir(), "FreeMyTablet.apk");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("User-Agent", "FreeMyTablet");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
            try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            }
        } finally {
            connection.disconnect();
        }
        return file;
    }

    /**
     * Hands the downloaded APK to the system's installer.
     *
     * It goes straight to the installer (and not through the system's "open with" chooser): the chooser can lose
     * the permission to read the file, and the installer would then say that the package is broken.
     */
    static void install(Context c, File file) {
        ApkProvider.setFile(file);
        Uri uri = ApkProvider.URI;
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(intent);
        } catch (RuntimeException e) {
            AppLog.w("the installer did not take it: " + e.getMessage());
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(uri, "application/vnd.android.package-archive");
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                c.startActivity(view);
            } catch (RuntimeException e2) {
                AppLog.w("could not open the installer: " + e2.getMessage());
            }
        }
    }

    private static String get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("User-Agent", "FreeMyTablet");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
            try (InputStream in = connection.getInputStream()) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }
}
