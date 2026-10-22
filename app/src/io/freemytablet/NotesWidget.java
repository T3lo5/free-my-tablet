package io.freemytablet;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The widget of the app: the last notes of your Obsidian vault, on the home screen. A tap opens the note in
 * Obsidian (obsidian://open?vault=...&file=...).
 *
 * It reads the vault with the permission the person gave (the folder picker), the same one the quick note uses.
 * How many notes fit depends on the size of the slot: the lines that do not fit are hidden.
 */
public class NotesWidget extends AppWidgetProvider {
    /** The lines of notes, from the top. */
    private static final int[] LINES = {R.id.line1, R.id.line2, R.id.line3, R.id.line4, R.id.line5, R.id.line6};

    @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(c, manager, id);
    }

    /** Asks the widget to redraw itself (after a note is saved, for instance). */
    static void refresh(Context c) {
        AppWidgetManager manager = AppWidgetManager.getInstance(c);
        int[] ids = manager.getAppWidgetIds(new android.content.ComponentName(c, NotesWidget.class));
        if (ids == null || ids.length == 0) return;
        for (int id : ids) update(c, manager, id);
    }

    /** The notes are read in a thread: the vault is on the disk and the screen cannot wait for it. */
    private static void update(Context c, AppWidgetManager manager, int id) {
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            RemoteViews views = views(app, manager, id);
            manager.updateAppWidget(id, views);
        }, "widget").start();
    }

    private static RemoteViews views(Context c, AppWidgetManager manager, int id) {
        RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.widget_notes);
        // The system draws the widget: the colors and the sizes go through the API, not through the layout.
        views.setTextColor(R.id.title, 0x99FFFFFF);
        views.setTextViewTextSize(R.id.title, android.util.TypedValue.COMPLEX_UNIT_SP, 12);
        int[] lines = {R.id.line1, R.id.line2, R.id.line3, R.id.line4, R.id.line5, R.id.line6};
        for (int id2 : lines) {
            views.setTextColor(id2, 0xE6FFFFFF);
            views.setTextViewTextSize(id2, android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        }
        views.setTextColor(R.id.footer, 0x99FFFFFF);
        views.setTextViewTextSize(R.id.footer, android.util.TypedValue.COMPLEX_UNIT_SP, 12);

        List<Vault.Note> notes = Vault.notes(c);
        views.setTextViewText(R.id.title, c.getString(R.string.widget_notes_title));
        // The title opens the vault itself.
        views.setOnClickPendingIntent(R.id.title, openVault(c));

        int howMany = howManyLines(c, manager, id);
        for (int i = 0; i < lines.length; i++) {
            if (i < howMany && i < notes.size()) {
                Vault.Note note = notes.get(i);
                views.setTextViewText(lines[i], line(c, note));
                views.setOnClickPendingIntent(lines[i], open(c, note, i));
                views.setViewVisibility(lines[i], View.VISIBLE);
            } else {
                views.setTextViewText(lines[i], "");
                views.setViewVisibility(lines[i], View.GONE);
            }
        }
        if (notes.isEmpty()) {
            views.setTextViewText(R.id.line1, c.getString(R.string.widget_notes_empty));
            views.setViewVisibility(R.id.line1, View.VISIBLE);
        }
        // The footer only appears when there is room for it.
        if (heightOf(c, manager, id) >= 200) {
            views.setTextViewText(R.id.footer, c.getString(R.string.widget_notes_open));
            views.setOnClickPendingIntent(R.id.footer, openVault(c));
            views.setViewVisibility(R.id.footer, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.footer, View.GONE);
        }
        return views;
    }

    /** How many notes fit the slot: the size the person chose (the system tells us). */
    private static int howManyLines(Context c, AppWidgetManager manager, int id) {
        int height = heightOf(c, manager, id);
        // The lines are about 24 dp each (the text and the gap): the slot of 140 dp takes three.
        // In the big one there is room for four notes and the footer ("open the vault").
        if (height >= 200) return 4;
        if (height >= 165) return 4;
        if (height >= 130) return 3;
        return 2;
    }

    /** The height of the slot, in dp (what the person chose). */
    private static int heightOf(Context c, AppWidgetManager manager, int id) {
        try {
            Bundle options = manager.getAppWidgetOptions(id);
            int asked = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            if (asked > 0) return asked;
        } catch (RuntimeException ignored) {
        }
        return 140;
    }

    /** The line of a note: when it was changed and where it is ("hoje · 00 - Painel/Home"). */
    private static String line(Context c, Vault.Note note) {
        return when(c, note.modified) + " · " + shortTitle(note);
    }

    /** "hoje", "ontem" or "22/10" (when the note was changed). */
    private static String when(Context c, long time) {
        if (time <= 0) return "";
        long day = 86400000L;
        long today = startOfDay(System.currentTimeMillis());
        long then = startOfDay(time);
        if (today == then) return c.getString(R.string.widget_notes_today);
        if (today - then == day) return c.getString(R.string.widget_notes_yesterday);
        return new SimpleDateFormat("dd/MM", Locale.getDefault()).format(new Date(time));
    }

    /** Midnight of the day of that instant. */
    private static long startOfDay(long time) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(time);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** The name of the note with the folder it is in ("00 - Painel/Home"), short enough for the line. */
    private static String shortTitle(Vault.Note note) {
        String path = note.path.endsWith(".md") ? note.path.substring(0, note.path.length() - 3) : note.path;
        return path.length() > 42 ? path.substring(0, 41) + "…" : path;
    }

    /** Opens the vault in the Obsidian (without a file). */
    private static PendingIntent openVault(Context c) {
        String vault = Vault.vaultName(c);
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("obsidian://open?vault=" + Uri.encode(vault)));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, 99, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Opens a note in Obsidian. */
    private static PendingIntent open(Context c, Vault.Note note, int index) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(Vault.obsidianUri(note.vault, note.path)));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
