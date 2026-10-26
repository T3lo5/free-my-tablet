package io.freemytablet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** System notices for the main process: the tablet booted, or this app was updated. Not exported. */
public class HomeReceiver extends BroadcastReceiver {
    /** A note was saved (sent by :home; the widget lives in this process). */
    static final String NOTES_CHANGED = "io.freemytablet.NOTES_CHANGED";

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            HomeMode.bootCheck(context);
        } else if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            HomeMode.verify(context);   // app updated: complete Free My Tablet mode if something is missing
        } else if (NOTES_CHANGED.equals(action)) {
            Vault.invalidate();         // this process kept its own list of the notes
            NotesWidget.refresh(context);
        }
    }
}
