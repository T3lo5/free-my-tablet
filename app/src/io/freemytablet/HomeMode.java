package io.freemytablet;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Original ⇄ Free My Tablet. "free": our home screen, and the apps in the lists below turned off
 * (pm disable-user: still installed, back with one button). "original": everything as it came from the factory.
 *
 * It all goes through the tablet's own ADB (Adb, 127.0.0.1:5555), which you allow once on the tablet. The switches run
 * as background jobs (ModeJob) in the main process; the settings screen (the :home process) asks for them and follows
 * the state file. Only the tablet itself asks for switches: nothing here listens to the network.
 */
final class HomeMode {
    private HomeMode() {}

    static final String PKG = "io.freemytablet";
    static final String HOME_ACTIVITY = PKG + "/" + PKG + ".HomeActivity";
    static final String SETTINGS_ACTIVITY = PKG + "/" + PKG + ".SettingsActivity";
    static final String FREE = "free", ORIGINAL = "original";

    /** Samsung's own home screen (One UI): the one that comes back with "Original". */
    static final String SAMSUNG_HOME = "com.sec.android.app.launcher";
    /** Samsung's setup wizard: on some tablets it is also a home screen (its recovery activity). */
    static final String SAMSUNG_SETUP = "com.samsung.android.setupwizard";
    /** Google's setup wizard: on some tablets it is the home screen too (Google TV's recovery home). */
    static final String GOOGLE_SETUP = "com.google.android.tungsten.setupwraith";

    /**
     * Turned off in Free My Tablet mode, when the tablet has them: the preinstalled bloat nobody asked for.
     * Samsung and Microsoft apps the manufacturer ships to fill the screen with advertising. Only system apps:
     * the apps you installed are never touched.
     */
    static final String[] DEBLOAT = {
        // Samsung's store and its extras
        "com.sec.android.app.samsungapps",        // Galaxy Store
        "com.sec.android.widgetapp.samsungapps",  // Galaxy Store (the widget)
        "com.samsung.android.themecenter",        // Galaxy Themes
        "com.samsung.android.app.clockpackage",   // Samsung Clock (the widget; the system's clock stays)
        "com.samsung.android.stickercenter",      // Sticker Center (the emoji pack)
        "com.samsung.android.smartswitchassistant",  // Smart Switch (moving data from the old phone)
        "com.samsung.android.rubin.app",          // Customization Service
        "com.sec.android.app.chromecustomizations",   // ChromeCustomizations (the menu it adds to Chrome)
        "com.sec.android.usermanual",             // User Manual
        "com.sec.android.diagmonagent",           // Diagnostics (1,7 GB here, and it reports the use of the tablet)
        "com.sec.android.app.servicemodeapp",     // Service mode
        "com.sec.android.app.factorykeystring",
        // Microsoft (the Office suite and OneDrive, on some tablets)
        "com.microsoft.skydrive",
        "com.microsoft.office.outlook", "com.microsoft.office.word", "com.microsoft.office.excel",
        "com.microsoft.office.powerpoint", "com.microsoft.office.onenote", "com.microsoft.office.excelviewer",
        "com.microsoft.office.365mobileapps", "com.microsoft.teams",
        // Social networks (preinstalled, no account, only notifications)
        "com.facebook.services", "com.facebook.system", "com.facebook.appmanager", "com.facebook.katana",
        "com.linkedin.android",
    };

    /**
     * Turned off only when the person asks for it in the settings: apps that are bloat for some and useful for
     * others. The tablet keeps them on by default.
     */
    static final String[] OPTIONAL = {
        "com.google.android.googlequicksearchbox",    // Google (the news feed and the search widget)
        "com.google.android.apps.tachyon",             // Google Meet
        "com.samsung.android.game.gos",                // Game Optimizing Service
        "com.samsung.android.scloud",                  // Samsung Cloud
        "com.sec.android.app.soundalive",              // SoundAlive
        "com.sec.android.daemonapp",                   // Weather (Samsung)
        "com.sec.android.app.dressroom",               // Wallpapers
        "com.samsung.android.app.sharelive",           // Quick Share
        "com.samsung.android.app.sbrowser",            // Samsung Internet
        "com.google.android.apps.books",               // Google Play Books
        "com.google.android.apps.magazines",           // Google Play Magazines
        "com.google.android.apps.music",               // Google Play Music
        "com.google.android.videos",                   // Google Play Movies
        "com.google.android.youtube.tvmusic",           // YouTube Music
        "com.samsung.android.app.shealth",             // Samsung Health
        "com.google.android.tts",                      // the text-to-speech engine (560 MB here)
        "com.google.android.apps.maps",                // Google Maps (preinstalled)
        "com.sec.android.daemonapp",                   // Samsung Weather
        "com.samsung.android.app.arzone",              // AR Zone
        "com.samsung.android.aremoji",                 // AR Emoji
        "com.samsung.android.app.gamehome",            // Game Launcher
        "com.sec.android.app.kidshome",                // Samsung Kids (a second launcher)
        "com.samsung.android.bixby.agent",             // Bixby
        "com.samsung.android.bixby.wakeup",
        "com.samsung.android.bixbyvision.framework",
    };

