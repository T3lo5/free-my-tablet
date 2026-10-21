package io.freemytablet;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The quick note goes to the Obsidian vault: the person picks the folder once (the system's own folder picker),
 * and every note is written there as a Markdown file, one per day.
 *
 * The folder is reached through the system's own API (SAF), so the app needs no permission over it and the
 * system asks the person the first time.
 */
final class Vault {
    private Vault() {}

    /** Asks the person for the folder (the system's picker). The answer comes back in onActivityResult. */
    static void pick(Activity a) {
        Intent intent = new Intent(Build.VERSION.SDK_INT >= 21 ? Intent.ACTION_OPEN_DOCUMENT_TREE : Intent.ACTION_GET_CONTENT);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            a.startActivityForResult(intent, HomeActivity.REQ_VAULT);
        } catch (RuntimeException e) {
            AppLog.w("no folder picker on the tablet: " + e.getMessage());
        }
    }

    /** Whether a folder was already chosen. */
    static boolean ready(Context c) {
        return !Prefs.vault().isEmpty();
    }

    /**
     * Writes the note in the vault: one file per day, with every note of the day appended to it.
     * Returns the name of the file, or null when it could not be written.
     */
    static String save(Context c, String note) {
        String tree = Prefs.vault();
        if (tree.isEmpty() || note.trim().isEmpty()) return null;
        try {
            Uri folder = Uri.parse(tree);
            ContentResolver resolver = c.getContentResolver();
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            String name = Ui.tr("Nota rápida ", "Quick note ") + today + ".md";
            Uri file = find(resolver, folder, name);
            boolean fresh = file == null;
            if (fresh) {
                file = DocumentsContract.createDocument(resolver, folder, "text/markdown", name);
                if (file == null) return null;
            }
            try (OutputStream out = resolver.openOutputStream(file, "wt")) {
                if (out == null) return null;
                String text = fresh ? "" : "\n\n";
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.write(("# " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()) + "\n\n")
                        .getBytes(StandardCharsets.UTF_8));
                out.write(note.trim().getBytes(StandardCharsets.UTF_8));
                out.write("\n".getBytes(StandardCharsets.UTF_8));
            }
            return name;
        } catch (Exception e) {
            AppLog.w("could not save the note: " + e.getMessage());
            return null;
        }
    }

    /** The document with that name inside the folder, or null. */
    private static Uri find(ContentResolver resolver, Uri folder, String name) {
        try {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(folder,
                    DocumentsContract.getTreeDocumentId(folder));
            try (android.database.Cursor cursor = resolver.query(children,
                    new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null)) {
                if (cursor == null) return null;
                while (cursor.moveToNext()) {
                    if (!name.equals(cursor.getString(1))) continue;
                    return DocumentsContract.buildDocumentUriUsingTree(folder, cursor.getString(0));
                }
            }
        } catch (Exception e) {
            AppLog.w("could not look in the vault: " + e.getMessage());
        }
        return null;
    }
}
