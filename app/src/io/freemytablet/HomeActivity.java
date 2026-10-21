package io.freemytablet;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;

/**
 * The Free My Tablet home screen (Android's "Home" role in Free My Tablet mode). It runs in the :home process: if it
 * crashes, the safety net in the main process keeps working. On the 3rd crash within 5 min, the Watchdog gives the
 * home screen back to the tablet's original one.
 */
public class HomeActivity extends Activity {
    private HomeView home;
    private int prefsVersion = Prefs.version;   // settings changed while we were away: start over
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int lastNotices = -1;
    private boolean running;

    /** Looks for the notices the switch leaves behind (the results of turning things on and off). */
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!running) return;
            JSONObject state = HomeMode.state(HomeActivity.this);
            List<String> notices = HomeMode.stringList(state, "notices");
            if (!notices.isEmpty()) {
                int id = notices.get(0).hashCode();
                if (lastNotices != id) {
                    lastNotices = id;
                    if (home != null) home.notice(notices.get(0));
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
        setContentView(home);
        getWindow().setBackgroundDrawable(null);
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefsVersion != Prefs.version) {
            prefsVersion = Prefs.version;
            if (home != null) home.reload();
        }
        if (home != null) home.resume();
        running = true;
        handler.post(poll);
    }

    @Override protected void onPause() {
        running = false;
        handler.removeCallbacks(poll);
        if (home != null) home.pause();
        super.onPause();
    }

    /** Home button pressed on the home screen: back to the entry state (the first page, no drawer). */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (home != null) home.reload();
    }

    @Override protected void onDestroy() {
        if (home != null) home.destroy();
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (home != null && home.onKeyDown(event.getKeyCode(), event)) return true;
        return super.dispatchKeyEvent(event);
    }
}
