package io.freemytablet;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;

/**
 * The widget on the home screen.
 *
 * The person picks it in the system's own widget picker (with the previews), and the system binds it: that way
 * every widget of the tablet works, not only ours. The app's own widget (the last notes of the vault) is one
 * of them.
 */
final class Widgets {
    private Widgets() {}

    /** The host id (one host, one slot). */
    static final int HOST_ID = 1;

    /**
     * Asks the system for the widget picker. The id is allocated here and comes back in the answer.
     */
    static void pick(Activity a, int requestCode) {
        AppWidgetHost host = new AppWidgetHost(a, HOST_ID);
        int id = host.allocateAppWidgetId();
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return;
        // The slot holds one widget: the old one goes away.
        int old = Prefs.widgetId();
        if (old != 0 && old != id) {
            host.deleteAppWidgetId(old);
            Prefs.setWidgetId(0);
        }
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        try {
            a.startActivityForResult(intent, requestCode);
            AppLog.w("the widget picker was asked (id " + id + ")");
        } catch (RuntimeException e) {
            AppLog.w("no widget picker on the tablet: " + e);
        }
    }

    /**
     * The person picked a widget: the system already bound it. When the widget wants its own settings (the city of
     * a clock widget, for instance), the system asks for them — with a different request code, so the answer is
     * not mistaken for another pick.
     */
    static void picked(Activity a, int configureCode, int id) {
        if (id == 0) return;
        Prefs.setWidgetId(id);
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(a).getAppWidgetInfo(id);
        if (info == null || info.configure == null) {
            done(a, id);
            return;
        }
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
        intent.setComponent(info.configure);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
        try {
            a.startActivityForResult(intent, configureCode);
        } catch (RuntimeException e) {
            AppLog.w("could not open the settings of the widget: " + e.getMessage());
            done(a, id);
        }
    }

    /** The widget is ready (or it did not need any configuration). */
    static void done(Activity a, int id) {
        Prefs.setWidgetId(id);
        NotesWidget.refresh(a);
    }

    /** Takes the widget off the home screen. */
    static void remove(Context c) {
        int id = Prefs.widgetId();
        if (id != 0) {
            new AppWidgetHost(c, HOST_ID).deleteAppWidgetId(id);
        }
        Prefs.setWidgetId(0);
    }
}
