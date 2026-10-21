package io.freemytablet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
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
    private SettingsView view;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        view = new SettingsView(this);
        setContentView(view);
        getWindow().setBackgroundDrawable(null);
    }

    /** The user picked the home screen in the system's dialog. */
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_ROLE) return;
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
        private float scroll;
        private List<String> rows = new ArrayList<>();     // the labels of the main screen
        private List<HomeApps.Item> apps = new ArrayList<>();  // the checklist
        private List<String> picked = new ArrayList<>();   // the packages checked in the checklist
        private boolean running;
        private long lastFrame;
        private String statusText = "";

        static final int MAIN = 0, OFF_APPS = 1, DOCK = 2;

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
            running = true;
            lastFrame = 0;
            Choreographer.getInstance().postFrameCallback(this);
            handler.post(poll);
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
                JSONObject state = HomeMode.state(activity);
                String busy = state.optString("busy", "");
                String error = state.optString("error", "");
                statusText = !busy.isEmpty() ? busy : error;
                handler.postDelayed(this, 700);
            }
        };

        // MARK: - The rows

        private List<String> mainRows() {
            List<String> out = new ArrayList<>();
            out.add(Ui.tr("Tela de Início", "Home screen"));
            out.add(Ui.tr("Apps para desligar", "Apps to turn off"));
            out.add(Ui.tr("Barra de apps", "Dock"));
            out.add(Ui.tr("Idioma", "Language"));
            out.add(Ui.tr("Tema", "Theme"));
            out.add(Ui.tr("Sobre", "About"));
            return out;
        }

        /** The text on the right of a row: what it is now. */
        private String value(int row) {
            JSONObject state = HomeMode.state(activity);
            String mode = state.optString("mode", HomeMode.ORIGINAL);
            switch (row) {
                case 0: return HomeMode.FREE.equals(mode)
                        ? Ui.tr("Free My Tablet", "Free My Tablet")
                        : Ui.tr("Original", "Original");
                case 1: return Ui.tr("Apps que você não usa", "Apps you don't use");
                case 2: return Ui.tr("Escolher", "Choose");
                case 3: {
                    String l = Prefs.language();
                    return "auto".equals(l) ? Ui.tr("Automático", "Automatic")
                            : "pt".equals(l) ? "Português" : "es".equals(l) ? "Español" : "English";
                }
                case 4: {
                    String t = Prefs.theme();
                    return "light".equals(t) ? Ui.tr("Claro", "Light") : "dark".equals(t) ? Ui.tr("Escuro", "Dark")
                            : Ui.tr("Automático", "Automatic");
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
                String v = value(i);
                float vw = rowValue.measureText(v);
                c.drawText(v, w - dp(20) - vw, y + dp(30), rowValue);
            }
        }

        private String title() {
            return screen == OFF_APPS ? Ui.tr("Apps para desligar", "Apps to turn off")
                    : Ui.tr("Barra de apps", "Dock");
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
                    downY = e.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    scroll -= e.getY() - lastY;
                    lastY = e.getY();
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP: {
                    if (Math.abs(e.getY() - downY) > dp(12)) return true;   // it was a scroll
                    if (screen == MAIN) tapMain(e.getY());
                    else tapChecklist(e.getY());
                    return true;
                }
                default:
                    return super.onTouchEvent(e);
            }
        }

        private float downY, lastY;

        private void tapMain(float y) {
            int index = (int) ((y + scroll - top + rowH / 2) / rowH);
            if (index < 0 || index >= rows.size()) return;
            switch (index) {
                case 0: askSwitch(); break;
                case 1: openChecklist(OFF_APPS); break;
                case 2: openChecklist(DOCK); break;
                case 3: askLanguage(); break;
                case 4: askTheme(); break;
                default: askAbout(); break;
            }
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
            apps = HomeApps.grid(activity);
            picked = new ArrayList<>();
            if (which == OFF_APPS) {
                // Only the apps that are in the optional list can be turned off.
                List<HomeApps.Item> only = new ArrayList<>();
                for (HomeApps.Item item : apps) if (isOptional(item.pkg)) only.add(item);
                apps = only;
                for (String pkg : Prefs.optionalOff()) if (isOptional(pkg)) picked.add(pkg);
            } else {
                List<String> shelf = Prefs.shelf();
                if (shelf != null) picked.addAll(shelf);
            }
            invalidate();
        }

        private boolean isOptional(String pkg) {
            for (String p : HomeMode.OPTIONAL) if (p.equals(pkg)) return true;
            return false;
        }

        /** What the switch does, before it does it. */
        private void askSwitch() {
            JSONObject state = HomeMode.state(activity);
            boolean free = HomeMode.FREE.equals(state.optString("mode", HomeMode.ORIGINAL));
            if (free) {
                // Back to the tablet's own home screen: with the ADB open it is automatic, without it the system's
                // own dialog asks the user to pick the tablet's launcher.
                if (Adb.isOpen()) {
                    HomeMode.request(activity, HomeMode.ORIGINAL);
                    return;
                }
                Intent role = HomeMode.roleIntent(activity);
                if (role != null) {
                    new AlertDialog.Builder(activity)
                            .setTitle(Ui.tr("Voltar ao Início original", "Go back to the original home screen"))
                            .setMessage(Ui.tr("Escolha o Início do tablet (One UI Home) na janela do sistema.",
                                    "Pick the tablet's own home screen (One UI Home) in the system's window."))
                            .setNegativeButton(Ui.tr("Cancelar", "Cancel"), null)
                            .setPositiveButton(Ui.tr("Escolher", "Choose"), (d, w) -> activity.startActivityForResult(role, REQ_ROLE))
                            .show();
                } else {
                    new AlertDialog.Builder(activity)
                            .setTitle(Ui.tr("Voltar ao Início original", "Go back to the original home screen"))
                            .setMessage(Ui.tr("Abra o seletor de Início do sistema (segure o botão Início) e escolha o "
                                            + "Início do tablet.",
                                    "Open the system's home picker (press and hold the Home button) and pick the "
                                            + "tablet's own home screen."))
                            .setPositiveButton("OK", null).show();
                }
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
            final String[] options = {"auto", "light", "dark"};
            String[] names = {Ui.tr("Automático", "Automatic"), Ui.tr("Claro", "Light"), Ui.tr("Escuro", "Dark")};
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

        /** The checklist changed: keep it. */
        private void saveChecklist() {
            if (screen == OFF_APPS) {
                Prefs.setOptionalOff(picked);
            } else {
                Prefs.setShelf(picked);
            }
        }
    }
}
