package com.sunzhe.tvquiz;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Prefs p = new Prefs(context);
        // Always restart monitoring after boot if it was on, or if a lock is active.
        if (p.isMonitoring() || p.isLocked()) {
            p.setMonitoring(true);
            Intent svc = new Intent(context, WatchService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc);
            else context.startService(svc);
        }
        if (p.isLocked()) {
            Intent lock = new Intent(context, LockActivity.class);
            lock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try { context.startActivity(lock); } catch (Exception ignored) {}
        }
    }
}