    /** The voice assistant, only with disable_assistant=true in config.properties. */
    static final String[] ASSISTANT = {"com.google.android.katniss", "com.android.hotwordenrollment.okgoogle"};

    /** Never turned off, not even by mistake (settings, keyboard, Play Store, the system itself). Checked before every disable. */
    private static final Set<String> PROTECTED = new HashSet<>(Arrays.asList(
        "android", "com.android.systemui", "com.android.settings", "com.android.shell", "com.android.keychain",
        "com.android.providers.settings", "com.android.providers.media", "com.android.providers.downloads",
        "com.android.certinstaller", "com.android.packageinstaller", "com.android.permissioncontroller",
        "com.android.phone", "com.android.bluetooth", "com.android.nfc", "com.android.printspooler",
        "com.android.documentsui", "com.android.externalstorage",
        "com.google.android.gms", "com.google.android.gsf", "com.android.vending", "com.google.android.inputmethod.latin",
        "com.google.android.webview", "com.google.android.apps.wellbeing", "com.google.android.projection.gearhead",
        "com.google.android.setupwizard", "com.google.android.documentsui",
        "com.google.android.captiveportallogin",       // the page that opens when the Wi-Fi asks for a login
        "com.samsung.android.app.settings", "com.sec.android.app.settings",
        "com.samsung.android.honeyboard", "com.sec.android.inputmethod",   // the keyboards
        "com.samsung.android.emergency", "com.sec.android.emergencylauncher", "com.sec.android.provider.emergencymode",
        "com.samsung.android.fmm", "com.samsung.android.mdm", "com.samsung.android.sdm.config",
        "com.samsung.android.sm.devicesecurity", "com.samsung.android.securitylogagent",
        "com.samsung.android.bluelightfilter",        // the night mode (nice for reading)
        "com.samsung.android.dialer", "com.samsung.android.incallui", "com.samsung.android.telephonyui",
        "com.samsung.android.providers.media", "com.samsung.android.providers.contacts",
        "com.samsung.android.providers.carrier", "com.samsung.android.calendar",
        "com.samsung.android.app.contacts", "com.samsung.android.email.provider",
        "com.samsung.android.app.notes", "com.samsung.android.app.clockpackage",
        "com.sec.android.gallery3d", "com.sec.android.app.camera", "com.sec.android.app.myfiles",
        "com.sec.android.app.launcher",               // Samsung's own home screen: it stays (the way back)
        "com.sec.android.app.samsungapps.uac",
        PKG));

    /** One switch at a time (jobs can overlap: a boot check and a button press). */
    private static final Object RUN = new Object();
    private static final Object FILE_LOCK = new Object();

    // MARK: - The home role (Android 10 and newer)

    /**
     * From Android 10 on, the home screen is a role the system gives to whoever the user picks: no ADB and no
     * scary prompts, just the system's own dialog. On Android 9 there is no role, and the home screen is set
     * through the tablet's own ADB.
     */
    static final boolean HAS_ROLES = Build.VERSION.SDK_INT >= 29;

    /** Whether our home screen is the system's home screen now. */
    static boolean roleHeld(Context c) {
        if (Build.VERSION.SDK_INT >= 30) {
            RoleManager rm = (RoleManager) c.getSystemService(Context.ROLE_SERVICE);
            if (rm != null) return rm.isRoleHeld(RoleManager.ROLE_HOME);
        }
        return state(c).optBoolean("roleHeld", false);
    }

