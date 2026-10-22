package io.freemytablet;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The notes of the vault, and the search in them: a dialog with a field and the results, and a tap opens the note
 * in Obsidian (the same obsidian:// the widget uses).
 */
final class VaultSearch {
    private VaultSearch() {}

    /** Opens the search. */
    static void show(Activity a) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        EditText field = new EditText(a);
        field.setHint(Ui.tr("Buscar uma nota…", "Search a note…"));
        field.setSingleLine(true);
        int pad = Math.round(20 * a.getResources().getDisplayMetrics().density);
        field.setPadding(pad, pad, pad, pad);
        ListView list = new ListView(a);
        TextView empty = new TextView(a);
        empty.setText(Ui.tr("Procurando as notas…", "Looking for the notes…"));
        empty.setTextColor(0x99FFFFFF);
        empty.setPadding(pad, pad, pad, pad);
        box.addView(field);
        box.addView(list);
        box.addView(empty);
        list.setEmptyView(empty);

        final List<Vault.Note> notes = new ArrayList<>();
        final NotesAdapter adapter = new NotesAdapter(a, notes);
        list.setAdapter(adapter);
        // The walk through the vault is slow (the system asks about every folder): it happens apart from the
        // screen, and the notes show up as they are found.
        // The notes come one by one, with the text inside: the search finds the word in the note.
        // The list is not redrawn for each note (that would cost a lot of processor): the notes come
        // in batches, two or three times a second.
        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final boolean[] waiting = {false};
        Vault.notesWithText(a, note -> a.runOnUiThread(() -> {
            notes.add(note);
            if (waiting[0]) return;
            waiting[0] = true;
            handler.postDelayed(() -> {
                waiting[0] = false;
                adapter.set(filter(notes, field.getText().toString()));
            }, 400);
        }));
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void onTextChanged(CharSequence s, int x, int y, int z) {
                adapter.set(filter(notes, s.toString()));
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        final android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
        list.setOnItemClickListener((parent, view, position, id) -> {
            Vault.Note note = adapter.get(position);
            if (note == null) return;
            open(a, note);
            if (dialog[0] != null) dialog[0].dismiss();
        });
        dialog[0] = new android.app.AlertDialog.Builder(a)
                .setTitle(Ui.tr("Buscar no vault", "Search the vault"))
                .setView(box)
                .setNegativeButton(Ui.tr("Fechar", "Close"), null)
                .show();
    }

    /** The notes whose path has the text — or whose text inside has it. */
    static List<Vault.Note> filter(List<Vault.Note> notes, String text) {
        String wanted = text == null ? "" : text.trim().toLowerCase(Locale.getDefault());
        List<Vault.Note> found = new ArrayList<>();
        for (Vault.Note note : notes) {
            if (wanted.isEmpty()) {
                found.add(note);
                continue;
            }
            if (note.path.toLowerCase(Locale.getDefault()).contains(wanted)
                    || (!note.lower.isEmpty() && note.lower.contains(wanted))) {
                found.add(note);
            }
        }
        return found;
    }

    /** Opens a note in Obsidian. */
    static void open(Activity a, Vault.Note note) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(Vault.obsidianUri(note.vault, note.path)));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            a.startActivity(intent);
        } catch (RuntimeException e) {
            AppLog.w("could not open the note in Obsidian: " + e.getMessage());
        }
    }

    /** The list of notes. */
    private static final class NotesAdapter extends BaseAdapter {
        private final Activity activity;
        private final List<Vault.Note> notes = new ArrayList<>();

        NotesAdapter(Activity a, List<Vault.Note> list) {
            activity = a;
            notes.addAll(list);
        }

        void set(List<Vault.Note> list) {
            notes.clear();
            notes.addAll(list);
            notifyDataSetChanged();
        }

        @Override public int getCount() {
            return notes.size();
        }

        @Override public Object getItem(int position) {
            return notes.get(position);
        }

        @Override public long getItemId(int position) {
            return position;
        }

        Vault.Note get(int position) {
            return position < notes.size() ? notes.get(position) : null;
        }

        @Override public View getView(int position, View convert, ViewGroup parent) {
            TextView view = (TextView) convert;
            if (view == null) {
                view = new TextView(activity);
                int pad = Math.round(16 * activity.getResources().getDisplayMetrics().density);
                view.setPadding(pad, pad, pad, pad);
                view.setTextColor(0xFFFFFFFF);
                view.setTextSize(15);
            }
            Vault.Note note = notes.get(position);
            view.setText(note.path);
            return view;
        }
    }
}
