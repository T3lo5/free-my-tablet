package io.freemytablet;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;



/**
 * The Free My Tablet home screen (Android's "Home" role in Free My Tablet mode). It runs in the :home process: if it
 * crashes, the safety net in the main process keeps working. On the 3rd crash within 5 min, the Watchdog gives the
 * home screen back to the tablet's original one.
 *
 * The quick note is a real text field, put exactly on the panel the home screen draws: the keyboard, the cursor and
 * the autocorrect are the system's own.
 */
public class HomeActivity extends Activity {
    /** The system's folder picker (for the Obsidian vault). */
    static final int REQ_VAULT = 43;
    /** Termux's permission to run commands (the git pull). */
    static final int REQ_TERMUX = 44;

    private HomeView home;
    private EditText note;
    private EditText search;
    private AppWidgetHost host;          // the widget in the slot
    private AppWidgetHostView widgetView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int lastNotices = -1;
    private boolean running;
    private int prefsVersion = Prefs.version;   // settings changed while we were away: read them again
    private String pendingNote = "";

    /** Puts the text field exactly on the note panel of the home screen. */
    private Root root;

    private final class Root extends FrameLayout {
        Root(Context c) {
            super(c);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            RectF rect = home.noteBounds();
            int left = Math.round(rect.left), top = Math.round(rect.top);
            int right = Math.round(rect.right), bottom = Math.round(rect.bottom);
            // Measured again with the exact size: the field was measured with the whole screen, and a text field
            // keeps its text in the middle of the height it was measured with (it would draw out of sight).
            note.measure(View.MeasureSpec.makeMeasureSpec(right - left, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(bottom - top, View.MeasureSpec.EXACTLY));
            note.layout(left, top, right, bottom);
            if (widgetView != null) {
                RectF area = home.widgetBounds();
                int wl = Math.round(area.left), wt = Math.round(area.top);
                int wr = Math.round(area.right), wb = Math.round(area.bottom);
                // Measured again with the exact size: without it the widget's own views stay with no size.
                widgetView.measure(View.MeasureSpec.makeMeasureSpec(wr - wl, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(wb - wt, View.MeasureSpec.EXACTLY));
                widgetView.layout(wl, wt, wr, wb);
                tellWidgetSize(Prefs.widgetId());
            }
            if (search.getVisibility() == View.VISIBLE) {
                RectF area = home.searchBounds();
                int sl = Math.round(area.left), st = Math.round(area.top);
                int sr = Math.round(area.right), sb = Math.round(area.bottom);
                search.measure(View.MeasureSpec.makeMeasureSpec(sr - sl, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(sb - st, View.MeasureSpec.EXACTLY));
                search.layout(sl, st, sr, sb);
            }
        }
    }

    /** Looks for the notices the switch leaves behind (the results of turning things on and off). */
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!running) return;
            String notice = HomeMode.lastNotice(HomeActivity.this);
            if (!notice.isEmpty()) {
                int id = notice.hashCode();
                if (lastNotices != id) {
                    lastNotices = id;
                    if (home != null) home.notice(notice);
                    // It was shown: the next opening of the home screen does not repeat it.
                    new Thread(() -> HomeMode.markNoticeSeen(HomeActivity.this, notice), "notice").start();
                }
            }
            handler.postDelayed(this, 2000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watchdog.install(this);
        if (Watchdog.crashedTooOften(this)) {
            // Don't try to draw again: only show a notice while the main process restores the original home screen.
            HomeMode.requestRevertHome(this);
            TextView t = new TextView(this);
            t.setText(Ui.tr("O Início do Free My Tablet teve um problema.\nVoltando para o Início original do tablet…",
                    "Free My Tablet's home screen ran into a problem.\nGoing back to the tablet's original home screen…"));
            t.setTextColor(Color.WHITE);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            t.setGravity(Gravity.CENTER);
            t.setBackgroundColor(Color.rgb(7, 11, 30));
            setContentView(t);
            return;
        }
        Ui.init(this);
        home = new HomeView(this);
        note = new EditText(this);
        styleNote();
        search = new EditText(this);
        styleSearch();
        host = new AppWidgetHost(this, Widgets.HOST_ID);
        host.startListening();
        root = new Root(this);
        root.addView(home, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(note, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(search, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        getWindow().setBackgroundDrawable(null);
        home.onOutsideTap = this::hideKeyboard;
        home.onSyncTap = this::sync;
        // Holding the empty home screen opens the settings (like holding the wallpaper of any launcher).
        home.onLongPress = () -> startActivity(new Intent(this, SettingsActivity.class));
        // A gaveta de apps é desenhada pelo canvas: o widget e o campo de nota ficariam por cima dela.
        home.onOverlayChanged = () -> {
            boolean open = home.isOverlayOpen();
            if (widgetView != null) widgetView.setVisibility(open ? View.GONE : View.VISIBLE);
            note.setVisibility(open ? View.GONE : View.VISIBLE);
            if (!open) hideSearch();
        };
        home.onSearchTap = this::showSearch;
        home.onAreaTap = this::chooseArea;
    }

    /** The sync button: the git pull and the copy, done by Termux (that is where git is). */
    private void sync() {
        if (!Vault.ready(this)) {
            home.notice(Ui.tr("Escolha a pasta do Obsidian nas configurações primeiro.",
                    "Choose the Obsidian folder in the settings first."));
            return;
        }
        if (!Termux.installed(this)) {
            home.notice(Ui.tr("O Termux não está neste tablet.", "Termux is not on this tablet."));
            return;
        }
        if (!Termux.allowed(this)) {
            // Termux asks for this permission itself: the system's dialog.
            requestPermissions(new String[] {Termux.PERMISSION}, REQ_TERMUX);
            return;
        }
        startSync();
    }

    private void startSync() {
        if (!Sync.start(this)) {
            home.notice(Ui.tr("Não consegui escrever o script de sincronização na pasta do Obsidian.",
                    "I couldn't write the sync script in the Obsidian folder."));
            return;
        }
        home.notice(Ui.tr("Sincronizando…", "Syncing…"));
        Sync.waitForResult(this, (ok, text) -> runOnUiThread(() -> {
            if (ok) home.notice(Ui.tr("Sincronizado.", "Synced."));
            else home.notice(text.isEmpty()
                    ? Ui.tr("A sincronização não respondeu.", "The sync did not answer.")
                    : text);
        }));
    }

    /** Termux's permission: the person said yes (or no). */
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_TERMUX) return;
        if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startSync();
        } else if (home != null) {
            home.notice(Ui.tr("Sem a permissão do Termux a sincronização não roda.",
                    "Without Termux's permission the sync cannot run."));
        }
    }

    /** The search field of the list of all the apps. */
    private void styleSearch() {
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0x99FFFFFF);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        search.setTypeface(Ui.regular);
        search.setSingleLine(true);
        search.setPadding(dp(42), 0, dp(12), 0);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        search.setVisibility(View.GONE);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (home != null) home.setFilter(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    /** The person tapped a work area: the row of apps changes. */
    private void chooseArea(int index) {
        Prefs.setArea(index);
        if (home != null) home.reload();
    }

    /** The person tapped the search box of the list: the field appears and the keyboard comes up. */
    private void showSearch() {
        search.setVisibility(View.VISIBLE);
        search.setText("");
        search.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
        root.requestLayout();
    }

    /** The search is over: the field goes away and the list shows everything again. */
    private void hideSearch() {
        search.setVisibility(View.GONE);
        search.setText("");
        if (home != null) home.setFilter("");
        hideKeyboard(search);
    }

    /** The quick note field: the text in the color of the theme, one line, the system's keyboard. */
    private void styleNote() {
        note.setBackgroundColor(Color.TRANSPARENT);
        note.setTextColor(Ui.ink());
        note.setHintTextColor(Ui.inkFaint());
        note.setHint(Ui.tr("Escreva uma nota…", "Write a note…"));
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        note.setTypeface(Ui.regular);
        note.setSingleLine(true);
        note.setEllipsize(TextUtils.TruncateAt.END);
        note.setPadding(dp(38), 0, dp(12), 0);
        note.setGravity(Gravity.CENTER_VERTICAL);
        note.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        note.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                saveNote();
                return true;
            }
            return false;
        });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Saves what is in the note field in the Obsidian vault (or asks for the folder, the first time). */
    private void saveNote() {
        String text = note.getText().toString();
        if (text.trim().isEmpty()) return;
        hideKeyboard();
        if (!Vault.ready(this)) {
            pendingNote = text;
            Vault.pick(this);
            return;
        }
        // Writing the file is disk work: it does not belong on the UI thread.
        new Thread(() -> {
            String file = Vault.save(this, text);
            runOnUiThread(() -> {
                if (file == null) {
                    home.notice(Ui.tr("Não consegui salvar a nota. See se a pasta do Obsidian ainda está lá.",
                            "I couldn't save the note. Check that the Obsidian folder is still there."));
                    return;
                }
                note.setText("");
                home.notice(Ui.tr("Nota salva: ", "Note saved: ") + file);
            });
        }, "save-note").start();
    }

