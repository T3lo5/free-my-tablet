package io.freemytablet;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The home screen: the clock at the top, the apps in a grid you swipe through, and the dock at the bottom.
 *
 * Drawn by hand on one Canvas, like the TV's: no allocations while drawing, the icons come from bitmaps made
 * once (HomeApps), and the touch is read here (tap to open, swipe to change page, swipe up for all the apps).
 * The arrows and Enter work too, for the tablet with a keyboard.
 */
public class HomeView extends View implements Choreographer.FrameCallback {
    private final Activity activity;
    private final Context context;
    private final Click click;
    private final Sky.Palette palette = new Sky.Palette();

    private final List<HomeApps.Item> grid = new ArrayList<>();
    private final List<HomeApps.Item> dock = new ArrayList<>();

    private final Paint bg = new Paint();
    private final Paint glow = new Paint();
    private final Paint panel = new Paint();
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint symbol = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint clock = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint sublabel = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint focusRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint notice = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private final RectF iconRect = new RectF();

    private final Spring page = new Spring(0.32f, 1f, 0f);   // the page offset, in pages (0 = the first one)
    private final Spring drawer = new Spring(0.28f, 1f, 0f);  // the "all apps" drawer, 0 = closed, 1 = open

    // Layout
    private int w, h;
    private float density, iconSize, cellW, cellH, gridTop, gridBottom, gridLeft, dockTop, clockSize;
    private int cols = 4, rows = 4, pages = 1;

    // Touch
    private float downX, downY, lastX, lastY;
    private long downAt;
    private boolean dragging, moved;
    private VelocityTracker velocity;
    private int touchSlop;

    // Drawer (all the apps)
    private boolean drawerOpen;
    private float drawerScroll, drawerMaxScroll;

    // State
    private int focus = -1;          // the focused icon (keyboard)
    private String clockText = "", dateText = "";
    private long clockAt;
    private String noticeText = "";
    private long noticeAt;
    private boolean running;
    private long lastFrame;

