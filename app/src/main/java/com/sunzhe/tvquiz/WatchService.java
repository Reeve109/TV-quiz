package com.sunzhe.tvquiz;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

public class WatchService extends Service {
    public static boolean quizActive = false;
    public static boolean lockActive = false;

    private static final String ALERT_CHANNEL = "quiz_alert";
    private static final int ALERT_NOTIFICATION_ID = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private long lastAlertAttemptMs = 0;
    /** Wall-clock baseline for watch-time accumulation (see consumeElapsedSeconds). */
    private long lastTickMs = 0;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            tick();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        createChannels();
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "watch")
                : new Notification.Builder(this);
        Notification n = b.setContentTitle("看电视答题正在计时")
                .setContentText("看满时间会自动出题")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(pi)
                .build();
        startForeground(1, n);
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            nm.createNotificationChannel(
                    new NotificationChannel("watch", "看电视计时", NotificationManager.IMPORTANCE_LOW));
            nm.createNotificationChannel(
                    new NotificationChannel(ALERT_CHANNEL, "答题提醒", NotificationManager.IMPORTANCE_HIGH));
        }
    }

    /**
     * Watch time must be accumulated from REAL elapsed wall-clock time, not by
     * counting ticker invocations. The old code added +1s on every tick() call,
     * and onStartCommand() runs tick() immediately — so anything that (re)started
     * the service instantly burned a second: pressing OK on the main screen's
     * focused "开始看电视" button, or simply returning to MainActivity while
     * monitoring was on. With the timestamp baseline below, an immediate extra
     * tick adds 0s, and only genuinely elapsed seconds are counted.
     *
     * Returns the whole seconds elapsed since the previous counting tick and
     * advances the baseline, keeping the sub-second remainder for next time.
     */
    private int consumeElapsedSeconds() {
        long now = System.currentTimeMillis();
        if (lastTickMs == 0) { lastTickMs = now; return 0; }
        long elapsed = now - lastTickMs;
        if (elapsed < 1000) return 0;
        long secs = elapsed / 1000;
        lastTickMs += secs * 1000L;
        return (int) secs;
    }

    /** Drop accumulated time (used in states where watch time must not accrue). */
    private void resetTickBaseline() { lastTickMs = System.currentTimeMillis(); }

    private void tick() {
        if (prefs == null) prefs = new Prefs(this);
        // Parent is entering a PIN (from quiz/lock/main): never launch quiz/lock over it.
        if (PinActivity.active) { resetTickBaseline(); return; }

        // Quiz or lock screen is already in front: make sure no gate/alert lingers.
        if (quizActive || lockActive) {
            OverlayGate.hide();
            cancelAlert();
            resetTickBaseline();
            return;
        }

        // Locked state takes priority
        if (prefs.isLocked()) {
            ensureAlert(LockActivity.class, true);
            resetTickBaseline();
            return;
        }
        // If lock just expired, reset watched so a new quiz round can start after lock period
        if (prefs.getLockUntil() != 0 && prefs.getLockUntil() <= System.currentTimeMillis()) {
            prefs.setLockUntil(0);
            prefs.setWatchedSeconds(0);
        }

        long limitSec = (long) prefs.getWatchMinutes() * 60L;
        if (prefs.getWatchedSeconds() >= limitSec) {
            ensureAlert(QuizActivity.class, false);
            resetTickBaseline();
            return;
        }

        // Nothing is due any more (e.g. parent reset the timer): clear any gate.
        if (OverlayGate.isShown()) {
            OverlayGate.hide();
            cancelAlert();
        }

        // Count only when screen is on / interactive, using real elapsed time.
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean screenOn = pm == null || pm.isInteractive();
        if (screenOn) {
            int secs = consumeElapsedSeconds();
            if (secs > 0) prefs.setWatchedSeconds(prefs.getWatchedSeconds() + secs);
        } else {
            resetTickBaseline();
        }
    }

    /**
     * Bring the quiz/lock screen to the front, using every channel Android allows:
     * 1) Direct startActivity — works when our app is in the foreground and on
     *    some TV OEM builds, but is SILENTLY BLOCKED on Android 10+ when the app
     *    is in the background (the normal case: child watching in another app).
     * 2) Full-screen-intent notification — fallback where the system allows it.
     * 3) Full-screen overlay gate (OverlayGate) — checked ~1.5s later; if the
     *    Activity still isn't showing, the overlay covers the screen and its
     *    button opens the quiz. Requires the overlay permission, which
     *    MainActivity now requests when the parent starts monitoring.
     * Attempts are throttled so we don't repost the notification every second.
     */
    private void ensureAlert(final Class<?> cls, final boolean isLock) {
        long now = System.currentTimeMillis();
        if (OverlayGate.isShown()) return;
        if (now - lastAlertAttemptMs < 8000) return;
        lastAlertAttemptMs = now;

        launch(cls);
        postFullScreenAlert(cls, isLock);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                boolean active = isLock ? lockActive : quizActive;
                if (!active && !PinActivity.active && !OverlayGate.isShown()) {
                    OverlayGate.show(WatchService.this, cls, isLock);
                }
            }
        }, 1500);
    }

    private void launch(Class<?> cls) {
        Intent i = new Intent(this, cls);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try { startActivity(i); } catch (Exception ignored) {}
    }

    private void postFullScreenAlert(Class<?> cls, boolean isLock) {
        try {
            Intent i = new Intent(this, cls);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, isLock ? 21 : 20, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, ALERT_CHANNEL)
                    : new Notification.Builder(this);
            Notification n = b.setContentTitle(isLock ? "休息时间还没到" : "⏰ 时间到啦，先答题！")
                    .setContentText(isLock ? "点这里查看休息倒计时" : "点这里开始答题，全部答对才能继续看")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentIntent(pi)
                    .setFullScreenIntent(pi, true)
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .build();
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(ALERT_NOTIFICATION_ID, n);
        } catch (Exception ignored) {}
    }

    private void cancelAlert() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(ALERT_NOTIFICATION_ID);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);
        OverlayGate.hide();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
