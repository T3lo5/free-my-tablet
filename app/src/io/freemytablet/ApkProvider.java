package io.freemytablet;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Serves the APK that was downloaded, so the system's installer can read it (Android does not let an app
 * hand a file:// path to another app). It is a small provider of our own: the project has no libraries.
 */
public class ApkProvider extends ContentProvider {
    static final String AUTHORITY = "io.freemytablet.apk";
    static final Uri URI = new Uri.Builder().scheme("content").authority(AUTHORITY).path("/apk").build();

    /**
     * The file that is being served (the APK that was downloaded).
     *
     * It is kept in the settings, not in a variable: the provider runs in the app's main process and the screen
     * in the ":home" one, and a variable of one is not seen by the other.
     */
    private static File file() {
        String path = Prefs.apkPath();
        return path.isEmpty() ? null : new File(path);
    }

    /** The APK that was downloaded, ready to be installed. */
    public static void setFile(File apk) {
        Prefs.setApkPath(apk == null ? "" : apk.getAbsolutePath());
    }

    @Override public boolean onCreate() {
        return true;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File file = file();
        if (file == null) return null;
        MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[] {file.getName(), file.length()});
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        // The installer asks for the file with a few different modes ("r", "rt"...): all of them are read.
        File file = file();
        if (file == null || !file.exists()) throw new FileNotFoundException("no APK to read");
        if (mode != null && !mode.contains("r")) throw new FileNotFoundException("read only");
        AppLog.w("the installer is reading the APK (" + file.length() + " bytes)");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
