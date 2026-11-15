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

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private int tickCount = 0;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            tick();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        createChannel();
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

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(
                    new NotificationChannel("watch", "看电视计时", NotificationManager.IMPORTANCE_LOW));
        }
    }

    private void tick() {
        if (prefs == null) prefs = new Prefs(this);
        tickCount++;
        // Parent is entering a PIN (from quiz/lock/main): never launch quiz/lock over it.
        if (PinActivity.active) return;

        // Locked state takes priority
        if (prefs.isLocked()) {
            if (!lockActive && !quizActive) launch(LockActivity.class);
            return;
        }
        // If lock just expired, reset watched so a new quiz round can start after lock period
        // Lock expiry is handled in LockActivity; here if lockUntil in past and >0, clear it.
        if (prefs.getLockUntil() != 0 && prefs.getLockUntil() <= System.currentTimeMillis()) {
            prefs.setLockUntil(0);
            prefs.setWatchedSeconds(0);
        }

        long limitSec = (long) prefs.getWatchMinutes() * 60L;
        if (prefs.getWatchedSeconds() >= limitSec) {
            if (!quizActive && !lockActive) launch(QuizActivity.class);
            return;
        }

        // Count only when screen is on / interactive
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean screenOn = pm == null || pm.isInteractive();
        if (screenOn && !quizActive && !lockActive) {
            prefs.setWatchedSeconds(prefs.getWatchedSeconds() + 1);
        }
    }

    private void launch(Class<?> cls) {
        Intent i = new Intent(this, cls);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try { startActivity(i); } catch (Exception ignored) {}
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
