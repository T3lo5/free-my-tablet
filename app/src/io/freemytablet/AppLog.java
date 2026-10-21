package io.freemytablet;

import android.util.Log;

/** The app's log (adb logcat -s FreeMyTablet). Never logs keys. */
final class AppLog {
    private AppLog() {}

    static void w(String message) {
        Log.i("FreeMyTablet", message);
    }
}
