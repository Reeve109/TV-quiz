package com.sunzhe.tvquiz;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private Prefs prefs;
    private TextView tvStatus, tvInfo;
    private static final int REQ_PIN_SETTINGS = 1001;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);
        // Online question bank: use cached copy immediately, refresh in background.
        // Any failure silently falls back to the built-in bank.
        QuestionBank.init(this);
        QuestionFetcher.refreshAsync(this);
        tvStatus = findViewById(R.id.tvStatus);
        tvInfo = findViewById(R.id.tvInfo);

        Button btnStart = findViewById(R.id.btnStart);
        Button btnQuizNow = findViewById(R.id.btnQuizNow);
        Button btnParent = findViewById(R.id.btnParent);

        btnStart.setOnClickListener(v -> {
            prefs.setMonitoring(true);
            startWatchService();
            Toast.makeText(this, "开始计时啦，看满" + prefs.getWatchMinutes() + "分钟会出题", Toast.LENGTH_LONG).show();
            refresh();
            maybeRequestOverlay();
        });
        btnQuizNow.setOnClickListener(v -> {
            Intent i = new Intent(this, QuizActivity.class);
            i.putExtra("practice", true);
            startActivity(i);
        });
        btnParent.setOnClickListener(v -> {
            Intent i = new Intent(this, PinActivity.class);
            startActivityForResult(i, REQ_PIN_SETTINGS);
        });
        btnStart.requestFocus();

        // If already locked, go straight to lock screen
        if (prefs.isLocked()) {
            startActivity(new Intent(this, LockActivity.class));
        } else if (prefs.isMonitoring()) {
            startWatchService();
        }
    }

    /**
     * Android 10+ silently blocks background apps from starting an Activity, so
     * when the watch time is up while the child is in another app, the quiz can
     * only be forced to the front through the overlay ("draw over other apps")
     * permission. Ask for it once, when the parent presses Start — without it
     * the forced popup cannot work on modern Android TV.
     */
    private void maybeRequestOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this,
                    "还需要允许「显示在其他应用上层」，到时间才能强制弹出答题页。正在打开设置，请把本应用的开关打开，然后按返回键回来。",
                    Toast.LENGTH_LONG).show();
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); }
                catch (Exception ignored) {}
            }
        }
    }

    private boolean overlayGranted() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    public void startWatchService() {
        Intent svc = new Intent(this, WatchService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        if (prefs == null) return;
        long limit = (long) prefs.getWatchMinutes() * 60L;
        long watched = prefs.getWatchedSeconds();
        long remain = Math.max(0, limit - watched);
        String status;
        if (prefs.isLocked()) {
            long sec = (prefs.getLockUntil() - System.currentTimeMillis()) / 1000;
            status = "正在休息中，还剩 " + (sec / 60) + "分" + (sec % 60) + "秒";
        } else {
            status = "还可以看 " + (remain / 60) + "分" + (remain % 60) + "秒";
        }
        tvStatus.setText(status);
        tvInfo.setText("当前：" + prefs.getGrade() + "年级 · 每轮看" + prefs.getWatchMinutes()
                + "分钟 · 答错休息" + prefs.getLockMinutes() + "分钟 · 计时"
                + (prefs.isMonitoring() ? "已开启" : "未开启")
                + " · 强制弹窗权限" + (overlayGranted() ? "已开启" : "未开启（点「开始看电视」按提示开启，否则到时间弹不出来）"));
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PIN_SETTINGS && res == RESULT_OK) {
            startActivity(new Intent(this, ParentSettingsActivity.class));
        }
    }
}
