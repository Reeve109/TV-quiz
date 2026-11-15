package com.sunzhe.tvquiz;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

public class Prefs {
    private static final String NAME = "tvquiz";
    private final SharedPreferences sp;

    public Prefs(Context c) { sp = c.getSharedPreferences(NAME, Context.MODE_PRIVATE); }

    public int getGrade() {
        int g = sp.getInt("grade", 2);
        return (g < 1 || g > 6) ? 2 : g;
    }
    public void setGrade(int g) {
        if (g < 1) g = 1;
        if (g > 6) g = 6;
        sp.edit().putInt("grade", g).apply();
    }

    public int getWatchMinutes() { return sp.getInt("watchMinutes", 30); }
    public void setWatchMinutes(int m) { sp.edit().putInt("watchMinutes", m).apply(); }

    public int getLockMinutes() { return sp.getInt("lockMinutes", 30); }
    public void setLockMinutes(int m) { sp.edit().putInt("lockMinutes", m).apply(); }

    public String getPin() { return sp.getString("pin", "8888"); }
    public void setPin(String p) { sp.edit().putString("pin", p).apply(); }

    public long getWatchedSeconds() { return sp.getLong("watchedSeconds", 0); }
    public void setWatchedSeconds(long s) { sp.edit().putLong("watchedSeconds", s).apply(); }

    public long getLockUntil() { return sp.getLong("lockUntil", 0); }
    public void setLockUntil(long t) { sp.edit().putLong("lockUntil", t).apply(); }

    public boolean isLocked() { return getLockUntil() > System.currentTimeMillis(); }

    public boolean isMonitoring() { return sp.getBoolean("monitoring", false); }
    public void setMonitoring(boolean b) { sp.edit().putBoolean("monitoring", b).apply(); }

    /** Practice mode flag is in-memory only via intent, not persisted. */

    public List<Integer> getRecentIds() {
        String s = sp.getString("recentIds", "");
        List<Integer> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split(",")) {
            try { out.add(Integer.parseInt(part.trim())); } catch (Exception ignored) {}
        }
        return out;
    }

    public void addRecentIds(List<Integer> ids) {
        List<Integer> cur = getRecentIds();
        cur.addAll(ids);
        // Keep last 18
        while (cur.size() > 18) cur.remove(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cur.size(); i++) { if (i > 0) sb.append(","); sb.append(cur.get(i)); }
        sp.edit().putString("recentIds", sb.toString()).apply();
    }

    public void clearRecent() { sp.edit().remove("recentIds").apply(); }

    /** Last attempt time (ms) of an online question-bank refresh, for throttling. */
    public long getLastBankFetch() { return sp.getLong("lastBankFetch", 0); }
    public void setLastBankFetch(long t) { sp.edit().putLong("lastBankFetch", t).apply(); }
}