    /** The folder was picked: the note that was waiting goes in. */
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_VAULT) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            home.notice(Ui.tr("Sem a pasta do Obsidian a nota não pode ser salva.",
                    "Without the Obsidian folder the note can't be saved."));
            return;
        }
        // The permission to write there is kept only while we need it (the person can take it back in the settings).
        try {
            getContentResolver().takePersistableUriPermission(data.getData(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (RuntimeException e) {
            AppLog.w("could not keep the folder permission: " + e.getMessage());
        }
        Prefs.setVault(data.getData().toString());
        if (!pendingNote.isEmpty()) {
            String text = pendingNote;
            pendingNote = "";
            new Thread(() -> {
                String file = Vault.save(this, text);
                runOnUiThread(() -> {
                    note.setText("");
                    home.notice(file == null
                            ? Ui.tr("Não consegui salvar a nota.", "I couldn't save the note.")
                            : Ui.tr("Nota salva: ", "Note saved: ") + file);
                });
            }, "save-note").start();
        }
    }

    private void hideKeyboard() {
        note.clearFocus();
        hideKeyboard(note);
    }

    private void hideKeyboard(View field) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
    }

    @Override protected void onResume() {
        super.onResume();
        Weather.refresh(this);
        showWidget();
        checkForUpdates();
        if (home != null) {
            if (prefsVersion != Prefs.version) {   // the favorites or the theme changed in the settings
                prefsVersion = Prefs.version;
                home.reload();
                home.layoutWidget();   // the size of the widget may have changed
                styleNote();     // the note field takes the color of the theme
                applyMode();
                root.requestLayout();
            }
            home.resume();
        }
        running = true;
        handler.post(poll);
    }

    /**
     * Looks for a new version, once in a while (the GitHub API is not called on every open). When there is one,
     * the home screen says so: the person updates from the settings, without downloading anything by hand.
     */
    private void checkForUpdates() {
        long now = System.currentTimeMillis();
        if (now - Prefs.updateCheck() < 6 * 60 * 60_000L) return;
        Prefs.setUpdateCheck(now);
        final Context app = getApplicationContext();
        new Thread(() -> {
            Update.Info info = Update.check(app);
            if (info == null) return;
            runOnUiThread(() -> {
                if (home != null) {
                    home.notice(Ui.tr("Tem versão nova (", "There is a new version (") + info.version
                            + Ui.tr("). Abra as configurações para atualizar.",
                                    "). Open the settings to update."));
                }
            });
        }, "update-check").start();
    }

    /** Puts the widget of the slot on the home screen (the person chose it in the settings). */
    /**
     * Tells the system how big the slot is. A widget that does not know its own size (the options come
     * with zeroes) stays loading forever: this is what the launcher of the tablet does.
     */
    private void tellWidgetSize(int id) {
        if (host == null || home == null) return;
        RectF area = home.widgetBounds();
        if (area.width() <= 0 || area.height() <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        int width = Math.round(area.width() / density);
        int height = Math.round(area.height() / density);
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height);
        try {
            AppWidgetManager.getInstance(this).updateAppWidgetOptions(id, options);
        } catch (RuntimeException e) {
            AppLog.w("could not tell the widget its size: " + e.getMessage());
        }
    }

    /**
     * The widget of the slot: the one the person picked. The others (the ones left from before an
     * update of the app) go away: the slot holds one.
     */
    private int currentWidget() {
        int[] ids = host.getAppWidgetIds();
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        int current = Prefs.widgetId();
        boolean known = false;
        for (int id : ids) {
            if (id == current && manager.getAppWidgetInfo(id) != null) known = true;
        }
        if (!known) {
            // The app was updated (the choice was forgotten): the newest widget that is really there wins.
            current = 0;
            for (int id : ids) {
                if (manager.getAppWidgetInfo(id) != null && id > current) current = id;
            }
            if (current != 0) Prefs.setWidgetId(current);
        }
        for (int id : ids) {
            if (id != current) host.deleteAppWidgetId(id);   // the slot holds one
        }
        return current;
    }

    /** The reading mode: the note field and the widget step aside (only the clock and the apps). */
    private void applyMode() {
        boolean read = home != null && home.reading();
        boolean open = home != null && home.isOverlayOpen();
        if (note != null) note.setVisibility(read || open ? View.GONE : View.VISIBLE);
        if (widgetView != null) widgetView.setVisibility(read || open ? View.GONE : View.VISIBLE);
    }

    private void showWidget() {
        int id = currentWidget();
        if (id == 0) {
            if (widgetView != null) widgetView.setVisibility(View.GONE);
            return;
        }
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        if (info == null) {
            Prefs.setWidgetId(0);
            return;
        }
        // The size the widget asks for (the slot never squeezes it).
        try {
            Bundle options = AppWidgetManager.getInstance(this).getAppWidgetOptions(id);
            int wanted = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            if (wanted > 0 && home != null) home.setWidgetHeight(wanted);
        } catch (RuntimeException ignored) {
        }
        if (widgetView == null) {
            widgetView = host.createView(this, id, info);
            widgetView.setAppWidget(id, info);
            root.addView(widgetView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            widgetView.setAppWidget(id, info);
        }
        widgetView.setVisibility(View.VISIBLE);
        applyMode();
        root.requestLayout();
        tellWidgetSize(id);
        NotesWidget.refresh(this);   // the notes may have changed since the last time
    }

    @Override protected void onPause() {
        if (host != null) host.stopListening();
        running = false;
        handler.removeCallbacks(poll);
        if (home != null) home.pause();
        super.onPause();
    }

    /** Home button pressed on the home screen: back to the entry state (the note field empty). */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (home != null) home.reload();
    }

    @Override protected void onDestroy() {
        if (home != null) home.destroy();
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (search != null && search.hasFocus()) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                hideSearch();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        if (note != null && note.hasFocus()) return super.dispatchKeyEvent(event);
        if (home != null && home.onKeyDown(event.getKeyCode(), event)) return true;
        return super.dispatchKeyEvent(event);
    }
}
