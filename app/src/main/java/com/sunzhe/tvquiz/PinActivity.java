package com.sunzhe.tvquiz;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** On-screen numeric PIN pad, remote D-pad friendly. Also accepts physical number keys. */
public class PinActivity extends Activity {
    /** While the PIN pad is up, WatchService must not relaunch quiz/lock over it. */
    public static volatile boolean active = false;

    private StringBuilder entered = new StringBuilder();
    private TextView tvDots;

    @Override protected void onResume() { super.onResume(); active = true; }
    @Override protected void onPause() { super.onPause(); active = false; }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // NOTE: sizes here must be dp, not raw px — the old version used raw px
        // for the keypad (200x110px buttons), which rendered tiny/overflowing
        // depending on the TV's density. The root is also scrollable so the
        // keypad can never be cut off on low-height screens.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(Color.parseColor("#16213A"));

        TextView title = new TextView(this);
        title.setText("请输入家长PIN码");
        title.setTextColor(Color.WHITE); title.setTextSize(30); title.setGravity(Gravity.CENTER);
        root.addView(title);

        tvDots = new TextView(this);
        tvDots.setText("○ ○ ○ ○");
        tvDots.setTextColor(Color.parseColor("#FFC93C")); tvDots.setTextSize(32);
        tvDots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16); tvDots.setLayoutParams(lp);
        root.addView(tvDots);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        glp.topMargin = dp(20); grid.setLayoutParams(glp);
        String[] keys = {"1","2","3","4","5","6","7","8","9","删除","0","取消"};
        Button first = null;
        for (String k : keys) {
            Button b = new Button(this);
            b.setText(k); b.setTextSize(24);
            // High-contrast focus state: the default system button style made
            // the focused key only a shade lighter than the rest — easy to
            // lose track of on a TV. Use the shared selector background
            // (yellow when focused) with its paired text colors instead.
            b.setBackgroundResource(R.drawable.btn_bg);
            b.setTextColor(getResources().getColorStateList(R.color.btn_text, getTheme()));
            b.setMinHeight(dp(60));
            GridLayout.LayoutParams blp = new GridLayout.LayoutParams();
            blp.width = dp(120); blp.height = GridLayout.LayoutParams.WRAP_CONTENT; blp.setMargins(dp(6), dp(6), dp(6), dp(6));
            b.setLayoutParams(blp);
            b.setOnClickListener(v -> onKey(k));
            grid.addView(b);
            if (first == null) first = b;
        }
        root.addView(grid);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#16213A"));
        scroll.addView(root);
        setContentView(scroll);
        if (first != null) first.requestFocus();
    }

    private void onKey(String k) {
        if ("删除".equals(k)) {
            if (entered.length() > 0) entered.deleteCharAt(entered.length() - 1);
        } else if ("取消".equals(k)) {
            setResult(RESULT_CANCELED); finish(); return;
        } else {
            if (entered.length() < 8) entered.append(k);
        }
        updateDots();
        check();
    }

    private void updateDots() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entered.length(); i++) sb.append("● ");
        if (sb.length() == 0) sb.append("○ ○ ○ ○");
        tvDots.setText(sb.toString().trim());
    }

    private void check() {
        Prefs p = new Prefs(this);
        if (entered.toString().equals(p.getPin())) {
            setResult(RESULT_OK); finish();
        } else if (entered.length() >= p.getPin().length()) {
            Toast.makeText(this, "PIN码不对，请重新输入", Toast.LENGTH_SHORT).show();
            entered.setLength(0); updateDots();
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            onKey(String.valueOf(keyCode - KeyEvent.KEYCODE_0)); return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            onKey(String.valueOf(keyCode - KeyEvent.KEYCODE_NUMPAD_0)); return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) { onKey("删除"); return true; }
        return super.onKeyDown(keyCode, event);
    }
}
