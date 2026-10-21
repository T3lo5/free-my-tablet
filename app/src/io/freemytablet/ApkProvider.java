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

    /** The file that is being served (the APK that was downloaded). */
    private static File file;

    /** The APK that was downloaded, ready to be installed. */
    public static void setFile(File apk) {
        file = apk;
    }

    @Override public boolean onCreate() {
        return true;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        if (file == null) return null;
        MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[] {file.getName(), file.length()});
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (file == null || !"r".equals(mode)) throw new FileNotFoundException("no APK to read");
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
