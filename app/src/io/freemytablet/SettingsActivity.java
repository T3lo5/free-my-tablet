package io.freemytablet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The settings (the app's own icon opens this): the switch between Free My Tablet and the tablet's original home
 * screen, the dock, the apps to turn off, the language and the theme.
 *
 * Drawn by hand on one Canvas, like the home screen: rows you tap, a checklist for the apps, and a line at the
 * bottom that says what the switch is doing.
 */
public class SettingsActivity extends Activity {
    /** The system's own dialog to choose the home screen. */
    static final int REQ_ROLE = 42;
    /** The widget that asks for its own settings. */
    static final int REQ_WIDGET = 45;
    private SettingsView view;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        view = new SettingsView(this);
        setContentView(view);
        getWindow().setBackgroundDrawable(null);
    }

    /** The user picked the home screen in the system's dialog, or the Obsidian folder. */
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_WIDGET) {
            // The person picked a widget in the system's picker (or cancelled). The system gives the id in the
            // data of the answer ("appwidget://13"), not in an extra.
            int id = 0;
            if (data != null) {
                id = data.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
                if (id == 0 && data.getData() != null && data.getData().getScheme() != null
                        && data.getData().getScheme().equals("appwidget")) {
                    try {
                        id = Integer.parseInt(data.getData().getLastPathSegment());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            AppLog.w("the picker answered: result=" + resultCode + " data=" + (data == null ? "none" : data.getData())
                    + " extra=" + (data == null ? "none" : data.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 0)));
            if (id != 0) Widgets.picked(this, REQ_WIDGET, id);
            view.reload();
            return;
        }
        if (requestCode == REQ_ROLE) {
            if (resultCode != RESULT_OK) {
                view.notice(Ui.tr("Você escolheu não trocar o Início.", "You chose not to change the home screen."));
                return;
            }
            if (HomeMode.roleHeld(this)) {
                // Ours: the switch goes on (and turns the apps off, when the ADB is open).
                HomeMode.request(this, HomeMode.FREE);
            } else {
                // The tablet's own launcher: the way back is done.
                HomeMode.markOriginal(this);
                view.notice(Ui.tr("Modo original: o Início do tablet voltou.",
                        "Original mode: the tablet's own home screen is back."));
            }
            return;
        }
        if (requestCode != HomeActivity.REQ_VAULT) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            getContentResolver().takePersistableUriPermission(data.getData(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (RuntimeException e) {
            AppLog.w("could not keep the folder permission: " + e.getMessage());
        }
        Prefs.setVault(data.getData().toString());
        view.reload();
        view.notice(Ui.tr("Pasta do Obsidian escolhida. As notas rápidas vão para lá.",
                "Obsidian folder chosen. The quick notes go there."));
    }

    @Override protected void onResume() {
        super.onResume();
        view.resume();
    }

    @Override protected void onPause() {
        view.pause();
        super.onPause();
    }

    /** Back on a checklist: go back to the list. */
    @Override public void onBackPressed() {
        if (view.screen != 0) {
            view.screen = 0;
            view.invalidate();
            return;
        }
        super.onBackPressed();
    }

    // MARK: - The screen

    static final class SettingsView extends View implements Choreographer.FrameCallback {
        private final Activity activity;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Paint rowTitle = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Paint rowValue = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Paint status = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint check = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tmp = new RectF();

        private int w, h;
        private float density, rowH, top, bottom;

        /** What the screen shows: the main list, the apps to turn off, or the dock. */
        private int screen = MAIN, pickFor;
        private JSONObject state = new JSONObject();   // the switch state (read once, not every frame)
        private float scroll;
        private List<String> rows = new ArrayList<>();     // the labels of the main screen
        private List<HomeApps.Item> apps = new ArrayList<>();  // the checklist
        private List<String> picked = new ArrayList<>();   // the packages checked in the checklist
        private boolean running;
        private long lastFrame;
        private String statusText = "";

        static final int MAIN = 0, FAVORITES = 1, OFF_APPS = 2, SPACE = 3, TALKERS = 4, UPDATES = 5, PANEL = 6;

        /** The lines of the main screen, in the order they are shown. */
        static final int HOME = 0, ON_SCREEN = 1, VAULT = 2, NOTE_FOLDER = 3, TODAY_NOTE = 4, VAULT_SEARCH = 5,
                WEATHER = 6, WIDGET = 7, WIDGET_SIZE = 8, SYNC = 9, SPACE_ROW = 10, PANEL_ROW = 11,
                TALKERS_ROW = 12, UPDATES_ROW = 13, EXPORT = 14, OFF = 15, LANGUAGE = 16, THEME = 17, ABOUT = 18;
        static final int[] ROWS = {HOME, ON_SCREEN, VAULT, NOTE_FOLDER, TODAY_NOTE, VAULT_SEARCH, WEATHER, WIDGET,
                WIDGET_SIZE, SYNC, SPACE_ROW, PANEL_ROW, TALKERS_ROW, UPDATES_ROW, EXPORT, OFF, LANGUAGE, THEME,
                ABOUT};

        SettingsView(Activity a) {
            super(a);
            this.activity = a;
            title.setTypeface(Ui.displaySemibold);
            rowTitle.setTypeface(Ui.medium);
            rowValue.setTypeface(Ui.regular);
            status.setTypeface(Ui.regular);
            setFocusable(true);
        }

        void resume() {
            rows = mainRows();
            state = HomeMode.state(activity);
            HomeMode.checkNow(activity);   // the state can be out of date: the home screen says the truth
            running = true;
            lastFrame = 0;
            Choreographer.getInstance().postFrameCallback(this);
            handler.post(poll);
        }

        /** Reads the rows again (the vault was chosen, the mode changed). */
        void reload() {
            rows = mainRows();
            invalidate();
        }

        void pause() {
            running = false;
            Choreographer.getInstance().removeFrameCallback(this);
            handler.removeCallbacks(poll);
        }

        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            if (lastFrame == 0) lastFrame = frameTimeNanos;
            lastFrame = frameTimeNanos;
            invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }

        /** Reads what the switch is doing (the state file the main process writes). */
        private final Runnable poll = new Runnable() {
            @Override public void run() {
                if (!running) return;
                state = HomeMode.state(activity);
                String busy = state.optString("busy", "");
                String error = state.optString("error", "");
                statusText = !busy.isEmpty() ? busy : error;
                handler.postDelayed(this, 700);
            }
        };

        // MARK: - The rows

        private List<String> mainRows() {
            List<String> out = new ArrayList<>();
            for (int row : ROWS) out.add(title(row));
            return out;
        }

        /** The title of a line. */
        private String title(int row) {
            switch (row) {
                case HOME: return Ui.tr("Tela de Início", "Home screen");
                case ON_SCREEN: return Ui.tr("Apps na tela", "Apps on the home screen");
                case VAULT: return Ui.tr("Pasta do Obsidian", "Obsidian folder");
                case NOTE_FOLDER: return Ui.tr("Pasta da nota", "Note folder");
                case TODAY_NOTE: return Ui.tr("Abrir a nota de hoje", "Open today's note");
                case VAULT_SEARCH: return Ui.tr("Buscar no vault", "Search the vault");
                case WEATHER: return Ui.tr("Card do tempo", "Weather card");
                case WIDGET: return Ui.tr("Widget", "Widget");
                case WIDGET_SIZE: return Ui.tr("Tamanho do widget", "Widget size");
                case SYNC: return Ui.tr("Sincronizar", "Sync");
                case SPACE_ROW: return Ui.tr("Espaço", "Space");
                case PANEL_ROW: return Ui.tr("Tablet", "Tablet");
                case TALKERS_ROW: return Ui.tr("Quem fala com você", "Who talks to you");
                case UPDATES_ROW: return Ui.tr("Atualizações", "Updates");
                case EXPORT: return Ui.tr("Exportar as escolhas", "Export the choices");
                case OFF: return Ui.tr("Apps para desligar", "Apps to turn off");
                case LANGUAGE: return Ui.tr("Idioma", "Language");
                case THEME: return Ui.tr("Tema", "Theme");
                default: return Ui.tr("Sobre", "About");
            }
        }

        /** The text on the right of a row: what it is now. */
        private String value(int row) {
            switch (row) {
                case HOME: return HomeMode.homeNow(activity)
                        ? Ui.tr("Free My Tablet", "Free My Tablet")
                        : Ui.tr("Original", "Original");
                case ON_SCREEN: return Ui.tr("Escolher", "Choose");
                case VAULT: return Vault.ready(activity) ? Ui.tr("Escolhida", "Chosen") : Ui.tr("Escolher", "Choose");
                case NOTE_FOLDER: return Prefs.noteFolder().isEmpty() ? Ui.tr("A raiz do vault", "The vault root")
                        : Prefs.noteFolder();
                case TODAY_NOTE: return Ui.tr("No Obsidian", "In Obsidian");
                case VAULT_SEARCH: return Ui.tr("Abrir", "Open");
                case WEATHER: return Prefs.weatherCard() ? Ui.tr("Ligado", "On") : Ui.tr("Desligado", "Off");
                case WIDGET: return Prefs.widgetId() == 0 ? Ui.tr("Escolher", "Choose") : Ui.tr("Colocado", "Placed");
                case WIDGET_SIZE: return Prefs.widgetId() == 0 ? Ui.tr("—", "—")
                        : (Prefs.widgetSize() <= 100 ? Ui.tr("Pequeno", "Small")
                        : Prefs.widgetSize() >= 200 ? Ui.tr("Grande", "Big") : Ui.tr("Médio", "Medium"));
                case SYNC: return Ui.tr("Ajustar", "Set up");
                case SPACE_ROW: return Ui.tr("O que ocupa o tablet", "What is using the tablet");
                case PANEL_ROW: return Ui.tr("Memória, CPU e bateria", "Memory, CPU and battery");
                case EXPORT: return Ui.tr("Salvar ou restaurar", "Save or restore");
                case TALKERS_ROW: return Ui.tr("Notificações", "Notifications");
                case UPDATES_ROW: return updateState;
                case OFF: return Ui.tr("Apps que você não usa", "Apps you don't use");
                case LANGUAGE: {
                    String l = Prefs.language();
                    return "auto".equals(l) ? Ui.tr("Automático", "Automatic")
                            : "pt".equals(l) ? "Português" : "es".equals(l) ? "Español" : "English";
                }
                case THEME: {
                    String t = Prefs.theme();
                    return "light".equals(t) ? Ui.tr("Claro", "Light") : "dark".equals(t) ? Ui.tr("Escuro", "Dark")
                            : "read".equals(t) ? Ui.tr("Leitura", "Reading") : Ui.tr("Automático", "Automatic");
                }
                default: return "";
            }
        }

        // MARK: - Drawing

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            w = width;
            h = height;
            density = getResources().getDisplayMetrics().density;
            rowH = dp(64);
            top = dp(72);
            bottom = h - dp(56);
            title.setTextSize(dp(24));
            rowTitle.setTextSize(dp(16));
            rowValue.setTextSize(dp(14));
            status.setTextSize(dp(13));
        }

        private float dp(float v) {
            return v * density;
        }

        @Override protected void onDraw(Canvas c) {
            c.drawColor(0xFF0B0F1E);
            if (screen == MAIN) {
                drawMain(c);
            } else if (screen == SPACE) {
                drawSpace(c);
            } else if (screen == TALKERS) {
                drawTalkers(c);
            } else if (screen == UPDATES) {
                drawUpdates(c);
            } else if (screen == PANEL) {
                drawPanel(c);
            } else {
                drawChecklist(c);
            }
            drawStatus(c);
        }

        private void drawMain(Canvas c) {
            title.setColor(0xFFFFFFFF);
            c.drawText(screen == MAIN ? Ui.tr("Free My Tablet", "Free My Tablet") : title(), dp(20), dp(44), title);
            for (int i = 0; i < rows.size(); i++) {
                float y = top + i * rowH - scroll;
                if (y < top - rowH || y > bottom) continue;
                float line = y + rowH - dp(8);
                panel.setColor(0x22FFFFFF);
                tmp.set(dp(20), line - dp(1), w - dp(20), line + dp(1));
                c.drawRect(tmp, panel);
                rowTitle.setColor(0xFFFFFFFF);
                c.drawText(rows.get(i), dp(20), y + dp(30), rowTitle);
                rowValue.setColor(0x99FFFFFF);
                String v = value(ROWS[i]);
                float vw = rowValue.measureText(v);
                c.drawText(v, w - dp(20) - vw, y + dp(30), rowValue);
            }
        }

        private String title() {
            return screen == OFF_APPS ? Ui.tr("Apps para desligar", "Apps to turn off")
                    : Ui.tr("Apps na tela", "Apps on the home screen");
        }

        /** The updates. */
        private void drawUpdates(Canvas c) {
            title.setColor(0xFFFFFFFF);
            c.drawText(Ui.tr("Atualizações", "Updates"), dp(20), dp(44), title);
            float y = top - scroll;
            for (String line : updateLines) {
                rowTitle.setColor(0xFFFFFFFF);
                c.drawText(line, dp(20), y + dp(30), rowTitle);
                y += rowH;
            }
        }

        /** The panel. */
        private void drawPanel(Canvas c) {
            title.setColor(0xFFFFFFFF);
            c.drawText(Ui.tr("Tablet", "Tablet"), dp(20), dp(44), title);
            float y = top - scroll;
            for (String line : panelLines) {
                rowTitle.setColor(0xFFFFFFFF);
                c.drawText(line, dp(20), y + dp(30), rowTitle);
                y += rowH;
            }
        }

        /** Who talks the most. */
        private void drawTalkers(Canvas c) {
            title.setColor(0xFFFFFFFF);
            c.drawText(Ui.tr("Quem fala com você", "Who talks to you"), dp(20), dp(44), title);
            float y = top - scroll;
            for (String line : talkerLines) {
                rowTitle.setColor(0xFFFFFFFF);
                c.drawText(line, dp(20), y + dp(30), rowTitle);
                y += rowH;
            }
        }

        /** The space: what is using the tablet. */
        private void drawSpace(Canvas c) {
            title.setColor(0xFFFFFFFF);
            c.drawText(Ui.tr("Espaço", "Space"), dp(20), dp(44), title);
            float y = top - scroll;
            for (String line : spaceLines) {
                // Only the lines that are on the screen are drawn (the list can be long).
                if (y > bottom + rowH || y + rowH < top) { y += rowH; continue; }
                rowTitle.setColor(0xFFFFFFFF);
                c.drawText(line, dp(20), y + dp(30), rowTitle);
                y += rowH;
            }
        }

        /** The checklist: the apps, with a box on the ones that are picked. */
        private void drawChecklist(Canvas c) {
            title.setColor(0xFFFFFFFF);
            c.drawText(title(), dp(20), dp(44), title);
            float y = top - scroll;
            for (HomeApps.Item item : apps) {
                if (y > bottom + rowH || y + rowH < top) { y += rowH; continue; }
                boolean on = picked.contains(item.pkg);
                rowTitle.setColor(0xFFFFFFFF);
                c.drawText(item.label, dp(20) + dp(34), y + dp(30), rowTitle);
                check.setStyle(Paint.Style.STROKE);
                check.setStrokeWidth(dp(1.6f));
                check.setColor(on ? 0xFF7FD1FF : 0x66FFFFFF);
                tmp.set(dp(20), y + dp(10), dp(20) + dp(20), y + dp(30));
                c.drawRoundRect(tmp, dp(4), dp(4), check);
                if (on) {
                    check.setStyle(Paint.Style.FILL);
                    check.setColor(0xFF7FD1FF);
                    c.drawRoundRect(tmp, dp(4), dp(4), check);
                }
                y += rowH;
            }
        }

        private void drawStatus(Canvas c) {
            if (statusText.isEmpty()) return;
            status.setColor(0xCCFFFFFF);
            c.drawText(statusText, dp(20), h - dp(24), status);
        }

        // MARK: - Touch

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = lastY = e.getY();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float moved = e.getY() - lastY;
                    lastY = e.getY();
                    if (Math.abs(moved) < 0.5f) return true;   // the finger did not move: it is a tap
                    scroll = Math.max(0, Math.min(maxScroll(), scroll - moved));
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    if (Math.abs(e.getY() - downY) > dp(12)) return true;   // it was a scroll
                    if (screen == MAIN) tapMain(e.getY());
                    else if (screen == SPACE) tapSpace((int) ((e.getY() + scroll - top + rowH / 2) / rowH));
                    else if (screen == TALKERS) tapTalker((int) ((e.getY() + scroll - top + rowH / 2) / rowH));
                    else if (screen == UPDATES) tapUpdate((int) ((e.getY() + scroll - top + rowH / 2) / rowH));
                    else tapChecklist(e.getY());
                    return true;
                }
                default:
                    return super.onTouchEvent(e);
            }
        }

        /** How far the list can go: nothing to scroll when everything fits. */
        private float maxScroll() {
            int items;
            if (screen == MAIN) items = rows.size();
            else if (screen == SPACE) items = spaceLines.size();
            else if (screen == PANEL) items = panelLines.size();
            else if (screen == TALKERS) items = talkerLines.size();
            else if (screen == UPDATES) items = updateLines.size();
            else items = apps.size();
            return Math.max(0, items * rowH - (bottom - top));
        }

        private float downY, lastY;

        private void tapMain(float y) {
            int index = (int) ((y + scroll - top + rowH / 2) / rowH);
            if (index < 0 || index >= rows.size()) return;
            switch (ROWS[index]) {
                case HOME: askSwitch(); break;
                case ON_SCREEN: openChecklist(FAVORITES); break;
                case VAULT: Vault.pick(activity); break;
                case NOTE_FOLDER: askNoteFolder(); break;
                case TODAY_NOTE: openTodayNote(); break;
                case VAULT_SEARCH: VaultSearch.show(activity); break;
                case EXPORT: askExport(); break;
                case WEATHER:
                    Prefs.setWeatherCard(!Prefs.weatherCard());
                    reload();
                    break;
                case WIDGET: askWidget(); break;
                case WIDGET_SIZE: askWidgetSize(); break;
                case SYNC: askSync(); break;
                case SPACE_ROW: openSpace(); break;
                case PANEL_ROW: openPanel(); break;
                case TALKERS_ROW: openTalkers(); break;
                case UPDATES_ROW: openUpdates(); break;
                case OFF: openChecklist(OFF_APPS); break;
                case LANGUAGE: askLanguage(); break;
                case THEME: askTheme(); break;
                default: askAbout(); break;
            }
        }

        /** The updates: what the latest release is, and the button to install it. */
        private void openUpdates() {
            screen = UPDATES;
            scroll = 0;
            updateLines = new ArrayList<>();
            updateLines.add(Ui.tr("Procurando…", "Looking…"));
            updateInfo = null;
            invalidate();
            final Context app = activity.getApplicationContext();
            new Thread(() -> {
                Update.Info info = Update.check(app);
                List<String> lines = new ArrayList<>();
                if (info == null) {
                    lines.add(Ui.tr("Você está na última versão (", "You have the latest version (")
                            + Update.installed(app) + ")");
                } else {
                    lines.add(Ui.tr("Nova versão: ", "New version: ") + info.version);
                    lines.add(Ui.tr("Tamanho: ", "Size: ") + size(info.size));
                    lines.add("");
                    for (String line : info.notes.split("\n")) {
                        String text = line.trim();
                        if (text.isEmpty()) continue;
                        // The notes come from the markdown of the changelog: the marks are not needed here.
                        text = text.replace("**", "").replace("`", "");
                        if (text.startsWith("- ")) text = text.substring(2);
                        lines.add(text);
                    }
                    lines.add("");
                    lines.add(Ui.tr("Toque em “Atualizar” para baixar e instalar.",
                            "Tap “Update” to download and install."));
                    lines.add(Ui.tr("Se a instalação ficar parada: o Google Play Protect está conferindo o app. "
                                    + "Na Play Store, Configurações → Segurança → Verificação de apps, "
                                    + "escolha “Não verificar”.",
                            "If the installation hangs, the Google Play Protect is checking the app. In the Play Store, "
                                    + "Settings → Security → App verification, choose “Don’t verify”."));
                }
                activity.runOnUiThread(() -> {
                    updateInfo = info;
                    updateLines = lines;
                    updateState = info == null ? Ui.tr("Atualizado", "Up to date")
                            : Ui.tr("Nova versão", "New version");
                    reload();
                    invalidate();
                });
            }, "update").start();
        }

        private List<String> updateLines = new ArrayList<>();
        private Update.Info updateInfo;
        private String updateState = "";

        /** The button of the update screen: download the APK and open the installer. */
        private void tapUpdate(int index) {
            if (updateInfo == null) return;
            if (index != updateLines.size() - 1) return;    // the last line is the button
            final Update.Info info = updateInfo;
            final Context app = activity.getApplicationContext();
            updateLines = new ArrayList<>();
            updateLines.add(Ui.tr("Baixando…", "Downloading…"));
            invalidate();
            new Thread(() -> {
                String message;
                java.io.File file = null;
                try {
                    file = Update.download(app, info.url);
                    Update.install(app, file);
                    message = Ui.tr("Abrindo o instalador…", "Opening the installer…");
                } catch (Exception e) {
                    message = Ui.tr("Não consegui baixar: ", "I couldn't download: ") + e.getMessage();
                }
                final String text = message;
                activity.runOnUiThread(() -> {
                    notice(text);
                    openUpdates();
                });
            }, "download").start();
        }

        /** The panel: the memory, the processor, the battery and the processes. */
        private void openPanel() {
            screen = PANEL;
            scroll = 0;
            panelLines = new ArrayList<>();
            panelLines.add(Ui.tr("Lendo…", "Reading…"));
            invalidate();
            final Context app = activity.getApplicationContext();
            new Thread(() -> {
                List<String> lines = new ArrayList<>();
                try (Adb adb = Adb.connect(activity, 30_000, null)) {
                    Panel.Data data = Panel.read(adb);
                    lines.add(Ui.tr("Memória", "Memory"));
                    lines.add(Ui.tr("  Total: ", "  Total: ") + data.size(data.ramTotal));
                    lines.add(Ui.tr("  Em uso: ", "  In use: ") + data.size(data.ramUsed));
                    lines.add(Ui.tr("  Livre: ", "  Free: ") + data.size(data.ramFree));
                    lines.add(Ui.tr("  Swap: ", "  Swap: ") + data.size(data.swapUsed));
                    lines.add("");
                    lines.add(Ui.tr("Bateria", "Battery"));
                    lines.add("  " + data.battery + "%   " + data.temperature + "°C");
                    lines.add("");
                    lines.add(Ui.tr("Processos vivos: ", "Processes alive: ") + data.processes);
                    lines.add("");
                    lines.add(Ui.tr("Os que mais usam a CPU", "The ones using the CPU"));
                    for (Panel.Entry entry : data.cpu) {
                        if (lines.size() > 40) break;
                        lines.add("  " + entry.name + "   " + entry.percent + "%");
                    }
                    lines.add("");
                    lines.add(Ui.tr("Os que mais usam a memória", "The ones using the memory"));
                    for (Panel.Entry entry : data.memory) {
                        if (lines.size() > 55) break;
                        lines.add("  " + entry.name + "   " + data.size(entry.size));
                    }
                } catch (Exception e) {
                    lines.add(Ui.tr("Não consegui ler: o ADB do tablet não está aberto.",
                            "I couldn't read: the tablet's ADB is not open."));
                }
                activity.runOnUiThread(() -> {
                    panelLines = lines;
                    invalidate();
                });
            }, "panel").start();
        }

        private List<String> panelLines = new ArrayList<>();

        /** Who talks the most: the notifications the tablet received, with a way to turn each one off. */
        private void openTalkers() {
            screen = TALKERS;
            scroll = 0;
            talkerLines = new ArrayList<>();
            talkerLines.add(Ui.tr("Lendo…", "Reading…"));
            invalidate();
            new Thread(() -> {
                List<String> lines = new ArrayList<>();
                List<Notify.Talker> talkers = new ArrayList<>();
                try (Adb adb = Adb.connect(activity, 30_000, null)) {
                    talkers = Notify.top(adb, 8);
                    for (Notify.Talker talker : talkers) {
                        lines.add(talker.count + "   " + talker.pkg);
                    }
                    if (lines.isEmpty()) lines.add(Ui.tr("Nenhuma notificação.", "No notifications."));
                    lines.add("");
                    lines.add(Ui.tr("Toque num app para abrir a tela dele e desligar a notificação.",
                            "Tap an app to open its screen and turn the notification off."));
                } catch (Exception e) {
                    lines.add(Ui.tr("Não consegui ler: o ADB do tablet não está aberto.",
                            "I couldn't read: the tablet's ADB is not open."));
                }
                final List<Notify.Talker> finalTalkers = talkers;
                activity.runOnUiThread(() -> {
                    talkerLines = lines;
                    talkerPkgs = new ArrayList<>();
                    for (Notify.Talker talker : finalTalkers) talkerPkgs.add(talker.pkg);
                    invalidate();
                });
            }, "notify").start();
        }

        private List<String> talkerLines = new ArrayList<>();
        private List<String> talkerPkgs = new ArrayList<>();

        /** A tap on an app that talks: the system's screen to turn its notification off. */
        private void tapTalker(int index) {
            if (index < 0 || index >= talkerPkgs.size()) return;
            String pkg = talkerPkgs.get(index);
            try {
                activity.startActivity(Notify.settings(pkg));
            } catch (RuntimeException e) {
                AppLog.w("could not open the notification settings: " + e.getMessage());
            }
        }

        /** The space: what is using the tablet, and the buttons to get some of it back. */
        private void openSpace() {
            screen = SPACE;
            scroll = 0;
            spaceLines = new ArrayList<>();
            spaceLines.add(Ui.tr("Lendo…", "Reading…"));
            invalidate();
            new Thread(() -> {
                List<String> lines = new ArrayList<>();
                try (Adb adb = Adb.connect(activity, 30_000, null)) {
                    long[] free = Space.free(adb);
                    lines.add(Ui.tr("Livre: ", "Free: ") + size(free[0]) + " de " + size(free[1]));
                    lines.add("");
                    List<Space.App> biggest = Space.biggest(adb, 0);   // all of them, the biggest first
                    lines.add(Ui.tr("O que ocupa o tablet (", "What is using the tablet (") + biggest.size()
                            + Ui.tr(" apps):", " apps):"));
                    for (Space.App app : biggest) {
                        lines.add(size(app.total()) + "   " + app.pkg);
                    }
                    lines.add("");
                    lines.add(Ui.tr("Limpar o cache de todos os apps (não apaga nada seu)",
                            "Clear the cache of every app (nothing of yours is lost)"));
                    lines.add(Ui.tr("Limpar os apps que estão desligados (o que sobrou deles)",
                            "Clear the apps that are turned off (what is left of them)"));
                } catch (Exception e) {
                    lines.add(Ui.tr("Não consegui ler o espaço: o ADB do tablet não está aberto.",
                            "I couldn't read the space: the tablet's ADB is not open."));
                }
                activity.runOnUiThread(() -> {
                    spaceLines = lines;
                    invalidate();
                });
            }, "space").start();
        }

        private List<String> spaceLines = new ArrayList<>();

        /** A size in a language people read ("1,2 GB"). */
        private String size(long bytes) {
            if (bytes >= 1024L * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024 * 1024));
            if (bytes >= 1024L * 1024) return String.format(Locale.getDefault(), "%.0f MB", bytes / (1024.0 * 1024));
            return String.format(Locale.getDefault(), "%.0f kB", bytes / 1024.0);
        }

        /** The two buttons of the space screen. */
        private void tapSpace(int index) {
            int total = spaceLines.size();
            int first = total - 2;
            if (index != first && index != first + 1) return;
            new Thread(() -> {
                String message;
                try (Adb adb = Adb.connect(activity, 30_000, null)) {
                    if (index == first) {
                        Space.trimCaches(adb);
                        message = Ui.tr("Cache limpo.", "Cache cleared.");
                    } else {
                        int done = Space.clearTurnedOff(adb, Space.turnedOff(adb));
                        message = Ui.tr(done + " apps limpos.", done + " apps cleared.");
                    }
                } catch (Exception e) {
                    message = Ui.tr("Não consegui: o ADB do tablet não está aberto.",
                            "I couldn't: the tablet's ADB is not open.");
                }
                final String text = message;
                activity.runOnUiThread(() -> {
                    openSpace();
                    notice(text);
                });
            }, "space-clean").start();
        }

        /** Opens the Obsidian with the note of the day (the file the quick note writes). */
        private void openTodayNote() {
            String vault = Vault.vaultName(activity);
            if (vault.isEmpty()) {
                notice(Ui.tr("Escolha a pasta do Obsidian primeiro.", "Choose the Obsidian folder first."));
                return;
            }
            String file = Ui.tr("Nota rápida ", "Quick note ")
                    + new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date())
                    + ".md";
            String folder = Prefs.noteFolder();
            String path = folder.isEmpty() ? file : folder + "/" + file;
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Vault.obsidianUri(vault, path)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                activity.startActivity(intent);
            } catch (RuntimeException e) {
                notice(Ui.tr("Não consegui abrir o Obsidian.", "I couldn't open Obsidian."));
            }
        }

        /** The folder inside the vault where the quick note goes. */
        private void askNoteFolder() {
            final android.widget.EditText field = new android.widget.EditText(activity);
            field.setText(Prefs.noteFolder());
            field.setHint(Ui.tr("Ex.: 01 - Diário (vazio = a raiz)", "e.g. 01 - Diary (empty = the root)"));
            field.setSingleLine();
            field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
            int pad = Math.round(dp(20));
            field.setPadding(pad, Math.round(dp(12)), pad, 0);
            final android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
            field.setOnEditorActionListener((v, actionId, event) -> {
                // The "done" key of the keyboard saves, like the button does.
                Prefs.setNoteFolder(field.getText().toString());
                if (dialog[0] != null) dialog[0].dismiss();
                reload();
                return true;
            });
            dialog[0] = new AlertDialog.Builder(activity)
                    .setTitle(Ui.tr("Pasta da nota", "Note folder"))
                    .setView(field)
                    .setNegativeButton(Ui.tr("Cancelar", "Cancel"), null)
                    .setPositiveButton("OK", (d, w) -> {
                        Prefs.setNoteFolder(field.getText().toString());
                        reload();
                    })
                    .show();
        }

        /** The choices: save them in the vault, or read them back from there. */
        private void askExport() {
            new AlertDialog.Builder(activity)
                    .setTitle(Ui.tr("Exportar as escolhas", "Export the choices"))
                    .setItems(new String[] {
                            Ui.tr("Salvar no vault", "Save in the vault"),
                            Ui.tr("Ler do vault (restaurar)", "Read from the vault (restore)")},
                            (d, which) -> {
                                if (which == 0) exportChoices();
                                else restoreChoices();
                            })
                    .setNegativeButton(Ui.tr("Cancelar", "Cancel"), null)
                    .show();
        }

        /** Reads the choices back from the vault. */
        private void restoreChoices() {
            new Thread(() -> {
                boolean ok = Backup.restore(activity);
                final String text = ok
                        ? Ui.tr("Escolhas restauradas.", "The choices were restored.")
                        : Ui.tr("Não achei o arquivo de escolhas no vault.", "No choices file in the vault.");
                activity.runOnUiThread(() -> {
                    notice(text);
                    reload();
                });
            }, "restore").start();
        }

        /** Writes the choices in the vault, so they can be copied to another tablet. */
        private void exportChoices() {
            new Thread(() -> {
                String name = Backup.save(activity);
                final String text = name == null
                        ? Ui.tr("Escolha a pasta do Obsidian primeiro (e uma pasta que aceite arquivo novo).",
                        "Choose the Obsidian folder first (and one that accepts new files).")
                        : Ui.tr("Escolhas salvas em ", "The choices were saved in ") + name;
                activity.runOnUiThread(() -> {
                    notice(text);
                    reload();
                });
            }, "export").start();
        }

        /** The widget: the system's own picker (with the previews), or taking the one that is there off. */
        private void askWidget() {
            final boolean has = Prefs.widgetId() != 0;
            String[] options = has
                    ? new String[] {Ui.tr("Escolher outro", "Choose another"), Ui.tr("Tirar o widget", "Take the widget off")}
                    : new String[] {Ui.tr("Escolher um widget", "Choose a widget")};
            new AlertDialog.Builder(activity)
                    .setTitle(Ui.tr("Widget na tela", "Widget on the home screen"))
                    .setItems(options, (d, which) -> {
                        if (has && which == 1) {
                            Widgets.remove(activity);
                            reload();
                        } else {
                            Widgets.pick(activity, REQ_WIDGET);
                        }
                    })
                    .show();
        }

        /** The height of the widget's slot. */
        private void askWidgetSize() {
            if (Prefs.widgetId() == 0) {
                notice(Ui.tr("Escolha um widget primeiro.", "Choose a widget first."));
                return;
            }
            final int[] sizes = {100, 140, 200};
            String[] names = {Ui.tr("Pequeno", "Small"), Ui.tr("Médio", "Medium"), Ui.tr("Grande", "Big")};
            new AlertDialog.Builder(activity)
                    .setTitle(Ui.tr("Tamanho do widget", "Widget size"))
                    .setItems(names, (d, which) -> {
                        Prefs.setWidgetSize(sizes[which]);
                        reload();
                    })
                    .setNegativeButton(Ui.tr("Cancelar", "Cancel"), null)
                    .show();
        }

        /** The sync: the working folder in Termux and the command that pulls. */
        private void askSync() {
            android.widget.LinearLayout box = new android.widget.LinearLayout(activity);
            box.setOrientation(android.widget.LinearLayout.VERTICAL);
            int pad = Math.round(dp(20));
            android.widget.EditText folder = new android.widget.EditText(activity);
            folder.setHint(Ui.tr("A pasta do git no Termux (ex.: ~/pos-coders)", "The git folder in Termux (e.g. ~/pos-coders)"));
            folder.setText(Prefs.syncFolder());
            folder.setSingleLine();
            android.widget.EditText command = new android.widget.EditText(activity);
            command.setHint(Ui.tr("O comando do pull (ex.: git pull)", "The pull command (e.g. git pull)"));
            command.setText(Prefs.syncCommand());
            command.setSingleLine();
            box.addView(folder);
            box.addView(command);
            box.setPadding(pad, Math.round(dp(8)), pad, 0);
            new AlertDialog.Builder(activity)
                    .setTitle(Ui.tr("Sincronizar", "Sync"))
                    .setMessage(Ui.tr("A pasta do git é a de trabalho no Termux. Depois do pull, os arquivos são "
                                    + "copiados por cima da pasta que o Obsidian lê (o .git e o .obsidian ficam).",
                            "The git folder is the working one in Termux. After the pull, the files are copied over "
                                    + "the folder Obsidian reads (the .git and .obsidian are kept)."))
                    .setView(box)
                    .setNegativeButton(Ui.tr("Cancelar", "Cancel"), null)
                    .setPositiveButton("OK", (d, w) -> {
                        Prefs.setSyncFolder(folder.getText().toString());
                        Prefs.setSyncCommand(command.getText().toString());
                    })
                    .show();
        }

        private void tapChecklist(float y) {
            int index = (int) ((y + scroll - top + rowH / 2) / rowH);
            if (index < 0 || index >= apps.size()) return;
            String pkg = apps.get(index).pkg;
            if (picked.contains(pkg)) picked.remove(pkg); else picked.add(pkg);
            saveChecklist();
            invalidate();
        }

        // MARK: - The screens

        private void openChecklist(int which) {
            screen = which;
            scroll = 0;
            picked = new ArrayList<>();
            if (which == OFF_APPS) {
                // Every optional app that is on the tablet (even the ones with no icon: they are services).
                apps = new ArrayList<>();
                PackageManager pm = activity.getPackageManager();
                for (String pkg : HomeMode.OPTIONAL) {
                    try {
                        ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                        String label = String.valueOf(pm.getApplicationLabel(info));
                        apps.add(new HomeApps.Item(pkg, label.isEmpty() ? pkg : label));
                    } catch (PackageManager.NameNotFoundException ignored) {
                    }
                }
                for (String pkg : Prefs.optionalOff()) if (isOptional(pkg)) picked.add(pkg);
            } else {
                apps = HomeApps.all(activity);
                List<String> chosen = Prefs.favorites();
                if (chosen == null) chosen = HomeApps.defaultFavorites();
                picked.addAll(chosen);
            }
            invalidate();
        }

        private boolean isOptional(String pkg) {
            for (String p : HomeMode.OPTIONAL) if (p.equals(pkg)) return true;
            return false;
        }

        /** What the switch does, before it does it. */
        private void askSwitch() {
            boolean free = HomeMode.homeNow(activity);
            if (free) {
                // Back to the tablet's own home screen: with the ADB open it is automatic, without it the system's
                // own dialog asks the user to pick the tablet's launcher. The check opens a socket: it has to run
                // off the UI thread (Android kills the app for network on the main thread).
                new Thread(() -> {
                    boolean open = Adb.isOpen();
                    activity.runOnUiThread(() -> {
                        if (open) {
                            HomeMode.request(activity, HomeMode.ORIGINAL);
                            return;
                        }
                        askRoleBack();
                    });
                }, "adb-check").start();
                return;
            }
            String message = Ui.tr("O Início vira o Free My Tablet: uma tela de início leve, sem propaganda, "
                            + "e os apps que você não usa são desligados (nunca desinstalados).",
                    "The home screen becomes Free My Tablet: a light home screen, with no ads, "
                            + "and the apps you don't use are turned off (never uninstalled).");
            new AlertDialog.Builder(activity)
                    .setTitle(Ui.tr("Ligar o Free My Tablet?", "Turn on Free My Tablet?"))
                    .setMessage(message)
                    .setNegativeButton(Ui.tr("Cancelar", "Cancel"), null)
                    .setPositiveButton(Ui.tr("Ligar", "Turn on"), (d, w) -> turnOn())
                    .show();
        }

        /** Without the ADB: the system's own dialog asks the user to pick the tablet's launcher. */
        private void askRoleBack() {
            Intent role = HomeMode.roleIntent(activity);
            if (role != null) {
                new AlertDialog.Builder(activity)
                        .setTitle(Ui.tr("Voltar ao Início original", "Go back to the original home screen"))
                        .setMessage(Ui.tr("Escolha o Início do tablet (One UI Home) na janela do sistema.",
                                "Pick the tablet's own home screen (One UI Home) in the system's window."))
                        .setNegativeButton(Ui.tr("Cancelar", "Cancel"), null)
                        .setPositiveButton(Ui.tr("Escolher", "Choose"), (d, w) -> activity.startActivityForResult(role, REQ_ROLE))
                        .show();
                return;
            }
            new AlertDialog.Builder(activity)
                    .setTitle(Ui.tr("Voltar ao Início original", "Go back to the original home screen"))
                    .setMessage(Ui.tr("Abra o seletor de Início do sistema (segure o botão Início) e escolha o "
                                    + "Início do tablet.",
                            "Open the system's home picker (press and hold the Home button) and pick the "
                                    + "tablet's own home screen."))
                    .setPositiveButton("OK", null).show();
        }

        /** Become the home screen: the system's own dialog (Android 10+) or the tablet's ADB (Android 9). */
        private void turnOn() {
            Intent role = HomeMode.roleIntent(activity);
            if (role != null) {
                activity.startActivityForResult(role, REQ_ROLE);
                return;
            }
            HomeMode.request(activity, HomeMode.FREE);
        }

        private void askLanguage() {
            final String[] options = {"auto", "pt", "en", "es"};
            String[] names = {Ui.tr("Automático", "Automatic"), "Português", "English", "Español"};
            new AlertDialog.Builder(activity).setTitle(Ui.tr("Idioma", "Language"))
                    .setItems(names, (d, which) -> {
                        Prefs.setLanguage(options[which]);
                        activity.recreate();
                    }).show();
        }

        private void askTheme() {
            final String[] options = {"auto", "light", "dark", "read"};
            String[] names = {Ui.tr("Automático", "Automatic"), Ui.tr("Claro", "Light"), Ui.tr("Escuro", "Dark"),
                    Ui.tr("Leitura", "Reading")};
            new AlertDialog.Builder(activity).setTitle(Ui.tr("Tema", "Theme"))
                    .setItems(names, (d, which) -> {
                        Prefs.setTheme(options[which]);
                        activity.recreate();
                    }).show();
        }

        private void askAbout() {
            String version = "1.0";
            try {
                version = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
            } catch (Exception ignored) {
            }
            new AlertDialog.Builder(activity).setTitle(Ui.tr("Free My Tablet", "Free My Tablet"))
                    .setMessage(Ui.tr("Versão " + version + ".\nUm Início leve, sem propaganda, para o seu tablet.\n\n"
                                    + "Nada é desinstalado: um botão devolve tudo.",
                            "Version " + version + ".\nA light home screen, with no ads, for your tablet.\n\n"
                                    + "Nothing is uninstalled: one button brings everything back."))
                    .setPositiveButton("OK", null).show();
        }

        /** Shows a message at the bottom of the screen (what the switch did). */
        void notice(String text) {
            statusText = text;
            invalidate();
        }

        /** The checklist changed: keep it (and turn the apps off right away, when the ADB is open). */
        private void saveChecklist() {
            if (screen == OFF_APPS) {
                Prefs.setOptionalOff(picked);
                HomeMode.applyOptional(activity);
            } else {
                Prefs.setFavorites(picked);
            }
        }
    }
}
