package io.freemytablet;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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

    /** The notes of the last walk through the vault (kept for a while: the walk is slow). */
    private static List<Note> cache;
    private static long cacheAt;

    /**
     * The names the note of the day had before the prefix was kept in the settings. Looking for them keeps the
     * notes of yesterday's language in the same file after a language change.
     */
    private static final String[] OLD_NOTE_PREFIXES = {"Nota rápida ", "Quick note "};

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
            Uri root = Uri.parse(tree);
            String rootId = DocumentsContract.getTreeDocumentId(root);
            ContentResolver resolver = c.getContentResolver();
            // The path of the day: the file that already exists wins, even when its name came from another language.
            String path = todayPath(c);
            int slash = path.lastIndexOf('/');
            String name = slash < 0 ? path : path.substring(slash + 1);
            // The folder of the note (inside the vault), made when it does not exist yet.
            String folderId = rootId;
            String sub = Prefs.noteFolder();
            if (!sub.isEmpty()) {
                folderId = subFolder(resolver, root, rootId, sub);
                if (folderId == null) return null;
            }
            Uri file = find(resolver, root, folderId, name);
            boolean fresh = file == null;
            if (fresh) {
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(root, folderId);
                file = DocumentsContract.createDocument(resolver, parent, "text/markdown", name);
                if (file == null) return null;
            }
            String block = (fresh ? "" : "\n\n")
                    + "# " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()) + "\n\n"
                    + note.trim() + "\n";
            if (fresh) {
                writeText(resolver, file, block);
            } else {
                // The file has the notes of the day: append, never truncate.
                append(resolver, file, block);
            }
            cache = null;
            notifyChanged(c);
            return name;
        } catch (Exception e) {
            AppLog.w("could not save the note: " + e);
            return null;
        } finally {
            cache = null;   // the notes changed: the widget and the search have to look again
        }
    }

    /**
     * The name of the note of the day. The prefix is kept in the settings the first time, so changing the
     * language does not split the day's notes between two files.
     */
    static String noteName(String prefix, String day) {
        return prefix + day + ".md";
    }

    private static String notePrefix(Context c) {
        String prefix = Prefs.notePrefix();
        if (!prefix.isEmpty()) return prefix;
        prefix = Ui.tr("Nota rápida ", "Quick note ");
        Prefs.setNotePrefix(prefix);
        return prefix;
    }

    /**
     * The path of the note of the day inside the vault (the folder included). When a note of today already
     * exists with the name of an older language, that name is kept: the day's notes stay in one file.
     */
    static String todayPath(Context c) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String name = noteName(notePrefix(c), today);
        String folder = Prefs.noteFolder();
        String tree = Prefs.vault();
        if (!tree.isEmpty()) {
            try {
                Uri root = Uri.parse(tree);
                String rootId = DocumentsContract.getTreeDocumentId(root);
                ContentResolver resolver = c.getContentResolver();
                // Only look: a folder that is not there yet is made by save (and by ensure).
                String folderId = folder.isEmpty() ? rootId
                        : subFolder(resolver, root, rootId, folder, false);
                if (folderId != null && find(resolver, root, folderId, name) == null) {
                    for (String old : OLD_NOTE_PREFIXES) {
                        String other = noteName(old, today);
                        if (!other.equals(name) && find(resolver, root, folderId, other) != null) {
                            name = other;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                AppLog.w("could not look for the note of the day: " + e.getMessage());
            }
        }
        return folder.isEmpty() ? name : folder + "/" + name;
    }

    /** The notes changed (a note was saved): the widget and the search have to look again. */
    static void invalidate() {
        cache = null;
    }

    /** Asks the main process (where the widget lives) to forget its copy of the notes and redraw the widget. */
    private static void notifyChanged(Context c) {
        try {
            c.sendBroadcast(new Intent(HomeReceiver.NOTES_CHANGED).setPackage(c.getPackageName()));
        } catch (RuntimeException e) {
            AppLog.w("could not tell the widget about the note: " + e.getMessage());
        }
    }

    /** Writes the whole file (a new note, or the fallback when the provider has no append mode). */
    private static void writeText(ContentResolver resolver, Uri file, String text) throws IOException {
        try (OutputStream out = resolver.openOutputStream(file, "rwt")) {
            if (out == null) throw new IOException("no output stream");
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Appends to the file. Some folder providers do not support "wa": then the old text is read and the file is
     * written again with the new block at the end.
     */
    private static void append(ContentResolver resolver, Uri file, String text) throws IOException {
        try (OutputStream out = resolver.openOutputStream(file, "wa")) {
            if (out == null) throw new IOException("no append stream");
            out.write(text.getBytes(StandardCharsets.UTF_8));
            return;
        } catch (IOException | RuntimeException e) {
            AppLog.w("append not supported, rewriting the note: " + e.getMessage());
        }
        String old = readText(resolver, file);
        writeText(resolver, file, old + text);
    }

    /** The text of a document (the note of the day, before it is written again). */
    private static String readText(ContentResolver resolver, Uri file) throws IOException {
        try (InputStream in = resolver.openInputStream(file)) {
            if (in == null) return "";
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /**
     * A folder inside the vault (made when it does not exist), or null. The system is asked about the tree
     * and the id of the folder: the tree alone is not enough to look inside a folder.
     */
    /**
     * Makes sure the file is there (inside the note's folder), with that content when it does not exist yet.
     * Returns true when the file is there.
     */
    static boolean ensure(Context c, String path, String content) {
        String tree = Prefs.vault();
        if (tree.isEmpty()) return false;
        try {
            Uri root = Uri.parse(tree);
            String rootId = DocumentsContract.getTreeDocumentId(root);
            ContentResolver resolver = c.getContentResolver();
            String folderId = rootId;
            String name = path;
            int slash = path.lastIndexOf('/');
            if (slash > 0) {
                folderId = subFolder(resolver, root, rootId, path.substring(0, slash));
                if (folderId == null) return false;
                name = path.substring(slash + 1);
            }
            if (find(resolver, root, folderId, name) != null) return true;   // it is already there
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(root, folderId);
            Uri created = DocumentsContract.createDocument(resolver, parent, "text/markdown", name);
            if (created == null) return false;
            try (OutputStream out = resolver.openOutputStream(created, "rwt")) {
                if (out == null) return false;
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception e) {
            AppLog.w("could not make the note of the day: " + e.getMessage());
            return false;
        }
    }

    private static String subFolder(ContentResolver resolver, Uri tree, String documentId, String path)
            throws IOException {
        return subFolder(resolver, tree, documentId, path, true);
    }

    /**
     * The folder inside the vault, or null. With create=false it is only looked for: the settings and the note of
     * the day must not make folders just by being read.
     */
    private static String subFolder(ContentResolver resolver, Uri tree, String documentId, String path,
            boolean create) throws IOException {
        String current = documentId;
        for (String part : path.split("/")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            Uri child = find(resolver, tree, current, part);
            if (child == null) {
                if (!create) return null;
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, current);
                child = DocumentsContract.createDocument(resolver, parent,
                        DocumentsContract.Document.MIME_TYPE_DIR, part);
                if (child == null) return null;
            }
            current = DocumentsContract.getDocumentId(child);
        }
        return current;
    }

    /** The document with that name inside the folder (the tree and the id of the folder), or null. */
    private static Uri find(ContentResolver resolver, Uri tree, String documentId, String name) {
        try {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId);
            try (android.database.Cursor cursor = resolver.query(children,
                    new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null)) {
                if (cursor == null) return null;
                while (cursor.moveToNext()) {
                    if (!name.equals(cursor.getString(1))) continue;
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0));
                }
            }
        } catch (Exception e) {
            AppLog.w("could not look in the vault: " + e.getMessage());
        }
        return null;
    }

    /**
     * The address that opens a note of the vault in the Obsidian. The slashes of the path stay slashes: the
     * Obsidian does not read a file whose path comes escaped.
     */
    static String obsidianUri(String vault, String path) {
        return "obsidian://open?vault=" + Url.encode(vault) + "&file=" + Url.encode(path).replace("%2F", "/");
    }

    /** The name of the vault (the folder's name, what Obsidian calls the vault), or "". */
    static String vaultName(Context c) {
        String tree = Prefs.vault();
        if (tree.isEmpty()) return "";
        try {
            String id = DocumentsContract.getTreeDocumentId(Uri.parse(tree));
            int slash = id.lastIndexOf('/');
            return slash < 0 ? id : id.substring(slash + 1);
        } catch (Exception e) {
            return "";
        }
    }

    /** Where the vault is on the disk ("/storage/emulated/0/Documents/Obsidian/pos-coders"), or "". */
    static String path(Context c) {
        String tree = Prefs.vault();
        if (tree.isEmpty()) return "";
        try {
            String id = DocumentsContract.getTreeDocumentId(Uri.parse(tree));
            int colon = id.indexOf(':');
            if (colon < 0) return "";
            String volume = id.substring(0, colon);
            String rest = id.substring(colon + 1);
            String root = "primary".equals(volume) ? "/storage/emulated/0" : "/storage/" + volume;
            return rest.isEmpty() ? root : root + "/" + rest;
        } catch (Exception e) {
            return "";
        }
    }

    /** Writes a file in the vault (the script Termux runs, the log it leaves). */
    static boolean write(Context c, String name, String content) {
        return write(c, name, content, "application/x-free-my-tablet");
    }

    /**
     * Writes a file in the vault. The mime type is one the system does not know: the folder app would add its
     * own extension to the name ("script.sh" would become "script.sh.txt").
     */
    static boolean write(Context c, String name, String content, String mimeType) {
        String tree = Prefs.vault();
        if (tree.isEmpty()) return false;
        try {
            Uri root = Uri.parse(tree);
            String rootId = DocumentsContract.getTreeDocumentId(root);
            ContentResolver resolver = c.getContentResolver();
            Uri file = find(resolver, root, rootId, name);
            boolean fresh = file == null;
            if (fresh) {
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(root, rootId);
                file = DocumentsContract.createDocument(resolver, parent, mimeType, name);
                if (file == null) return false;
            }
            try (OutputStream out = resolver.openOutputStream(file, "rwt")) {
                if (out == null) return false;
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception e) {
            AppLog.w("could not write " + name + ": " + e.getMessage());
            return false;
        }
    }

    /** Reads a file of the vault (the log of the sync), or null. */
    static String read(Context c, String name) {
        String tree = Prefs.vault();
        if (tree.isEmpty()) return null;
        try {
            Uri root = Uri.parse(tree);
            Uri file = find(c.getContentResolver(), root, DocumentsContract.getTreeDocumentId(root), name);
            if (file == null) {
                AppLog.w("the file is not in the vault: " + name);
                return null;
            }
            try (InputStream in = c.getContentResolver().openInputStream(file)) {
                if (in == null) return null;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            AppLog.w("could not read the file: " + e.getMessage());
            return null;
        }
    }

    /** Removes a file of the vault (the log of the sync, once it was read). */
    static void delete(Context c, String name) {
        String tree = Prefs.vault();
        if (tree.isEmpty()) return;
        try {
            Uri root = Uri.parse(tree);
            Uri file = find(c.getContentResolver(), root, DocumentsContract.getTreeDocumentId(root), name);
            if (file != null) DocumentsContract.deleteDocument(c.getContentResolver(), file);
        } catch (Exception ignored) {
        }
    }

    /** A note of the vault. */
    static final class Note {
        final String title;     // the name without ".md"
        final String path;      // the path inside the vault ("00 - Painel/Home.md")
        final String vault;     // the name of the vault folder
        final long modified;
        /** The text of the note (when it was read: the search looks inside it). */
        String text = "";
        /** The same text in lower case (the search goes through it). */
        String lower = "";
        /** Whether the text of the note was read (the search looks inside it). */
        boolean read;

        Note(String title, String path, String vault, long modified) {
            this.title = title;
            this.path = path;
            this.vault = vault;
            this.modified = modified;
        }
    }

    /** Called with each note found while the app walks through the vault. */
    interface NoteSink {
        void note(Note note);
    }

    /** The notes of the vault, the most recent first (the folders are looked into too). */
    static List<Note> notes(Context c) {
        List<Note> fresh = cached();
        if (fresh != null) return fresh;
        List<Note> out = new ArrayList<>();
        walk(c, new NoteSink() {
            @Override public void note(Note note) {
                out.add(note);
            }
        });
        sortByDate(out);
        cache = out;
        cacheAt = System.currentTimeMillis();
        return new ArrayList<>(out);
    }

    /**
     * The notes, one by one as they are found: the walk through the vault is slow (the system asks about
     * every folder), so the screen shows what it has while the rest is being looked for.
     */
    static void notesStreaming(Context c, final NoteSink sink) {
        new Thread(() -> {
            List<Note> out = new ArrayList<>();
            walk(c, new NoteSink() {
                @Override public void note(Note note) {
                    out.add(note);
                    sink.note(note);
                }
            });
            sortByDate(out);
            cache = out;
            cacheAt = System.currentTimeMillis();
        }, "vault").start();
    }

    /**
     * The notes with their text: it reads every file, so it takes a while. The notes come one by one
     * (the search shows them while they are read).
     */
    static void notesWithText(Context c, final NoteSink sink) {
        // The notes of the last walk (with the text inside): there is nothing to read again.
        List<Note> fresh = cached();
        if (fresh != null && !fresh.isEmpty() && fresh.get(0).read) {
            for (Note note : fresh) sink.note(note);
            return;
        }
        new Thread(() -> {
            String tree = Prefs.vault();
            if (tree.isEmpty()) return;
            try {
                Uri root = Uri.parse(tree);
                String rootId = DocumentsContract.getTreeDocumentId(root);
                int slash = rootId.lastIndexOf('/');
                String vault = slash < 0 ? rootId : rootId.substring(slash + 1);
                ContentResolver resolver = c.getContentResolver();
                final List<Note> out = new ArrayList<>();
                walk(resolver, root, rootId, "", vault, new NoteSink() {
                    @Override public void note(Note note) {
                        out.add(note);
                        sink.note(note);
                    }
                }, 0, new int[1], true);
                sortByDate(out);
                cache = out;
                cacheAt = System.currentTimeMillis();
            } catch (Exception e) {
                AppLog.w("could not read the notes: " + e.getMessage());
            }
        }, "vault-text").start();
    }

    /** The notes of the last walk (the same list, when it is still fresh). */
    static List<Note> cached() {
        // Ten minutes: the walk through the vault is slow (the system asks about every folder) and the
        // widget asks for the notes every time the home screen is opened.
        if (cache == null || System.currentTimeMillis() - cacheAt > 600000) return null;
        return new ArrayList<>(cache);
    }

    /** The most recent note first. */
    private static void sortByDate(List<Note> out) {
        Collections.sort(out, new Comparator<Note>() {
            @Override public int compare(Note a, Note b) {
                return Long.compare(b.modified, a.modified);
            }
        });
    }

    /** Looks through the vault and gives each note to the sink. */
    private static void walk(Context c, NoteSink sink) {
        String tree = Prefs.vault();
        if (tree.isEmpty()) return;
        try {
            Uri folder = Uri.parse(tree);
            String id = DocumentsContract.getTreeDocumentId(folder);
            int slash = id.lastIndexOf('/');
            String vault = slash < 0 ? id : id.substring(slash + 1);
            walk(c.getContentResolver(), folder, DocumentsContract.getTreeDocumentId(folder), "", vault,
                    sink, 0, new int[1]);
        } catch (Exception e) {
            AppLog.w("could not read the notes: " + e.getMessage());
        }
    }

    /** Looks through a folder and the ones inside it (up to three levels, a hundred notes). */
    private static void walk(ContentResolver resolver, Uri tree, String documentId, String prefix, String vault,
            NoteSink sink, int depth, int[] count) {
        walk(resolver, tree, documentId, prefix, vault, sink, depth, count, false);
    }

    /** Looks through a folder and the ones inside it (up to three levels, a hundred notes). */
    private static void walk(ContentResolver resolver, Uri tree, String documentId, String prefix, String vault,
            NoteSink sink, int depth, int[] count, boolean withText) {
        try {
            // The children of a folder: the tree (the one the person gave) and the id of the folder.
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId);
            try (android.database.Cursor cursor = resolver.query(children,
                    new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED}, null, null, null)) {
                if (cursor == null) return;
                while (cursor.moveToNext() && count[0] < 100) {
                    String name = cursor.getString(1);
                    String mime = cursor.getString(2);
                    if (name == null || name.startsWith(".")) continue;
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        if (depth < 3) {
                            walk(resolver, tree, cursor.getString(0), prefix + name + "/", vault, sink, depth + 1,
                                    count);
                        }
                    } else if (name.endsWith(".md")) {
                        count[0]++;
                        Note note = new Note(name.substring(0, name.length() - 3), prefix + name, vault,
                                cursor.getLong(3));
                        if (withText) {
                            // The text of the note: the search looks inside it.
                            try {
                                Uri file = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0));
                                try (InputStream in = resolver.openInputStream(file)) {
                                    if (in != null) {
                                        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                                        byte[] chunk = new byte[8192];
                                        int n;
                                        // Twenty thousand bytes are enough to find the text (and the
                                        // memory of a hundred notes stays small).
                                        while ((n = in.read(chunk)) > 0 && buffer.size() < 20000) {
                                            buffer.write(chunk, 0, n);
                                        }
                                        note.text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                                        note.lower = note.text.toLowerCase(java.util.Locale.getDefault());
                                        note.read = true;
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        sink.note(note);
                    }
                }
            }
        } catch (Exception e) {
            AppLog.w("could not look in a folder of the vault: " + e.getMessage());
        }
    }
}
