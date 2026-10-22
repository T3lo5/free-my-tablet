package io.freemytablet;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * The home screen: the clock, the apps you use and the quick note. Nothing else.
 *
 * It is not a grid of every app on the tablet (that is what the manufacturers' launchers are, and it is where
 * the noise comes from): there is a row with the apps you picked, the note that goes straight to your Obsidian
 * vault, and a small button for the rest of the apps.
 *
 * Drawn by hand on one Canvas: no allocations while drawing, the icons come from bitmaps made once
 * (HomeApps), and the touch is read here (tap to open, long press for the app's page).
 */
public class HomeView extends View implements Choreographer.FrameCallback {
    private final Activity activity;
    private final Context context;
    private final Click click;
    private final Sky.Palette palette = new Sky.Palette();

    private final List<HomeApps.Item> favorites = new ArrayList<>();

    /** The apps opened lately (the row on top of the list of all the apps). */
    private final List<HomeApps.Item> recents = new ArrayList<>();

    /** The names of the work areas (the row of tabs above the apps). */
    private final List<String> areaNames = new ArrayList<>();
    private final List<RectF> areaRects = new ArrayList<>();
    private int areaCurrent = -1;   // -1 = the main row of apps
    private float tabsY;

    private final Paint bg = new Paint();
    private final Paint glow = new Paint();
    private final Paint panel = new Paint();
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint clock = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint date = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint focusRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private final RectF iconRect = new RectF();

    // Layout
    private int w, h;
    private float density, iconSize, labelSize, clockSize, noteTop, noteH, rowH;
    private int perRow = 5;
    private final RectF noteRect = new RectF();
    private final RectF widgetRect = new RectF();

    /** The height the widget asked for (0 = the one from the settings). */
    private int widgetHeight;

    /** The height we told the widget (so we can tell our own echo from a real request). */
    private int widgetTold;
    private final RectF searchRect = new RectF();

    // All apps (the overlay)
    private boolean allOpen;
    private float allScroll, allMaxScroll;

    // State
    private int focus = -1;
    private String clockText = "", dateText = "";
    private long clockAt;
    private boolean running;
    private boolean animating;
    private long lastFrame;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    public HomeView(Activity a) {
        super(a);
        this.activity = a;
        this.context = a.getApplicationContext();
        click = new Click(context);
        Ui.init(context);
        clock.setTypeface(Ui.thin);
        label.setTypeface(Ui.medium);
        date.setTypeface(Ui.regular);
        hint.setTypeface(Ui.regular);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(dp(1f));
        focusRing.setStyle(Paint.Style.STROKE);
        focusRing.setStrokeWidth(dp(1.5f));
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    // MARK: - Life cycle

    /**
     * Loads the favorites and starts the clock.
     *
     * The screen is not redrawn all the time: the background is still (a gradient, nothing moves), so the only
     * reasons to draw are the clock, an animation that is running, and a message on the screen. A home screen
     * that redraws 60 times a second costs a third of the CPU of the tablet (measured: 37% here, against 0.1%
     * of the stock launcher).
     */
    public void resume() {
        reload();
        running = true;
        handler.post(tick);
    }

    public void pause() {
        running = false;
        handler.removeCallbacks(tick);
        animating = false;
        Choreographer.getInstance().removeFrameCallback(this);
        click.release();
    }

    /** The clock, once a minute. */
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = System.currentTimeMillis();
            clockText = Ui.clock(now);
            dateText = Ui.date(now);
            invalidate();
            // The next minute, a little after it (so the clock does not change a second too early).
            handler.postDelayed(this, 60_000 - (now % 60_000) + 500);
        }
    };

    public void destroy() {
        pause();
    }

    /** The apps changed (installed, removed, or the favorites edited in the settings): read them again. */
    public void reload() {
        final Context app = context;
        new Thread(() -> {
            List<HomeApps.Item> all = HomeApps.all(app);
            // The icons are made here, off the UI thread: the first frame draws them, it never builds them.
            for (HomeApps.Item item : all) HomeApps.icon(app, item);
            List<HomeApps.Item> chosen = HomeApps.favorites(app, all);
            // The work areas: the one that is on the screen takes the place of the main row.
            List<Prefs.Area> areas = Prefs.areas();
            int index = Prefs.area();
            if (index >= 0 && index < areas.size()) {
                chosen = HomeApps.of(areas.get(index).apps, all);
            }
            final List<HomeApps.Item> row = chosen;
            final List<Prefs.Area> list = areas;
            final int current = index;
            post(() -> {
                favorites.clear();
                favorites.addAll(row);
                recents.clear();
                recents.addAll(recentItems());
                layoutWidget();     // the size of the widget may have changed in the settings
                areaNames.clear();
                areaNames.add(Ui.tr("Início", "Home"));
                for (Prefs.Area area : list) areaNames.add(area.name);
                areaCurrent = (current >= 0 && current < list.size()) ? current + 1 : 0;
                setAllApps(all);
                layoutFavorites();
                buildBackground();     // the theme may have changed in the settings
                invalidate();
            });
        }, "apps").start();
    }

    /** Starts the animation loop (it stops by itself when nothing is moving). */
    private void startAnimation() {
        if (animating || !running) return;
        animating = true;
        lastFrame = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        if (lastFrame == 0) lastFrame = frameTimeNanos;
        lastFrame = frameTimeNanos;
        if (noticeVisible()) {   // the message fades away on its own
            invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        } else {
            animating = false;   // nothing moves: the screen rests until something happens
        }
    }

    /** Whether a message is still on the screen (it fades away on its own). */
    private boolean noticeVisible() {
        return !noticeText.isEmpty() && System.currentTimeMillis() - noticeAt < 5000;
    }

    /** The row of apps: the number of columns, balanced (six apps make two rows of three, not five and one). */
    private void layoutFavorites() {
        int count = favorites.size();
        int max = portrait() ? 5 : 7;
        int rows = Math.max(1, (int) Math.ceil(count / (float) max));
        perRow = Math.max(1, (int) Math.ceil(count / (float) rows));
    }

    // MARK: - Layout

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        w = width;
        h = height;
        density = getResources().getDisplayMetrics().density;
        boolean portrait = h >= w;
        float pad = dp(portrait ? 24 : 32);
        clockSize = dp(portrait ? 60 : 52);
        iconSize = dp(portrait ? 60 : 56);
        labelSize = dp(12);
        rowH = iconSize + dp(28);
        noteH = dp(58);
        noteTop = h - (portrait ? dp(132) : dp(104));
        noteRect.set(pad, noteTop, w - pad, noteTop + noteH);
        layoutWidget();   // the slot of the widget
        clock.setTextSize(clockSize);
        label.setTextSize(labelSize);
        date.setTextSize(dp(14));
        hint.setTextSize(dp(15));
        buildBackground();   // the view has its size now
    }

    /** The background: the sky of the day (two tones) with a soft light on top. */
    private void buildBackground() {
        if (w <= 0 || h <= 0) return;   // the view has no size yet (right after the app starts)
        String theme = Prefs.theme();
        if ("read".equals(theme)) {
            Sky.reading(palette);
        } else if ("light".equals(theme)) {
            Sky.paper(palette);   // warm paper, for reading
        } else if ("dark".equals(theme)) {
            Sky.dark(palette);
        } else {
            Sky.sky(palette, System.currentTimeMillis(), Sun.sunrise(context), Sun.sunset(context));
        }
        bg.setShader(new android.graphics.LinearGradient(0, 0, 0, h, palette.color(1), palette.color(0),
                Shader.TileMode.CLAMP));
        int light = palette.color(2);
        float radius = Math.max(1f, Math.max(w, h) * 0.85f);
        glow.setShader(new android.graphics.RadialGradient(w * 0.5f, h * 0.26f, radius,
                new int[] {Ui.alpha(light, 0.26f), Ui.alpha(light, 0.06f), Ui.alpha(light, 0f)},
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
    }

    private float dp(float v) {
        return v * density;
    }

    /** Where the note field is: the activity puts the text field exactly there. */
    RectF noteBounds() {
        return noteRect;
    }

    /** Where the widget goes: the activity puts the widget exactly there. */
    RectF widgetBounds() {
        return widgetRect;
    }

    /** The widget asked for another height (the person resized it in the system). */
    public void setWidgetHeight(int dp) {
        if (dp == widgetTold) return;   // it is repeating what we told it: not a new request
        if (dp == widgetHeight) return;
        widgetHeight = dp;
        layoutWidget();
        invalidate();
    }

    /** Puts the slot back in place (the height of the widget changed). */
    public void layoutWidget() {
        float pad = dp(portrait() ? 24 : 32);
        // The size the person chose, or the one the widget asks for when it is bigger (the widget is not cut).
        int wh = Math.max(Prefs.widgetSize(), widgetHeight);
        widgetRect.set(pad, dp(140), w - pad, dp(140) + dp(wh));
        widgetTold = wh;
    }

    // MARK: - Drawing

    @Override protected void onDraw(Canvas c) {
        c.drawRect(0, 0, w, h, bg);
        c.drawRect(0, 0, w, h, glow);
        drawClock(c);
        if (reading()) {     // the reading mode: the clock and the apps, nothing else
            drawFavorites(c);
            return;
        }
        drawWeather(c);
        drawFavorites(c);
        drawNote(c);
        drawButtons(c);
        drawNotice(c);
        if (allOpen) drawAll(c);
    }

    private void drawClock(Canvas c) {
        float x = dp(24);
        float y = dp(portrait() ? 92 : 76);
        if (reading()) {
            // The reading mode: a small clock, dim, up in the corner.
            clock.setTextSize(dp(22));
            clock.setColor(0x66FFFFFF);
            c.drawText(clockText, x, dp(48), clock);
            clock.setTextSize(clockSize);
            return;
        }
        clock.setColor(Ui.ink());
        c.drawText(clockText, x, y, clock);
        date.setColor(Ui.inkSoft());
        c.drawText(dateText, x, y + dp(26), date);
    }

    /** Whether the theme is the reading mode (a black screen, only the clock and the apps). */
    public boolean reading() {
        return "read".equals(Prefs.theme());
    }

    /** The weather card, at the top right: the symbol, the temperature and the city. */
    private void drawWeather(Canvas c) {
        if (!Prefs.weatherCard()) return;
        float temp = Weather.temperature(context);
        if (temp == 0f) return;
        int code = Weather.code(context);
        String symbol = code < 0 ? "cloud" : Symbols.weather(code, Weather.day(context));
        float y = dp(portrait() ? 88 : 72);
        float x = w - dp(24);
        String text = Math.round(temp) + "°";
        hint.setTextSize(dp(20));
        float tw = hint.measureText(text);
        Bitmap bit = Symbols.get(context, symbol);
        if (bit != null) {
            float s = dp(26);
            iconRect.set(x - tw - s - dp(6), y - s * 0.75f, x - tw - dp(6), y + s * 0.25f);
            symbolPaint.setColorFilter(new android.graphics.PorterDuffColorFilter(Ui.ink(),
                    android.graphics.PorterDuff.Mode.SRC_IN));
            c.drawBitmap(bit, null, iconRect, symbolPaint);
        }
        hint.setColor(Ui.ink());
        c.drawText(text, x - tw, y, hint);
        hint.setTextSize(dp(12));
        String city = Prefs.city();
        if (!city.isEmpty()) {
            hint.setColor(Ui.inkSoft());
            float cw = hint.measureText(city);
            c.drawText(city, x - cw, y + dp(18), hint);
        }
        hint.setTextSize(dp(15));
    }

    /** The row with the apps you use. */
    private void drawFavorites(Canvas c) {
        drawAreas(c);
        if (favorites.isEmpty()) return;
        int rows = (int) Math.ceil(favorites.size() / (float) perRow);
        float top = widgetRect.bottom + dp(16);
        float bottom = noteTop - dp(24);
        float blockH = rows * rowH;
        float startY = top + Math.max(0, (bottom - top - blockH) / 2f);
        float slot = w / perRow;
        for (int i = 0; i < favorites.size(); i++) {
            HomeApps.Item item = favorites.get(i);
            int col = i % perRow, row = i / perRow;
            float cx = slot * col + slot / 2;
            float cy = startY + row * rowH + iconSize / 2;
            drawIcon(c, item, cx, cy, i == focus);
            label.setColor(Ui.ink());
            c.drawText(item.label, cx - label.measureText(item.label) / 2, cy + iconSize / 2 + dp(18), label);
        }
    }

    /** The row of tabs: the work areas (only when there is more than the main one). */
    private void drawAreas(Canvas c) {
        areaRects.clear();
        if (areaNames.size() <= 1) return;
        float y = widgetRect.bottom + dp(18);
        tabsY = y;
        float x = dp(24);
        hint.setTextSize(dp(13));
        for (int i = 0; i < areaNames.size(); i++) {
            String name = areaNames.get(i);
            float tw = hint.measureText(name) + dp(22);
            if (x + tw > w - dp(24)) break;   // it does not fit: the rest is left out
            boolean on = i == areaCurrent;
            panel.setColor(on ? Ui.alpha(Ui.ink(), 0.18f) : Ui.alpha(Ui.ink(), 0.07f));
            RectF r = new RectF(x, y, x + tw, y + dp(26));
            areaRects.add(r);
            c.drawRoundRect(r, dp(13), dp(13), panel);
            hint.setColor(Ui.ink());
            c.drawText(name, x + dp(11), y + dp(17), hint);
            x += tw + dp(8);
        }
        hint.setTextSize(dp(15));
    }

    /** A tap on a tab of the areas: the person changes what is on the screen. */
    private int areaAt(float x, float y) {
        for (int i = 0; i < areaRects.size(); i++) {
            if (areaRects.get(i).contains(x, y)) return i;
        }
        return -1;
    }

    private void drawIcon(Canvas c, HomeApps.Item item, float cx, float cy, boolean focused) {
        Bitmap b = HomeApps.icon(context, item);
        float half = iconSize / 2;
        if (b != null) {
            iconRect.set(cx - half, cy - half, cx + half, cy + half);
            c.drawBitmap(b, null, iconRect, icon);
        }
        if (focused) {
            focusRing.setColor(Ui.ink());
            tmp.set(cx - half - dp(4), cy - half - dp(4), cx + half + dp(4), cy + half + dp(4));
            c.drawRoundRect(tmp, dp(10), dp(10), focusRing);
        }
    }

    /** Called when the person taps the home screen outside the apps (the keyboard goes away). */
    public Runnable onOutsideTap;

    /** Called when the person taps the sync button. */
    public Runnable onSyncTap;

    /** Called when the person holds the home screen (the settings). */
    public Runnable onLongPress;

    /** Called when the list of all the apps opens or closes (the widget and the note step aside). */
    public Runnable onOverlayChanged;

    /** Called when the person taps the search box of the list. */
    public Runnable onSearchTap;

    /** Called when the person taps a work area (the index: -1 = the main row). */
    public AreaTap onAreaTap;

    /** Called when the person taps the button of the note of the day. */
    public Runnable onTodayNote;

    /** The work area the person tapped. */
    public interface AreaTap {
        void area(int index);
    }

    /** Whether the list of all the apps is open. */
    public boolean isOverlayOpen() {
        return allOpen;
    }

    /** The widget and the note step aside while the list of all the apps is open. */
    private void overlayChanged() {
        if (onOverlayChanged != null) onOverlayChanged.run();
    }

    /** The quick note: the panel the text field sits on (the field itself is a real EditText, above this). */
    private void drawNote(Canvas c) {
        panel.setColor(Ui.veil());
        c.drawRoundRect(noteRect, dp(20), dp(20), panel);
        rim.setColor(Ui.rim());
        c.drawRoundRect(noteRect, dp(20), dp(20), rim);
        float cy = noteRect.centerY();
        Bitmap pencil = Symbols.get(context, "edit");
        if (pencil != null) {
            float s = dp(20);
            iconRect.set(noteRect.left + dp(16), cy - s / 2, noteRect.left + dp(16) + s, cy + s / 2);
            symbolPaint.setColorFilter(new android.graphics.PorterDuffColorFilter(Ui.inkFaint(), android.graphics.PorterDuff.Mode.SRC_IN));
            c.drawBitmap(pencil, null, iconRect, symbolPaint);
        }
    }

    /** The small buttons at the bottom: the sync (left) and all the apps (right). */
    private void drawButtons(Canvas c) {
        drawRoundButton(c, dp(24) + dp(22), h - dp(24) - dp(22), "sync");
        drawRoundButton(c, w / 2f, h - dp(24) - dp(22), "note");   // the note of the day
        drawRoundButton(c, w - dp(24) - dp(22), h - dp(24) - dp(22), "apps");
    }

    /** The button of the note of the day (in the middle of the row of buttons). */
    private boolean hitTodayButton(float x, float y) {
        float r = dp(22);
        float cx = w / 2f, cy = h - dp(24) - r;
        return Math.abs(x - cx) <= r && Math.abs(y - cy) <= r;
    }

    private void drawRoundButton(Canvas c, float cx, float cy, String symbol) {
        float r = dp(22);
        panel.setColor(Ui.veil());
        tmp.set(cx - r, cy - r, cx + r, cy + r);
        c.drawRoundRect(tmp, r, r, panel);
        rim.setColor(Ui.rim());
        c.drawRoundRect(tmp, r, r, rim);
        Bitmap bit = Symbols.get(context, symbol);
        if (bit == null) return;
        float s = dp(20);
        iconRect.set(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
        symbolPaint.setColorFilter(new android.graphics.PorterDuffColorFilter(0xFFFFFFFF,
                android.graphics.PorterDuff.Mode.SRC_IN));
        c.drawBitmap(bit, null, iconRect, symbolPaint);
    }

    /** All the apps, over the home screen (a grid you can scroll). */
    private void drawAll(Canvas c) {
        panel.setColor(0xFF0B0F1E);
        c.drawRect(0, 0, w, h, panel);
        searchRect.set(dp(20), dp(16), w - dp(20), dp(16) + dp(44));
        panel.setColor(0x22FFFFFF);
        c.drawRoundRect(searchRect, dp(22), dp(22), panel);
        Bitmap glass = Symbols.get(context, "search");
        if (glass != null) {
            float s = dp(20);
            iconRect.set(searchRect.left + dp(12), searchRect.centerY() - s / 2,
                    searchRect.left + dp(12) + s, searchRect.centerY() + s / 2);
            symbolPaint.setColorFilter(new android.graphics.PorterDuffColorFilter(0x99FFFFFF,
                    android.graphics.PorterDuff.Mode.SRC_IN));
            c.drawBitmap(glass, null, iconRect, symbolPaint);
        }
        hint.setColor(0x99FFFFFF);
        c.drawText(filter.isEmpty() ? Ui.tr("Buscar app", "Search an app") : filter,
                searchRect.left + dp(42), searchRect.centerY() + dp(5), hint);
        float bottom = h - dp(24);
        // The apps opened lately, in a row on top.
        float recentH = 0;
        if (!recents.isEmpty() && filter.isEmpty()) {
            recentH = iconSize + dp(26);
            hint.setColor(0x99FFFFFF);
            c.drawText(Ui.tr("Recentes", "Recent"), dp(20), dp(74) + dp(14), hint);
            float slot = (w - dp(40)) / Math.min(5, recents.size());
            for (int i = 0; i < Math.min(5, recents.size()); i++) {
                float cx = dp(20) + slot * i + slot / 2;
                drawIcon(c, recents.get(i), cx, dp(74) + dp(30) + iconSize / 2, false);
            }
        }
        float top = gridTop();
        float cell = Math.max(dp(72), iconSize + dp(34));
        int cols = Math.max(3, (int) ((w - dp(40)) / cell));
        float cellW = (w - dp(40)) / cols;
        List<HomeApps.Item> all = filter.isEmpty() ? allApps : filtered;
        int rows = (int) Math.ceil((double) all.size() / cols);
        float contentH = rows * cell;
        allMaxScroll = Math.max(0, contentH - (bottom - top));
        allScroll = Math.max(0, Math.min(allScroll, allMaxScroll));
        c.save();
        c.clipRect(0, top, w, bottom);
        for (int i = 0; i < all.size(); i++) {
            int col = i % cols, row = i / cols;
            float cx = dp(20) + col * cellW + cellW / 2;
            float cy = top + row * cell - allScroll + cell / 2;
            if (cy < top - cell || cy > bottom + cell) continue;
            drawIcon(c, all.get(i), cx, cy, false);
            label.setColor(0xE6FFFFFF);
            c.drawText(all.get(i).label, cx - label.measureText(all.get(i).label) / 2, cy + iconSize / 2 + dp(18), label);
        }
        c.restore();
    }

    private List<HomeApps.Item> allApps = new ArrayList<>();
    private final List<HomeApps.Item> filtered = new ArrayList<>();
    private String filter = "";

    /** The text typed in the search box: it filters the list of all the apps. */
    public void setFilter(String text) {
        filter = text == null ? "" : text.trim().toLowerCase(java.util.Locale.getDefault());
        filtered.clear();
        for (HomeApps.Item item : allApps) {
            if (filter.isEmpty() || item.label.toLowerCase(java.util.Locale.getDefault()).contains(filter)) filtered.add(item);
        }
        invalidate();
    }

    /** The apps opened lately (the ones that are still installed). */
    private List<HomeApps.Item> recentItems() {
        List<HomeApps.Item> out = new ArrayList<>();
        for (String pkg : Prefs.recent()) {
            for (HomeApps.Item item : allApps) {
                if (item.pkg.equals(pkg)) {
                    out.add(item);
                    break;
                }
            }
        }
        return out;
    }

    /** Where the grid of the apps starts: under the search box and under the row of the recents. */
    private float gridTop() {
        float top = dp(74);
        if (filter.isEmpty() && !recents.isEmpty()) top += iconSize + dp(26);
        return top;
    }

    /** Whether the list is being filtered. */
    public boolean isFiltering() {
        return !filter.isEmpty();
    }

    /** Where the search box is (the activity puts the text field exactly there). */
    RectF searchBounds() {
        return searchRect;
    }

    /** The list of every app (the overlay needs it; it is loaded with the favorites). */
    public void setAllApps(List<HomeApps.Item> all) {
        allApps = all;
    }

    private boolean portrait() {
        // The tablet's own orientation, not the view's shape: with the keyboard open the window is shorter than
        // it is wide, and the screen would change the layout for nothing.
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) return true;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) return false;
        return h >= w;
    }

    // MARK: - Touch

    private float downX, downY, lastY;
    private long downAt;
    private boolean moved;

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = lastY = e.getY();
                downAt = System.currentTimeMillis();
                moved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(e.getY() - downY) > dp(10)) moved = true;
                if (allOpen && moved) {
                    allScroll -= e.getY() - lastY;
                    allScroll = Math.max(0, Math.min(allMaxScroll, allScroll));
                    invalidate();
                }
                lastY = e.getY();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (allOpen) {
                    if (!moved) tapAll(e.getX(), e.getY());
                } else if (!moved) {
                    // A long press on the empty screen: the settings (like every launcher's wallpaper).
                    if (System.currentTimeMillis() - downAt > 550 && hitFavorites(e.getX(), e.getY()) < 0
                            && onLongPress != null) {
                        onLongPress.run();
                        return true;
                    }
                    tap(e.getX(), e.getY());
                }
                return true;
            }
            default:
                return super.onTouchEvent(e);
        }
    }

    /** A tap on the home screen: an app opens, the all-apps button opens the list. */
    private void tap(float x, float y) {
        int area = areaAt(x, y);
        if (area >= 0) {
            click.move();
            if (onAreaTap != null) onAreaTap.area(area - 1);   // -1 = the main row
            return;
        }
        int index = hitFavorites(x, y);
        if (index >= 0) {
            if (System.currentTimeMillis() - downAt > 550) {
                HomeApps.info(activity, favorites.get(index));    // a long press: the app's own page
                return;
            }
            click.open();
            HomeApps.open(activity, favorites.get(index));
            return;
        }
        if (hitTodayButton(x, y)) {
            click.move();
            if (onTodayNote != null) onTodayNote.run();
            return;
        }
        if (hitAllButton(x, y)) {
            click.move();
            allOpen = true;
            allScroll = 0;
            invalidate();
            startAnimation();
            overlayChanged();
            return;
        }
        if (hitSyncButton(x, y)) {
            click.move();
            if (onSyncTap != null) onSyncTap.run();
            return;
        }
        if (onOutsideTap != null) onOutsideTap.run();
    }

    /** The sync button, at the bottom left. */
    private boolean hitSyncButton(float x, float y) {
        float r = dp(22);
        float cx = dp(24) + r, cy = h - dp(24) - r;
        return Math.abs(x - cx) <= r && Math.abs(y - cy) <= r;
    }

    /** Which favorite is at (x, y), or -1. */
    private int hitFavorites(float x, float y) {
        if (favorites.isEmpty()) return -1;
        int rows = (int) Math.ceil(favorites.size() / (float) perRow);
        float top = widgetRect.bottom + dp(16);
        float bottom = noteTop - dp(24);
        float blockH = rows * rowH;
        float startY = top + Math.max(0, (bottom - top - blockH) / 2f);
        float slot = w / perRow;
        int col = (int) (x / slot);
        if (col < 0 || col >= perRow) return -1;
        int row = (int) ((y - startY) / rowH);
        if (row < 0 || row >= rows) return -1;
        int index = row * perRow + col;
        return index < favorites.size() ? index : -1;
    }

    private boolean hitAllButton(float x, float y) {
        float r = dp(22);
        float cx = w - dp(24) - r, cy = h - dp(24) - r;
        return Math.abs(x - cx) <= r && Math.abs(y - cy) <= r;
    }

    /** A tap in the list of all the apps. */
    private void tapAll(float x, float y) {
        if (searchRect.contains(x, y)) {
            if (onSearchTap != null) onSearchTap.run();
            return;
        }
        float top = gridTop(), bottom = h - dp(24);
        // The row of the recents: the app opens like the others.
        if (!recents.isEmpty() && filter.isEmpty() && y >= dp(74) && y < top) {
            float slot = (w - dp(40)) / Math.min(5, recents.size());
            int at = (int) ((x - dp(20)) / slot);
            if (at >= 0 && at < Math.min(5, recents.size())) {
                click.open();
                HomeApps.open(activity, recents.get(at));
                allOpen = false;
                invalidate();
                startAnimation();
                overlayChanged();
                return;
            }
            allOpen = false;
            invalidate();
            return;
        }
        if (y < top || y > bottom) { allOpen = false; invalidate(); return; }
        float cell = Math.max(dp(72), iconSize + dp(34));
        int cols = Math.max(3, (int) ((w - dp(40)) / cell));
        float cellW = (w - dp(40)) / cols;
        int col = (int) ((x - dp(20)) / cellW);
        if (col < 0 || col >= cols) return;
        int row = (int) ((y - top + allScroll) / cell);
        int index = row * cols + col;
        if (index < 0 || index >= allApps.size()) return;
        click.open();
        HomeApps.open(activity, allApps.get(index));
        allOpen = false;
        invalidate();
        startAnimation();
        overlayChanged();
    }

    /** Closes the list of all the apps (the back button). */
    public boolean closeAll() {
        if (!allOpen) return false;
        allOpen = false;
        invalidate();
        startAnimation();
        overlayChanged();
        return true;
    }

    /** Shows a message at the bottom of the screen, for a few seconds (the note was saved, the switch did it). */
    public void notice(String text) {
        noticeText = text == null ? "" : text;
        noticeAt = System.currentTimeMillis();
        invalidate();
        startAnimation();   // the message fades away
    }

    /** The message at the bottom. */
    private void drawNotice(Canvas c) {
        if (noticeText.isEmpty()) return;
        long age = System.currentTimeMillis() - noticeAt;
        if (age > 5000) { noticeText = ""; return; }
        float alpha = age > 4000 ? 1 - (age - 4000) / 1000 : 1;
        float tw = hint.measureText(noticeText) + dp(32);
        float x = (w - tw) / 2, y = noteRect.top - dp(52);
        panel.setColor(Ui.alpha(0xFF101828, 0.9f * alpha));
        tmp.set(x, y, x + tw, y + dp(38));
        c.drawRoundRect(tmp, dp(19), dp(19), panel);
        hint.setColor(Ui.alpha(0xFFFFFFFF, alpha));   // the panel is dark in every theme
        hint.setTextSize(dp(13));
        c.drawText(noticeText, x + dp(16), y + dp(24), hint);
        hint.setTextSize(dp(15));
    }

    private String noticeText = "";
    private long noticeAt;

    // MARK: - Keyboard

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                if (focus < 0) focus = 0;
                int per = perRow;
                int col = focus % per, row = focus / per;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) col = Math.max(0, col - 1);
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) col = Math.min(per - 1, col + 1);
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) row = Math.max(0, row - 1);
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) row = Math.min(1, row + 1);
                focus = row * per + col;
                if (focus >= favorites.size()) focus = favorites.size() - 1;
                click.move();
                invalidate();
                return true;
            }
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (focus >= 0 && focus < favorites.size()) {
                    click.open();
                    HomeApps.open(activity, favorites.get(focus));
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                // The home screen never leaves: back closes the list of apps, or does nothing.
                closeAll();
                return true;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }
}