    /**
     * The system's own dialog to choose the home screen (null when the system has no such dialog). The same
     * dialog serves for both ways: to become the home screen, and to give it back to the tablet's own launcher.
     */
    static Intent roleIntent(Context c) {
        if (!HAS_ROLES) return null;
        RoleManager rm = (RoleManager) c.getSystemService(Context.ROLE_SERVICE);
        if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_HOME)) return null;
        return rm.createRequestRoleIntent(RoleManager.ROLE_HOME);
    }

    /** The way back, without the ADB: the user picks the tablet's own launcher in the system's dialog. */
    static void markOriginal(Context c) {
        JSONObject s = state(c);
        put(s, "mode", ORIGINAL);
        put(s, "since", System.currentTimeMillis());
        put(s, "roleHeld", false);
        save(c, s);
        notice(c, Ui.tr("Modo original: o Início do tablet voltou.",
                "Original mode: the tablet's own home screen is back."));
    }

    /** Puts a value in the state (the JSON error never happens: the keys are ours). */
    private static void put(JSONObject s, String key, Object value) {
        try {
            s.put(key, value);
        } catch (JSONException ignored) {
        }
    }

    // MARK: - Requests (from any process)

    /** Asks the main process to switch (the settings screen calls this). */
    static void request(Context c, String mode) {
        ModeJob.schedule(c, ModeJob.APPLY, mode, 0);
    }

    /** Our home screen crashed 3 times in 5 minutes: give the home screen back to the tablet's own (asked by :home). */
    static void requestRevertHome(Context c) {
        ModeJob.schedule(c, ModeJob.REVERT, null, 0);
    }

    /** After the tablet boots (and after an update): wait for the system to settle and check Free My Tablet mode. */
    static void bootCheck(Context c) {
        ModeJob.schedule(c, ModeJob.VERIFY, null, 45_000);
    }

    /** After an update of this app: complete Free My Tablet mode if anything is missing. */
    static void verify(Context c) {
        ModeJob.schedule(c, ModeJob.VERIFY, null, 0);
    }

    /**
     * Whether our home screen is the one the system shows now (the system's own answer, no ADB needed).
     * This is the truth the settings screen shows: the state file can be out of date.
     */
    static boolean homeNow(Context c) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo info = c.getPackageManager().resolveActivity(intent, 0);
            if (info == null || info.activityInfo == null) return false;
            return PKG.equals(info.activityInfo.packageName);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Checks the home screen against the state and fixes it (the state can be out of date when a switch failed
     * halfway). It runs in a thread: the settings screen asks for it when it opens, because the system does not
     * always run the background job (the manufacturer stops it).
     */
    static void checkNow(Context c) {
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            boolean ours = homeNow(app);
            JSONObject s = state(app);
            String mode = ours ? FREE : ORIGINAL;
            if (!mode.equals(s.optString("mode"))) {
                put(s, "mode", mode);
                put(s, "roleHeld", ours);
                save(app, s);
                AppLog.w("the state was wrong: the home screen is " + (ours ? "ours" : "the tablet's own"));
            }
        }, "check").start();
    }

    // MARK: - Switches (main process, from ModeJob)

    static void applyNow(Context c, String mode) {
        synchronized (RUN) {
            if (FREE.equals(mode)) applyFree(c); else applyOriginal(c);
        }
    }

    private static void applyFree(Context c) {
        JSONObject s = state(c);
        // The home screen: from Android 10 on, the role was already given by the system (the settings screen asked
        // for it). On Android 9 there is no role, and it goes through the tablet's own ADB.
        boolean homeOk = roleHeld(c);
        int turned = 0;
        // One connection for everything: the tablet only asks for the debugging permission once.
        try (Adb adb = connect(c, s)) {
            if (!homeOk) homeOk = setHomeWithAdb(c, adb);
            if (homeOk) turned = turnAppsOff(c, adb);
        } catch (IOException e) {
            AppLog.w("could not reach the tablet's ADB: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            if (!homeOk) {
                done(c, Ui.tr("O Android não aceitou o novo Início. Sem ADB no tablet, use o seletor de Início do "
                                + "sistema (segure o botão Início) e escolha o Free My Tablet.",
                        "Android didn't accept the new home screen. Without the tablet's ADB, use the system's own "
                                + "home picker (press and hold the Home button) and pick Free My Tablet."));
                return;
            }
            notice(c, Ui.tr("O Início novo está no lugar. Os apps não puderam ser desligados: o ADB do tablet não está "
                            + "aberto (\"adb tcpip 5555\" pelo computador).",
                    "The new home screen is in place. The apps could not be turned off: the tablet's ADB is not open "
                            + "(run \"adb tcpip 5555\" from your computer)."));
        }
        if (!homeOk) return;
        s = state(c);
        put(s, "roleHeld", true);
        save(c, s);
        Watchdog.clear(c);
        s = state(c);
        put(s, "mode", FREE);
        put(s, "since", System.currentTimeMillis());
        put(s, "homeReverted", false);
        save(c, s);
        notice(c, Ui.tr("Free My Tablet ligado: Início novo, " + turned + " apps desligados.",
                "Free My Tablet is on: new home screen, " + turned + " apps turned off."));
        done(c, null);
    }

    /** Sets our home screen through the tablet's ADB (Android 9, or when the role was refused). */
    private static boolean setHomeWithAdb(Context c, Adb adb) throws IOException {
        JSONObject s = state(c);
        // The tablet's own home screen, recorded once (before ours replaces it) so "original" restores exactly what
        // was there, on any brand.
        String home = originalHome(s);
        if (home.isEmpty() || home.startsWith(PKG + "/")) {
            String found = currentHome(adb);
            home = found != null ? found : "";
        }
        put(s, "originalHome", home);
        save(c, s);
        // Turning off an app makes Android delete the Wi-Fi networks it saved, and Original can't bring them back.
        // The setup wizard usually saved the network: then stop here, before anything changes.
        Set<String> owners = wifiOwners(adb);
        boolean setupHome = setupIsHome(adb);
        if (owners.contains(packageOf(home)) || (setupHome && owners.contains(GOOGLE_SETUP))) {
            throw new IOException(Ui.tr("a configuração inicial do tablet salvou o Wi-Fi, e desligá-la apagaria essas redes. "
                    + "Em Configurações → Conexões → Wi-Fi, esqueça a rede e conecte de novo (as fora de alcance não aparecem "
                    + "lá: o Claude Code apaga pelo computador) e tente outra vez.",
                    "the tablet's first setup saved the Wi-Fi, and turning it off would erase those networks. "
                    + "In Settings → Connections → Wi-Fi, forget the network and connect again (networks out of range don't "
                    + "show there: Claude Code can remove them from your computer), then try once more.",
                    "la configuración inicial del tablet guardó el Wi-Fi, y desactivarla borraría esas redes. "
                    + "En Ajustes → Conexiones → Wi-Fi, olvida la red y vuelve a conectarte (las fuera de alcance no aparecen "
                    + "ahí: Claude Code las borra desde la computadora) y vuelve a intentarlo."));
        }
        busy(c, Ui.tr("Trocando o Início…", "Switching the home screen…"));
        check(adb.shell("cmd package set-home-activity " + HOME_ACTIVITY), "Success",
                Ui.tr("o Android não aceitou o novo Início", "Android didn't accept the new home screen"));
        put(s, "setupHome", setupHome);
        save(c, s);
        return true;
    }

    /** Turns off the apps that don't belong (the list in DEBLOAT and the ones the person picked). */
    private static int turnAppsOff(Context c, Adb adb) throws IOException {
        JSONObject s = state(c);
        String home = originalHome(s);
        boolean setupHome = s.optBoolean("setupHome", true);
        List<String> targets = targets(home, setupHome);
        Set<String> disabled = new HashSet<>(stringList(s, "disabled"));
        Set<String> present = packages(adb, "");
        Set<String> owners = wifiOwners(adb);
        int done = 0;
        for (String pkg : targets) {
            done++;
            busy(c, Ui.tr("Desligando apps (", "Turning off apps (") + done + "/" + targets.size() + ")…");
            if (PROTECTED.contains(pkg) || !present.contains(pkg) || owners.contains(pkg)) continue;
            String out = adb.shell("pm disable-user --user 0 " + pkg);
            if (out.contains("disabled-user")) disabled.add(pkg);
            else AppLog.w("could not turn off " + pkg + ": " + out.trim());
        }
        put(s, "disabled", new JSONArray(sorted(disabled)));
        save(c, s);
        return disabled.size();
    }

    private static void applyOriginal(Context c) {
        JSONObject s = state(c);
        // The home screen back to the tablet's own: the recorded home is set again through the ADB. Without it
        // (the port closed), nothing changes: the settings screen says so and the person picks the tablet's
        // launcher in the system's dialog.
        boolean homeOk = false;
        List<String> failed = new ArrayList<>();
        try (Adb adb = connect(c, s)) {
            busy(c, Ui.tr("Devolvendo o Início original…", "Restoring the original home screen…"));
            homeOk = restoreHome(adb, originalHome(s));
            busy(c, Ui.tr("Religando os apps…", "Turning the apps back on…"));
            failed = turnAppsOn(c, adb);
        } catch (IOException e) {
            AppLog.w("could not reach the tablet's ADB: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            done(c, Ui.tr("Não deu para voltar ao original: o ADB do tablet não está aberto "
                            + "(\"adb tcpip 5555\" pelo computador, com o tablet ligado pelo USB).",
                    "I couldn't go back to the original: the tablet's ADB is not open "
                            + "(run \"adb tcpip 5555\" from your computer, with the tablet plugged in)."));
            return;   // nothing changed: the state stays as it was
        }
        s = state(c);
        put(s, "mode", ORIGINAL);
        put(s, "since", System.currentTimeMillis());
        put(s, "homeReverted", false);
        put(s, "roleHeld", false);
        put(s, "disabled", new JSONArray(failed));
        save(c, s);
        if (!failed.isEmpty()) {
            notice(c, Ui.tr("Modo original, mas não consegui religar: ", "Original mode, but I couldn't turn back on: ")
                    + String.join(", ", failed) + ".");
        } else if (homeOk) {
            notice(c, Ui.tr("Modo original: Início e apps de fábrica de volta.",
                    "Original mode: the factory home screen and apps are back."));
        } else {
            notice(c, Ui.tr("Escolha o Início do tablet no aviso do sistema (o botão Início, segure) para voltar.",
                    "Pick the tablet's own home screen in the system's dialog (press and hold the Home button) to go back."));
        }
        done(c, null);
    }

    /** Turns the apps back on: everything that was turned off, plus the ones in the lists. */
    private static List<String> turnAppsOn(Context c, Adb adb) throws IOException {
        JSONObject s = state(c);
        String home = originalHome(s);
        Set<String> toEnable = new HashSet<>(stringList(s, "disabled"));
        for (String pkg : targets(home, true)) {
            if (!Arrays.asList(DEBLOAT).contains(pkg) && !Arrays.asList(ASSISTANT).contains(pkg)
                    && !Arrays.asList(OPTIONAL).contains(pkg)) toEnable.add(pkg);
        }
        Set<String> present = packages(adb, "");
        int done = 0;
        List<String> failed = new ArrayList<>();
        for (String pkg : sorted(toEnable)) {
            done++;
            busy(c, Ui.tr("Religando apps (", "Turning apps back on (") + done + "/" + toEnable.size() + ")…");
            if (!present.contains(pkg)) continue;
            // Checked: whatever didn't come back stays on the list (and in the notice); nothing disappears silently.
            String out = adb.shell("pm enable " + pkg);
            if (!out.contains("new state: enabled")) failed.add(pkg);
        }
        return failed;
    }

    /** The recorded home screen; without one, the system's own (we leave the role). */
    private static boolean restoreHome(Adb adb, String home) throws IOException {
        if (!home.isEmpty()) {
            adb.shell("pm enable " + packageOf(home));
            if (adb.shell("cmd package set-home-activity " + home).contains("Success")) return true;
        }
        adb.shell("cmd role remove-role-holder --user 0 android.app.role.HOME " + PKG);
        return !homeHolder(adb).contains(PKG);
    }

    /** Safety net: our home screen crashed 3 times in a row. Only the home screen goes back; the rest stays. */
    static void revertHomeNow(Context app) {
        synchronized (RUN) {
            try (Adb adb = Adb.connect(app, 10_000, null)) {
                JSONObject s = state(app);
                String home = originalHome(s);
                adb.shell("pm enable " + GOOGLE_SETUP);
                adb.shell("pm enable " + SAMSUNG_SETUP);
                restoreHome(adb, home);
                String homePkg = packageOf(home);
                JSONArray kept = new JSONArray();
                for (String pkg : stringList(s, "disabled")) {
                    if (!pkg.equals(GOOGLE_SETUP) && !pkg.equals(homePkg)) kept.put(pkg);
                }
                s.put("homeReverted", true).put("disabled", kept);
                save(app, s);
                notice(app, Ui.tr("O Início do Free My Tablet caiu 3 vezes seguidas; voltei para o Início original.",
                        "Free My Tablet's home screen crashed 3 times in a row; I switched back to the original one."));
                adb.shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME");
            } catch (Exception e) {
                AppLog.w("could not give the home screen back: " + e.getMessage());
            }
        }
    }

    /**
     * In Free My Tablet mode, checks that everything is still as we left it (the listed apps off, our home screen)
     * and completes whatever is missing, with a notice. Runs after the tablet boots (updates can turn things back on)
     * and after an update of this app.
     */
    static void verifyNow(Context app) {
        synchronized (RUN) {
            JSONObject s = state(app);
            String fingerprint = Build.FINGERPRINT;
            boolean updated = !fingerprint.equals(s.optString("fingerprint", fingerprint));
            try {
                s.put("fingerprint", fingerprint);
                save(app, s);
            } catch (JSONException ignored) {}
            List<String> fixed = new ArrayList<>();
            try (Adb adb = Adb.connect(app, 45_000, () -> notice(app, Ui.tr(
                    "Aceite o aviso \"Permitir depuração\" no tablet (marque \"Sempre permitir\").",
                    "Accept \"Allow USB debugging?\" on the tablet (tick \"Always allow\").")))) {
                // The state can be out of date (a switch that failed halfway): the home screen says the truth.
                String real = currentHome(adb);
                boolean ours = real != null && real.startsWith(PKG + "/");
                String mode = ours ? FREE : ORIGINAL;
                if (!mode.equals(s.optString("mode"))) {
                    put(s, "mode", mode);
                    put(s, "roleHeld", ours);
                    save(app, s);
                }
                if (!FREE.equals(mode)) return;   // the tablet is on its own home screen: nothing to do
                Set<String> present = packages(adb, "");
                Set<String> disabledNow = packages(adb, "-d");
                Set<String> owners = wifiOwners(adb);
                boolean reverted = s.optBoolean("homeReverted");
                String home = originalHome(s);
                Set<String> wanted = new HashSet<>(stringList(s, "disabled"));
                for (String pkg : targets(home, s.optBoolean("setupHome", true))) {
                    if (!reverted || !(pkg.equals(packageOf(home)) || pkg.equals(GOOGLE_SETUP))) wanted.add(pkg);
                }
                Set<String> disabled = new HashSet<>(stringList(s, "disabled"));
                for (String pkg : sorted(wanted)) {
                    if (PROTECTED.contains(pkg) || !present.contains(pkg) || owners.contains(pkg)) continue;
                    if (disabledNow.contains(pkg)) {
                        disabled.add(pkg);
                        continue;
                    }
                    if (adb.shell("pm disable-user --user 0 " + pkg).contains("disabled-user")) {
                        disabled.add(pkg);
                        fixed.add(pkg);
                    }
                }
                if (!reverted && !homeHolder(adb).contains(PKG)) {
                    adb.shell("cmd package set-home-activity " + HOME_ACTIVITY);
                    fixed.add(Ui.tr("o Início", "the home screen"));
                }
                JSONObject latest = state(app);
                latest.put("disabled", new JSONArray(sorted(disabled)));
                save(app, latest);
            } catch (Exception e) {
                notice(app, Ui.tr("Não consegui conferir o Free My Tablet: ", "Couldn't check Free My Tablet: ") + e.getMessage());
                return;
            }
            if (!fixed.isEmpty()) {
                notice(app, (updated ? Ui.tr("A atualização do tablet religou ", "The tablet update turned back on ")
                        : Ui.tr("Desliguei o que faltava: ", "Turned off what was missing: ")) + String.join(", ", fixed) + ".");
            } else if (updated) {
                notice(app, Ui.tr("O tablet foi atualizado; o Free My Tablet continua igual.",
                        "The tablet was updated; Free My Tablet is still on."));
            }
        }
    }

    /**
     * What to turn off: the tablet's own home screen, the setup wizard (when it is one of the home screens), the
     * optional apps the person turned off in the settings, and the lists above.
     */
    private static List<String> targets(String home, boolean setupHome) {
        List<String> out = new ArrayList<>();
        // The tablet's own home screen stays enabled: the switch is the HOME role, and the way back needs it there.
        out.addAll(Arrays.asList(DEBLOAT));
        out.addAll(optionalOff());
        if (Config.DISABLE_ASSISTANT) out.addAll(Arrays.asList(ASSISTANT));
        return out;
    }

    /** The optional apps the person turned off in the settings (the ones on the tablet). */
    static List<String> optionalOff() {
        List<String> out = new ArrayList<>();
        for (String pkg : Prefs.optionalOff()) {
            if (Arrays.asList(OPTIONAL).contains(pkg)) out.add(pkg);
        }
        return out;
    }

    /**
     * Applies right away the optional apps the person just chose: the ones they marked are turned off, the others
     * are turned back on. It runs in a thread and needs the tablet's ADB (the port has to be open).
     */
    static void applyOptional(Context c) {
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try (Adb adb = Adb.connect(app, 20_000, null)) {
                Set<String> off = new HashSet<>(optionalOff());
                Set<String> present = packages(adb, "");
                Set<String> disabled = packages(adb, "-d");
                int turned = 0;
                for (String pkg : OPTIONAL) {
                    if (!present.contains(pkg)) continue;
                    boolean shouldBeOff = off.contains(pkg);
                    boolean isOff = disabled.contains(pkg);
                    if (shouldBeOff == isOff) continue;
                    String out = adb.shell((shouldBeOff ? "pm disable-user --user 0 " : "pm enable ") + pkg);
                    if (out.contains("disabled-user") || out.contains("new state: enabled")) turned++;
                    else AppLog.w("could not change " + pkg + ": " + out.trim());
                }
                notice(app, turned == 0
                        ? Ui.tr("Os apps que você marcou estão assim.", "The apps you marked are like that.")
                        : Ui.tr(turned + " apps desligados (ou religados).", turned + " apps turned off (or back on).")
                        );
            } catch (Exception e) {
                notice(app, Ui.tr("Não consegui mudar os apps: o ADB do tablet não está aberto "
                                + "(\"adb tcpip 5555\" pelo computador).",
                        "I couldn't change the apps: the tablet's ADB is not open "
                                + "(run \"adb tcpip 5555\" from your computer)."));
            }
        }, "optional").start();
    }

    /** The home screen in use now ("package/activity"): the HOME role holder, among the tablet's home screens. */
    private static String currentHome(Adb adb) throws IOException {
        String holder = homeHolder(adb);
        if (holder.isEmpty() || holder.contains(PKG)) return null;
        String list = adb.shell("cmd package query-activities --brief --user 0 -a android.intent.action.MAIN"
                + " -c android.intent.category.HOME");
        for (String line : list.split("\n")) {
            line = line.trim();
            if (line.startsWith(holder + "/")) return line;
        }
        return null;
    }

    /**
     * The package that is the home screen now, or "". Some builds have no `cmd role get-role-holders` (it answers
     * "Unknown command"), so then we ask which activity opens HOME.
     */
    private static String homeHolder(Adb adb) throws IOException {
        String holder = adb.shell("cmd role get-role-holders --user 0 android.app.role.HOME").trim();
        if (holder.matches("[\\w.]+(;[\\w.]+)*")) return holder;
        String[] lines = adb.shell("cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN"
                + " -c android.intent.category.HOME").trim().split("\n");
        String component = lines[lines.length - 1].trim();
        // With no default home yet, the system's chooser answers ("android/...ResolverActivity"): unknown.
        return component.contains("/") && !component.startsWith("android/") ? packageOf(component) : "";
    }

    /** Whether a setup wizard is one of the home screens (their recovery activity), so it must go off too. */
    private static boolean setupIsHome(Adb adb) throws IOException {
        String list = adb.shell("cmd package query-activities --brief -a android.intent.action.MAIN"
                + " -c android.intent.category.HOME");
        return list.contains(GOOGLE_SETUP + "/") || list.contains(SAMSUNG_SETUP + "/");
    }

    /** Apps that saved a Wi-Fi network ("cname" in the Wi-Fi service's list of saved networks). */
    private static Set<String> wifiOwners(Adb adb) throws IOException {
        Set<String> owners = new HashSet<>();
        Matcher m = Pattern.compile("cname=([\\w.]+)").matcher(adb.shell("dumpsys wifi | grep -o 'cname=[^ ]*'"));
        while (m.find()) owners.add(m.group(1));
        return owners;
    }

    /** The recorded home screen of the tablet. */
    private static String originalHome(JSONObject s) {
        String home = s.optString("originalHome", "");
        // The setup wizard standing in as the home screen is not the tablet's real one: keep looking.
        if (home.startsWith(GOOGLE_SETUP + "/") || home.startsWith(SAMSUNG_SETUP + "/")) return "";
        return home;
    }

    private static String packageOf(String component) {
        if (component == null || component.isEmpty()) return null;
        int slash = component.indexOf('/');
        return slash > 0 ? component.substring(0, slash) : component;
    }

    /** Packages the shell sees (all; "-d" = only the disabled ones). The app alone can't see the ones it doesn't declare. */
    private static Set<String> packages(Adb adb, String flags) throws IOException {
        Set<String> out = new HashSet<>();
        for (String line : adb.shell("pm list packages " + flags).split("\n")) {
            line = line.trim();
            if (line.startsWith("package:")) out.add(line.substring(8));
        }
        return out;
    }

    private static Adb connect(Context c, JSONObject s) throws IOException {
        busy(c, Ui.tr("Conectando…", "Connecting…"));
        Adb adb = Adb.connect(c, 120_000, () -> busy(c, Ui.tr("Aceite o aviso “Permitir depuração” no tablet (marque “Sempre permitir”).",
                "Accept “Allow USB debugging?” on the tablet (tick “Always allow”).")));
        if (!s.optBoolean("adbAuthorized")) {
            JSONObject latest = state(c);
            try { latest.put("adbAuthorized", true); } catch (JSONException ignored) {}
            save(c, latest);
        }
        return adb;
    }

    private static void check(String output, String expected, String error) throws IOException {
        if (!output.contains(expected)) throw new IOException(error + " (" + output.trim() + ")");
    }

    // MARK: - Measurements (without opening apps: memory and the home button)

    private static void measureHomeTime(Context c, Adb adb, String mode) throws IOException, JSONException {
        List<Integer> samples = new ArrayList<>();
        Pattern total = Pattern.compile("TotalTime: (\\d+)");
        for (int i = 0; i < 3; i++) {
            adb.shell("am start -W -n " + SETTINGS_ACTIVITY);
            SystemClock.sleep(500);
            Matcher m = total.matcher(adb.shell("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME"));
            if (m.find()) samples.add(Integer.parseInt(m.group(1)));
            SystemClock.sleep(600);
        }
        adb.shell("am start -W -n " + SETTINGS_ACTIVITY);
        if (samples.isEmpty()) return;
        Collections.sort(samples);
        putMetric(c, mode, "homeMs", samples.get(samples.size() / 2));
    }

    /** Two minutes after a switch (the home screen settled, caches trimmed). */
    static void measureMemoryNow(Context c, String mode) {
        synchronized (RUN) {
            if (!mode.equals(state(c).optString("mode"))) return;
            try (Adb adb = Adb.connect(c, 10_000, null)) {
                measureMemory(c, adb, mode);
            } catch (Exception e) {
                AppLog.w("memory measurement failed: " + e.getMessage());
            }
        }
    }

    private static void measureMemory(Context c, Adb adb, String mode) throws IOException, JSONException {
        Matcher avail = Pattern.compile("MemAvailable:\\s+(\\d+) kB").matcher(adb.shell("cat /proc/meminfo"));
        if (avail.find()) putMetric(c, mode, "memAvailableMB", Long.parseLong(avail.group(1)) / 1024);
        long pss = 0;
        String home = packageOf(originalHome(state(c)));
        String[] processes = FREE.equals(mode) ? new String[] {PKG + ":home"}
                : home == null ? new String[0] : new String[] {home};
        Pattern total = Pattern.compile("TOTAL PSS:\\s+(\\d+)");
        for (String p : processes) {
            Matcher m = total.matcher(adb.shell("dumpsys meminfo " + p));
            if (m.find()) pss += Long.parseLong(m.group(1));
        }
        if (pss > 0) putMetric(c, mode, "homePssMB", pss / 1024);
    }

    private static void putMetric(Context c, String mode, String key, long value) throws JSONException {
        JSONObject s = state(c);
        JSONObject metrics = s.optJSONObject("metrics");
        if (metrics == null) metrics = new JSONObject();
        JSONObject m = metrics.optJSONObject(mode);
        if (m == null) m = new JSONObject();
        m.put(key, value).put("at", System.currentTimeMillis());
        metrics.put(mode, m);
        s.put("metrics", metrics);
        save(c, s);
    }

    private static JSONObject metrics(JSONObject s, String mode) {
        JSONObject metrics = s.optJSONObject("metrics");
        return metrics == null ? null : metrics.optJSONObject(mode);
    }

    // MARK: - State (a file shared by the two processes; atomic writes)

    static JSONObject state(Context c) {
        synchronized (FILE_LOCK) {
            File f = new File(c.getFilesDir(), "home-state.json");
            if (!f.exists()) return defaults();
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] data = new byte[(int) f.length()];
                int read = 0;
                while (read < data.length) {
                    int n = in.read(data, read, data.length - read);
                    if (n < 0) break;
                    read += n;
                }
                return new JSONObject(new String(data, 0, read, StandardCharsets.UTF_8));
            } catch (IOException | JSONException e) {
                return defaults();
            }
        }
    }

    private static JSONObject defaults() {
        try {
            return new JSONObject().put("mode", ORIGINAL);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    static void save(Context c, JSONObject s) {
        synchronized (FILE_LOCK) {
            File dir = c.getFilesDir();
            File tmp = new File(dir, "home-state.json.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(s.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            } catch (IOException e) {
                AppLog.w("could not save the state: " + e.getMessage());
                return;
            }
            if (!tmp.renameTo(new File(dir, "home-state.json"))) AppLog.w("could not replace the state file");
        }
    }

    static void busy(Context c, String text) {
        JSONObject s = state(c);
        try { s.put("busy", text).put("error", ""); } catch (JSONException ignored) {}
        save(c, s);
    }

    static void done(Context c, String error) {
        JSONObject s = state(c);
        s.remove("busy");
        try { s.put("error", error == null ? "" : error); } catch (JSONException ignored) {}
        save(c, s);
        if (error != null) {
            AppLog.w(error);
            notice(c, error);
        }
    }

    /** A notice for the home screen (a bubble) and the settings screen. Keeps the last 20. */
    static void notice(Context c, String text) {
        AppLog.w("notice: " + text);
        JSONObject s = state(c);
        JSONArray old = s.optJSONArray("notices");
        JSONArray list = new JSONArray();
        try {
            list.put(new JSONObject().put("at", System.currentTimeMillis()).put("text", text));
            if (old != null) for (int i = 0; i < old.length() && list.length() < 20; i++) list.put(old.get(i));
            s.put("notices", list);
        } catch (JSONException ignored) {}
        save(c, s);
    }

    // MARK: - Helpers

    static List<String> stringList(JSONObject s, String key) {
        List<String> out = new ArrayList<>();
        JSONArray a = s.optJSONArray(key);
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    /** The text of the newest notice that was not shown yet (what the switch did), or "" when there is none. */
    static String lastNotice(Context c) {
        JSONArray a = state(c).optJSONArray("notices");
        if (a == null) return "";
        for (int i = 0; i < a.length(); i++) {
            JSONObject notice = a.optJSONObject(i);
            if (notice == null) continue;
            if (notice.optBoolean("seen", false)) continue;
            return notice.optString("text");
        }
        return "";
    }

    /** The notice was shown: it does not come back on the next opening of the home screen. */
    static void markNoticeSeen(Context c, String text) {
        JSONObject s = state(c);
        JSONArray a = s.optJSONArray("notices");
        if (a == null) return;
        boolean changed = false;
        for (int i = 0; i < a.length(); i++) {
            JSONObject notice = a.optJSONObject(i);
            if (notice == null) continue;
            if (!text.equals(notice.optString("text"))) continue;
            try {
                notice.put("seen", true);
                changed = true;
            } catch (JSONException ignored) {}
        }
        if (!changed) return;
        try {
            s.put("notices", a);
        } catch (JSONException ignored) {}
        save(c, s);
    }

    private static List<String> sorted(Set<String> set) {
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }
}