    public HomeView(Activity a) {
        super(a);
        this.activity = a;
        this.context = a.getApplicationContext();
        click = new Click(context);
        Ui.init(context);
        clock.setTypeface(Ui.thin);
        label.setTypeface(Ui.medium);
        sublabel.setTypeface(Ui.regular);
        focusRing.setStyle(Paint.Style.STROKE);
        focusRing.setStrokeWidth(dp(1.5f));
        notice.setTypeface(Ui.medium);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    // MARK: - Life cycle

    /** Loads the apps and starts the clock. */
    public void resume() {
        reload();
        running = true;
        lastFrame = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void pause() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        click.release();
    }

    public void destroy() {
        pause();
    }

    /** The apps changed (installed, removed, or the dock edited in the settings): read them again. */
    public void reload() {
        final Context app = context;
        new Thread(() -> {
            List<HomeApps.Item> apps = HomeApps.grid(app);
            // The icons are made here, off the UI thread: the first frame draws them, it never builds them.
            for (HomeApps.Item item : apps) HomeApps.icon(app, item);
            List<HomeApps.Item> bottom = HomeApps.dock(app, apps);
            post(() -> {
                grid.clear();
                grid.addAll(apps);
                dock.clear();
                dock.addAll(bottom);
                pages = Math.max(1, (int) Math.ceil((double) grid.size() / Math.max(1, cols * rows)));
                invalidate();
            });
        }, "apps").start();
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        if (lastFrame == 0) lastFrame = frameTimeNanos;
        float dt = Math.min(0.05f, (frameTimeNanos - lastFrame) / 1_000_000_000f);
        lastFrame = frameTimeNanos;
        boolean moving = page.step(dt) | drawer.step(dt);
        long now = System.currentTimeMillis();
        if (now / 60000 != clockAt / 60000) {
            clockAt = now;
            clockText = Ui.clock(now);
            dateText = Ui.date(now);
        }
        if (moving) invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    // MARK: - Layout

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        w = width;
        h = height;
        density = getResources().getDisplayMetrics().density;
        boolean portrait = h >= w;
        float pad = dp(portrait ? 20 : 28);
        clockSize = dp(portrait ? 64 : 52);
        iconSize = dp(portrait ? 56 : 52);
        cellH = iconSize + dp(34);
        dockTop = h - dp(portrait ? 96 : 88);
        gridTop = dp(portrait ? 150 : 120);
        gridBottom = dockTop - dp(12);
        float gridWidth = w - pad * 2;
        gridLeft = pad;
        cols = Math.max(3, Math.min(portrait ? 5 : 8, (int) (gridWidth / (iconSize + dp(28)))));
        cellW = gridWidth / cols;
        rows = Math.max(1, (int) ((gridBottom - gridTop) / cellH));
        pages = Math.max(1, (int) Math.ceil((double) grid.size() / Math.max(1, cols * rows)));
        clock.setTextSize(clockSize);
        label.setTextSize(dp(12));
        sublabel.setTextSize(dp(13));
        notice.setTextSize(dp(13));
        buildBackground();
    }

    /** The background: the sky of the day (two tones) with a soft light on top. */
    private void buildBackground() {
        Sky.sky(palette, System.currentTimeMillis(), Sun.sunrise(context), Sun.sunset(context));
        bg.setShader(new LinearGradient(0, 0, 0, h, palette.color(1), palette.color(0), Shader.TileMode.CLAMP));
        int light = palette.color(2);
        glow.setShader(new RadialGradient(w * 0.5f, h * 0.28f, Math.max(w, h) * 0.8f,
                new int[] {Ui.alpha(light, 0.28f), Ui.alpha(light, 0.06f), Ui.alpha(light, 0f)},
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
    }

    private float dp(float v) {
        return v * density;
    }

    // MARK: - Drawing

    @Override protected void onDraw(Canvas c) {
        c.drawRect(0, 0, w, h, bg);
        c.drawRect(0, 0, w, h, glow);
        if (drawerOpen || drawer.value > 0.001f) {
            drawDrawer(c);
            if (drawer.value < 0.999f) drawHome(c, 1f - drawer.value);
        } else {
            drawHome(c, 1f);
        }
    }

    /** The home screen: clock, grid and dock. `dim` darkens it while the drawer is over it. */
    private void drawHome(Canvas c, float dim) {
        drawClock(c);
        drawGrid(c);
        drawDock(c);
        drawNotice(c);
        if (dim < 0.999f) {
            panel.setColor(Ui.alpha(0xFF000000, 0.45f * (1 - dim)));
            c.drawRect(0, 0, w, h, panel);
        }
    }

    private void drawClock(Canvas c) {
        float x = dp(24);
        float y = dp(portrait() ? 96 : 78);
        clock.setColor(0xFFFFFFFF);
        c.drawText(clockText, x, y, clock);
        sublabel.setColor(0xCCFFFFFF);
        c.drawText(dateText, x, y + dp(28), sublabel);
    }

    /** The grid of apps, on the current page (and the next one, while it is being dragged). */
    private void drawGrid(Canvas c) {
        int perPage = cols * rows;
        float offset = page.value * w;
        for (int p = -1; p <= 1; p++) {
            int current = Math.round(page.value) + p;
            if (current < 0 || current >= pages) continue;
            float shift = offset - current * w;
            if (shift > w || shift < -w) continue;
            for (int i = 0; i < perPage; i++) {
                int index = current * perPage + i;
                if (index >= grid.size()) break;
                HomeApps.Item item = grid.get(index);
                int col = i % cols, row = i / cols;
                float cx = shift + col * cellW + cellW / 2;
                float cy = gridTop + row * cellH + iconSize / 2;
                drawIcon(c, item, cx, cy, index == focus);
                label.setColor(0xE6FFFFFF);
                c.drawText(item.label, cx - measure(item.label, label) / 2, cy + iconSize / 2 + dp(18), label);
            }
        }
    }

    private void drawIcon(Canvas c, HomeApps.Item item, float cx, float cy, boolean focused) {
        Bitmap b = HomeApps.icon(context, item);
        float half = iconSize / 2;
        if (b != null) {
            iconRect.set(cx - half, cy - half, cx + half, cy + half);
            c.drawBitmap(b, null, iconRect, icon);
        }
        if (focused) {
            focusRing.setColor(0xFFFFFFFF);
            tmp.set(cx - half - dp(4), cy - half - dp(4), cx + half + dp(4), cy + half + dp(4));
            c.drawRoundRect(tmp, dp(10), dp(10), focusRing);
        }
    }

    /** The dock at the bottom: the apps the person keeps there, and the door to all the apps. */
    private void drawDock(Canvas c) {
        float pad = dp(16);
        float top = dockTop;
        float height = h - top - dp(10);
        panel.setColor(0x33000000);
        tmp.set(pad, top, w - pad, top + height);
        c.drawRoundRect(tmp, dp(26), dp(26), panel);
        float slot = (w - pad * 2) / (dock.size() + 1);
        for (int i = 0; i < dock.size(); i++) {
            HomeApps.Item item = dock.get(i);
            float cx = pad + slot * i + slot / 2;
            drawIcon(c, item, cx, top + height / 2 - dp(6), false);
        }
        // The door to all the apps (the grid symbol).
        float cx = pad + slot * dock.size() + slot / 2;
        float r = dp(20);
        panel.setColor(0x22FFFFFF);
        tmp.set(cx - r, top + height / 2 - dp(6) - r, cx + r, top + height / 2 - dp(6) + r);
        c.drawRoundRect(tmp, r, r, panel);
        android.graphics.Bitmap bmp = Symbols.get(context, "apps");
        if (bmp != null) {
            float s = dp(22);
            iconRect.set(cx - s / 2, top + height / 2 - dp(6) - s / 2, cx + s / 2, top + height / 2 - dp(6) + s / 2);
            symbol.setColorFilter(new android.graphics.PorterDuffColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN));
            c.drawBitmap(bmp, null, iconRect, symbol);
        }
    }

    /** The "all apps" drawer: every app, in a grid you can scroll. */
    private void drawDrawer(Canvas c) {
        float t = drawer.value;
        c.save();
        c.translate(0, h * (1 - t));
        panel.setColor(0xF20B0F1E);
        c.drawRect(0, 0, w, h, panel);
        sublabel.setColor(0xCCFFFFFF);
        c.drawText(Ui.tr("Todos os apps", "All apps"), dp(24), dp(48), sublabel);
        float top = dp(70), bottom = h - dp(24);
        float cell = Math.max(dp(72), iconSize + dp(34));
        int cols2 = Math.max(3, (int) ((w - dp(40)) / cell));
        float cellW2 = (w - dp(40)) / cols2;
        int rows2 = (int) Math.ceil((double) grid.size() / cols2);
        float contentH = rows2 * cell;
        drawerMaxScroll = Math.max(0, contentH - (bottom - top));
        drawerScroll = Math.max(0, Math.min(drawerScroll, drawerMaxScroll));
        c.save();
        c.clipRect(0, top, w, bottom);
        for (int i = 0; i < grid.size(); i++) {
            int col = i % cols2, row = i / cols2;
            float cx = dp(20) + col * cellW2 + cellW2 / 2;
            float cy = top + row * cell - drawerScroll + cell / 2;
            if (cy < top - cell || cy > bottom + cell) continue;
            drawIcon(c, grid.get(i), cx, cy, false);
            label.setColor(0xE6FFFFFF);
            c.drawText(grid.get(i).label, cx - measure(grid.get(i).label, label) / 2, cy + iconSize / 2 + dp(18), label);
        }
        c.restore();
        c.restore();
    }

    /** The notice from the switch (a bubble at the top, for a few seconds). */
    private void drawNotice(Canvas c) {
        if (noticeText.isEmpty()) return;
        float age = System.currentTimeMillis() - noticeAt;
        if (age > 6000) { noticeText = ""; return; }
        float alpha = age > 4500 ? 1 - (age - 4500) / 1500 : 1;
        panel.setColor(Ui.alpha(0xFF101828, 0.85f * alpha));
        float y = dp(8);
        float tw = measure(noticeText, notice) + dp(32);
        tmp.set((w - tw) / 2, y, (w + tw) / 2, y + dp(40));
        c.drawRoundRect(tmp, dp(20), dp(20), panel);
        notice.setColor(Ui.alpha(0xFFFFFFFF, alpha));
        c.drawText(noticeText, (w - tw) / 2 + dp(16), y + dp(26), notice);
    }

    private float measure(String text, Paint p) {
        return p.measureText(text);
    }

    private boolean portrait() {
        return h >= w;
    }

    // MARK: - Touch

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = lastY = e.getY();
                downAt = System.currentTimeMillis();
                moved = false;
                dragging = false;
                if (velocity == null) velocity = VelocityTracker.obtain();
                velocity.addMovement(e);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (velocity != null) velocity.addMovement(e);
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (!moved && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) moved = true;
                if (moved && !dragging) {
                    if (drawerOpen) {
                        if (Math.abs(dy) > Math.abs(dx)) dragging = true;   // the drawer scrolls
                    } else if (Math.abs(dx) > Math.abs(dy)) {
                        dragging = true;
                        page.velocity = 0;
                    }
                }
                if (dragging) {
                    if (drawerOpen) {
                        drawerScroll -= e.getY() - lastY;
                    } else {
                        float pages1 = page.value - (e.getX() - lastX) / w;
                        page.value = Math.max(-0.3f, Math.min(pages - 1 + 0.3f, pages1));
                        page.target = Math.round(page.value);
                        page.velocity = 0;
                    }
                    invalidate();
                }
                lastX = e.getX();
                lastY = e.getY();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (velocity != null) velocity.computeCurrentVelocity(1000);
                float vx = velocity == null ? 0 : velocity.getXVelocity();
                if (velocity != null) { velocity.recycle(); velocity = null; }
                if (drawerOpen) {
                    if (dragging) {
                        drawerScroll = Math.max(0, Math.min(drawerMaxScroll, drawerScroll));
                    } else if (!moved) {
                        // A tap: on an app it opens, anywhere else it closes.
                        if (hitDrawer(e.getX(), e.getY()) >= 0) openDrawerItem(hitDrawer(e.getX(), e.getY()));
                        else closeDrawer();
                    }
                } else if (dragging) {
                    // The page follows the finger; a flick decides which page it lands on.
                    int target = Math.round(page.value);
                    if (Math.abs(vx) > dp(300)) target = vx < 0 ? Math.round(page.value) + 1 : Math.round(page.value) - 1;
                    target = Math.max(0, Math.min(pages - 1, target));
                    page.target = target;
                    page.velocity = -vx / w;
                } else if (!moved) {
                    tap(e.getX(), e.getY());
                }
                dragging = false;
                return true;
            }
            default:
                return super.onTouchEvent(e);
        }
    }

    /** A tap on the home screen: an app opens, the dock works, the door to all the apps opens. */
    private void tap(float x, float y) {
        int index = hitGrid(x, y);
        if (index >= 0) {
            click.open();
            HomeApps.open(activity, grid.get(index));
            return;
        }
        int dockIndex = hitDock(x, y);
        if (dockIndex >= 0) {
            click.open();
            HomeApps.open(activity, dock.get(dockIndex));
            return;
        }
        if (hitAllApps(x, y)) {
            click.move();
            openDrawer();
            return;
        }
        // A long press on an app opens its page in the settings.
        if (System.currentTimeMillis() - downAt > 600) {
            if (index >= 0) HomeApps.info(activity, grid.get(index));
        }
    }

    /** Which app of the grid is at (x, y), or -1. */
    private int hitGrid(float x, float y) {
        if (y < gridTop || y > gridBottom) return -1;
        int col = (int) ((x - gridLeft) / cellW);
        if (col < 0 || col >= cols) return -1;
        int row = (int) ((y - gridTop) / cellH);
        if (row < 0 || row >= rows) return -1;
        int index = Math.round(page.value) * cols * rows + row * cols + col;
        return index < grid.size() ? index : -1;
    }

    /** Which app of the dock is at (x, y), or -1. */
    private int hitDock(float x, float y) {
        if (y < dockTop) return -1;
        float pad = dp(16);
        float slot = (w - pad * 2) / (dock.size() + 1);
        for (int i = 0; i < dock.size(); i++) {
            float cx = pad + slot * i + slot / 2;
            if (Math.abs(x - cx) <= slot / 2 - dp(8)) return i;
        }
        return -1;
    }

    /** The door to all the apps is the last slot of the dock. */
    private boolean hitAllApps(float x, float y) {
        if (y < dockTop) return false;
        float pad = dp(16);
        float slot = (w - pad * 2) / (dock.size() + 1);
        float cx = pad + slot * dock.size() + slot / 2;
        return Math.abs(x - cx) <= slot / 2 - dp(8);
    }

    // MARK: - The drawer

    private void openDrawer() {
        drawerOpen = true;
        drawer.target = 1;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void closeDrawer() {
        drawerOpen = false;
        drawer.target = 0;
    }

    /** Which app of the drawer is at (x, y), or -1. */
    private int hitDrawer(float x, float y) {
        float top = dp(70), bottom = h - dp(24);
        if (y < top || y > bottom) return -1;
        float cell = Math.max(dp(72), iconSize + dp(34));
        int cols2 = Math.max(3, (int) ((w - dp(40)) / cell));
        float cellW2 = (w - dp(40)) / cols2;
        int col = (int) ((x - dp(20)) / cellW2);
        if (col < 0 || col >= cols2) return -1;
        int row = (int) ((y - top + drawerScroll) / cell);
        int index = row * cols2 + col;
        return index < grid.size() ? index : -1;
    }

    private void openDrawerItem(int index) {
        if (index < 0) return;
        click.open();
        HomeApps.open(activity, grid.get(index));
        closeDrawer();
    }

    // MARK: - Keyboard

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        int perPage = cols * rows;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                if (focus < 0) focus = 0;
                int col = focus % cols, row = focus / cols;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) col = Math.max(0, col - 1);
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) col = Math.min(cols - 1, col + 1);
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) row = Math.max(0, row - 1);
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) row = Math.min(rows - 1, row + 1);
                focus = row * cols + col;
                if (focus >= grid.size()) focus = grid.size() - 1;
                click.move();
                invalidate();
                return true;
            }
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (focus >= 0 && focus < grid.size()) {
                    click.open();
                    HomeApps.open(activity, grid.get(focus));
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (drawerOpen) { closeDrawer(); return true; }
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    // MARK: - Notices

    /** Shows a message at the top of the screen (the switch's results). */
    public void notice(String text) {
        noticeText = text == null ? "" : text;
        noticeAt = System.currentTimeMillis();
        invalidate();
    }
}
