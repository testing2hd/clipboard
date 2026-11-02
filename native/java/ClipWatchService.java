package com.linkrouter.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.json.JSONObject;

/**
 * Background clipboard detection.
 *
 * Android only lets the focused app read the clipboard, but the system logs
 * "Denying clipboard access to <our package>" on every copy while we hold a clipboard listener.
 * We read that log line through Shizuku, then show a small focusable overlay window.
 * Once the overlay has focus we are allowed to read the clipboard and draw the popup in it.
 */
public class ClipWatchService extends Service {
    public static volatile boolean running = false;
    private static final String CH = "watch";
    private static final int NID = 1;

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean alive;
    private volatile Process proc;
    private volatile String lastErr = "";
    private FocusRoot popup;
    private long lastTrigger = 0, lastUrlTime = 0;
    private String lastUrl = "";
    private final ClipboardManager.OnPrimaryClipChangedListener noop = () -> { };

    /** Overlay root that reports when its window gets focus. */
    private static class FocusRoot extends FrameLayout {
        Runnable onFocus, onBack;
        boolean fired;
        FocusRoot(Context c) { super(c); }

        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
            super.onWindowFocusChanged(hasFocus);
            if (hasFocus && !fired && onFocus != null) { fired = true; onFocus.run(); }
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_UP && onBack != null) onBack.run();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            try {
                JSONObject c = Config.load(this);
                c.put("autoDetect", false);
                Config.save(this, c);
            } catch (Exception ignored) { }
            Config.setStatus(this, "Off");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running) return START_STICKY;

        try {
            NotificationChannel ch = new NotificationChannel(CH, "Link watcher", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
            Notification n = buildNotification("Starting...");
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NID, n);
            }
        } catch (Throwable t) {
            Config.setStatus(this, "Could not start: " + t.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }

        running = true;
        alive = true;
        try {
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).addPrimaryClipChangedListener(noop);
        } catch (Throwable ignored) { }
        new Thread(this::watchLoop, "clip-watch").start();
        return START_STICKY;
    }

    private Notification buildNotification(String text) {
        Intent stop = new Intent(this, ClipWatchService.class).setAction("STOP");
        PendingIntent pi = PendingIntent.getService(this, 0, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Link Router")
                .setContentText(text)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Stop", pi).build())
                .build();
    }

    private void status(String text) {
        Config.setStatus(this, text);
        try { getSystemService(NotificationManager.class).notify(NID, buildNotification(text)); }
        catch (Throwable ignored) { }
    }

    private Process openLogcat() throws Exception {
        if (!ShizukuShell.ready()) return null;
        return ShizukuShell.newProcess(new String[]{"logcat", "-b", "main", "-b", "system", "-T", "1",
                "-v", "brief", "-e", "clipboard access to"});
    }

    private void watchLoop() {
        String pkg = getPackageName();
        while (alive) {
            try {
                proc = openLogcat();
                if (proc == null) {
                    status("Waiting for Shizuku. Start it and authorize Link Router.");
                } else {
                    status("Watching. Copy a video link to test.");
                    final Process p = proc;
                    lastErr = "";
                    new Thread(() -> {
                        try (BufferedReader e = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                            String l;
                            while ((l = e.readLine()) != null) lastErr = l;
                        } catch (Throwable ignored) { }
                    }).start();
                    long start = SystemClock.elapsedRealtime();
                    BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    while (alive && (line = r.readLine()) != null) {
                        if (SystemClock.elapsedRealtime() - start < 800) continue; // replayed old line
                        if (line.contains(pkg)) main.post(this::trigger);
                    }
                    if (alive) status("Log reader stopped" + (lastErr.isEmpty() ? "" : ": " + lastErr) + ". Retrying...");
                }
            } catch (Throwable t) {
                status("Log error: " + t.getMessage());
            } finally {
                try { if (proc != null) proc.destroy(); } catch (Throwable ignored) { }
                proc = null;
            }
            try { Thread.sleep(4000); } catch (InterruptedException e) { return; }
        }
    }

    // ---------- popup ----------

    private void trigger() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastTrigger < 700 || popup != null) return;
        lastTrigger = now;
        if (!Settings.canDrawOverlays(this)) {
            status("Copy detected, but 'Display over other apps' is off.");
            return;
        }
        final FocusRoot root = new FocusRoot(this);
        root.onBack = this::dismissPopup;
        root.onFocus = () -> root.postDelayed(() -> readAndShow(root, 0), 150);
        try {
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(root, p);
            popup = root;
        } catch (Throwable t) {
            status("Copy detected, but the popup window failed: " + t.getMessage());
            return;
        }
        main.postDelayed(() -> {
            if (popup == root && !root.fired) {
                status("Copy detected, but the popup window never got focus.");
                dismissPopup();
            }
        }, 2500);
    }

    private void readAndShow(FocusRoot root, int attempt) {
        if (popup != root) return;
        String text = null;
        try {
            ClipData d = ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).getPrimaryClip();
            if (d != null && d.getItemCount() > 0) {
                CharSequence cs = d.getItemAt(0).coerceToText(this);
                text = cs == null ? null : cs.toString();
            }
        } catch (Throwable ignored) { }
        if (text == null) {
            if (attempt < 3) { root.postDelayed(() -> readAndShow(root, attempt + 1), 250); return; }
            status("Copy detected, but the clipboard could not be read.");
            dismissPopup();
            return;
        }
        String url = Config.extractUrl(text);
        if (url == null || !Config.matches(url)) {
            status("Copy detected, but it is not a supported video link.");
            dismissPopup();
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (url.equals(lastUrl) && now - lastUrlTime < 2500) { dismissPopup(); return; }
        lastUrl = url;
        lastUrlTime = now;
        status("Watching. Last popup: " + url);
        PopupUi.build(this, root, Config.load(this), url, this::dismissPopup);
    }

    private void dismissPopup() {
        try {
            if (popup != null) ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(popup);
        } catch (Throwable ignored) { }
        popup = null;
    }

    @Override
    public void onDestroy() {
        alive = false;
        running = false;
        dismissPopup();
        try { if (proc != null) proc.destroy(); } catch (Throwable ignored) { }
        try { ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).removePrimaryClipChangedListener(noop); }
        catch (Throwable ignored) { }
        if (!Config.getStatus(this).startsWith("Could not")) Config.setStatus(this, "Off");
        super.onDestroy();
    }
}
