package com.sunzhe.tvquiz;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class LockActivity extends Activity {
    private Prefs prefs;
    private TextView tvCountdown;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final int REQ_PIN = 3001;
    private boolean done = false;

    private final Runnable updater = new Runnable() {
        @Override public void run() {
            updateCountdown();
            handler.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);
        prefs = new Prefs(this);
        tvCountdown = findViewById(R.id.tvCountdown);
        Button parentBtn = findViewById(R.id.btnParentLock);
        parentBtn.setOnClickListener(v -> startActivityForResult(new Intent(this, PinActivity.class), REQ_PIN));
        parentBtn.requestFocus();
    }

    private void updateCountdown() {
        if (done) return;
        long remainMs = prefs.getLockUntil() - System.currentTimeMillis();
        if (remainMs <= 0) {
            done = true;
            prefs.setLockUntil(0);
            prefs.setWatchedSeconds(0);
            Toast.makeText(this, "休息结束！答对全部6道题就能继续看", Toast.LENGTH_LONG).show();
            // After lock, require a fresh quiz before watching again
            Intent i = new Intent(this, QuizActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
            return;
        }
        long sec = remainMs / 1000;
        tvCountdown.setText(String.format("%02d:%02d", sec / 60, sec % 60));
    }

    @Override protected void onResume() {
        super.onResume();
        WatchService.lockActive = true;
        OverlayGate.hide();
        handler.removeCallbacks(updater);
        handler.post(updater);
    }

    @Override protected void onPause() {
        super.onPause();
        WatchService.lockActive = false;
        handler.removeCallbacks(updater);
    }

    @Override public void onBackPressed() {
        Toast.makeText(this, "休息时间还没到哦", Toast.LENGTH_SHORT).show();
    }

    @Override public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            startActivityForResult(new Intent(this, PinActivity.class), REQ_PIN);
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PIN && res == RESULT_OK) {
            done = true;
            prefs.setLockUntil(0);
            prefs.setWatchedSeconds(0);
            Toast.makeText(this, "家长已解锁", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
