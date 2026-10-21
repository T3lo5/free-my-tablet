package io.freemytablet;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import java.util.List;

/**
 * The widget of the app: the last notes of your Obsidian vault, on the home screen. A tap opens the note in
 * Obsidian (obsidian://open?vault=...&file=...).
 *
 * It reads the vault with the permission the person gave (the folder picker), the same one the quick note uses.
 */
public class NotesWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, views(c));
    }

    /** Asks the widget to redraw itself (after a note is saved, for instance). */
    static void refresh(Context c) {
        AppWidgetManager manager = AppWidgetManager.getInstance(c);
        int[] ids = manager.getAppWidgetIds(new android.content.ComponentName(c, NotesWidget.class));
        if (ids == null || ids.length == 0) return;
        for (int id : ids) manager.updateAppWidget(id, views(c));
    }

    private static RemoteViews views(Context c) {
        RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.widget_notes);
        // The system draws the widget: the colors and the sizes go through the API, not through the layout.
        views.setTextColor(R.id.title, 0x99FFFFFF);
        views.setTextViewTextSize(R.id.title, android.util.TypedValue.COMPLEX_UNIT_SP, 12);
        int[] lines = {R.id.line1, R.id.line2, R.id.line3};
        for (int id : lines) {
            views.setTextColor(id, 0xE6FFFFFF);
            views.setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        }
        List<Vault.Note> notes = Vault.notes(c);
        views.setTextViewText(R.id.title, c.getString(R.string.widget_notes_title));
        AppLog.w("the widget: " + notes.size() + " notes in the vault");
        for (int i = 0; i < lines.length; i++) {
            if (i < notes.size()) {
                Vault.Note note = notes.get(i);
                views.setTextViewText(lines[i], shortTitle(note));
                views.setOnClickPendingIntent(lines[i], open(c, note, i));
            } else {
                views.setTextViewText(lines[i], "");
            }
        }
        if (notes.isEmpty()) views.setTextViewText(R.id.line1, c.getString(R.string.widget_notes_empty));
        return views;
    }

    /** The name of the note with the folder it is in ("00 - Painel/Home"), short enough for the line. */
    private static String shortTitle(Vault.Note note) {
        String path = note.path.endsWith(".md") ? note.path.substring(0, note.path.length() - 3) : note.path;
        return path.length() > 42 ? path.substring(0, 41) + "…" : path;
    }

    /** Opens a note in Obsidian. */
    private static PendingIntent open(Context c, Vault.Note note, int index) {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("obsidian://open?vault=" + Uri.encode(note.vault) + "&file=" + Uri.encode(note.path)));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
