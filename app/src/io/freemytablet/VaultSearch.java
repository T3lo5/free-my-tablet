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
        box.addView(field);
        box.addView(list);

        final List<Vault.Note> notes = new ArrayList<>(Vault.notes(a));
        final NotesAdapter adapter = new NotesAdapter(a, notes);
        list.setAdapter(adapter);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void onTextChanged(CharSequence s, int x, int y, int z) {
                String text = s.toString().trim().toLowerCase(Locale.getDefault());
                List<Vault.Note> found = new ArrayList<>();
                for (Vault.Note note : notes) {
                    if (text.isEmpty() || note.path.toLowerCase(Locale.getDefault()).contains(text)) found.add(note);
                }
                adapter.set(found);
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

    /** Opens a note in Obsidian. */
    static void open(Activity a, Vault.Note note) {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("obsidian://open?vault=" + Uri.encode(note.vault) + "&file=" + Uri.encode(note.path)));
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
