package com.sunzhe.tvquiz;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Full-screen overlay "gate" shown when the watch time is up (or a lock is active)
 * and the quiz/lock Activity could not be brought to the foreground.
 *
 * Why this exists: since Android 10, an app in the background may NOT start an
 * Activity (the system silently blocks it — no exception). That is exactly the
 * normal use case here: the child is watching in another app while WatchService
 * runs in the background, so the old startActivity()-only approach never popped
 * up on Android TV 10+ (incl. the Android TV 13 emulator).
 *
 * A TYPE_APPLICATION_OVERLAY window is not subject to that restriction (it does
 * require the "draw over other apps" permission, which MainActivity now asks
 * for). The overlay covers the whole screen and cannot be dismissed except by
 * its button, which opens the quiz/lock screen, or by the parent PIN flow
 * inside those screens.
 */
public class OverlayGate {
    private static WindowManager wm;
    private static View root;

    public static boolean canShow(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    public static boolean isShown() { return root != null; }

    public static synchronized void show(Context ctx, final Class<?> target, boolean isLock) {
        if (root != null || !canShow(ctx)) return;
        final Context app = ctx.getApplicationContext();

        LinearLayout box = new LinearLayout(app);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.parseColor("#16213A"));
        int pad = dp(app, 32);
        box.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(app);
        title.setText(isLock ? "⏳ 休息时间还没到" : "⏰ 时间到啦！");
        title.setTextColor(Color.parseColor("#FFC93C"));
        title.setTextSize(36);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView msg = new TextView(app);
        msg.setText(isLock
                ? "先休息一下，倒计时结束后再答题。"
                : "先答对全部题目，才能继续看哦。");
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(24);
        msg.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(app, 16);
        msg.setLayoutParams(mlp);
        box.addView(msg);

        Button go = new Button(app);
        go.setText(isLock ? "查看休息倒计时" : "开始答题 ▶");
        go.setTextSize(26);
        go.setBackgroundResource(R.drawable.btn_bg);
        go.setTextColor(app.getResources().getColorStateList(R.color.btn_text, app.getTheme()));
        go.setFocusable(true);
        go.setFocusableInTouchMode(true);
        go.setMinHeight(dp(app, 64));
        go.setPadding(dp(app, 20), dp(app, 10), dp(app, 20), dp(app, 10));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(app, 28);
        go.setLayoutParams(blp);
        go.setOnClickListener(v -> {
            hide();
            Intent i = new Intent(app, target);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try { app.startActivity(i); } catch (Exception ignored) {}
        });
        box.addView(go);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        lp.gravity = Gravity.CENTER;
        try {
            wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return;
            wm.addView(box, lp);
            root = box;
            go.requestFocus();
        } catch (Exception e) {
            root = null;
            wm = null;
        }
    }

    public static synchronized void hide() {
        if (wm != null && root != null) {
            try { wm.removeView(root); } catch (Exception ignored) {}
        }
        root = null;
        wm = null;
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
