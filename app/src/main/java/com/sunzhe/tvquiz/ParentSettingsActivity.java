package com.sunzhe.tvquiz;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Parent settings: D-pad friendly cycling buttons + PIN change field. Only reachable after PIN. */
public class ParentSettingsActivity extends Activity {
    private Prefs prefs;
    private TextView tvGrade, tvWatch, tvLock, tvBank;
    private EditText etPin;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        QuestionBank.init(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(56, 40, 56, 40);
        root.setBackgroundColor(Color.parseColor("#16213A"));
        scroll.addView(root);

        TextView title = label("家长设置", 38, "#FFFFFF");
        root.addView(title);
        TextView hint = label("用遥控器选择，按确认键切换。修改后自动保存。", 20, "#C7D2E8");
        root.addView(hint);

        tvGrade = label("", 28, "#FFFFFF"); root.addView(tvGrade);
        Button bGrade = btn("切换年级（1 → 2 → 3 → 4 → 5 → 6）");
        bGrade.setOnClickListener(v -> { prefs.setGrade(prefs.getGrade() % 6 + 1); refresh(); });
        root.addView(bGrade);

        tvWatch = label("", 28, "#FFFFFF"); root.addView(tvWatch);
        Button bWatch = btn("切换每轮观看时长（15/30/45/60分钟）");
        bWatch.setOnClickListener(v -> { prefs.setWatchMinutes(cycle(prefs.getWatchMinutes(), new int[]{15,30,45,60})); refresh(); });
        root.addView(bWatch);

        tvLock = label("", 28, "#FFFFFF"); root.addView(tvLock);
        Button bLock = btn("切换答错休息时长（10/20/30/45/60分钟）");
        bLock.setOnClickListener(v -> { prefs.setLockMinutes(cycle(prefs.getLockMinutes(), new int[]{10,20,30,45,60})); refresh(); });
        root.addView(bLock);

        tvBank = label("", 24, "#C7D2E8"); root.addView(tvBank);
        Button bBank = btn("立即刷新在线题库");
        bBank.setOnClickListener(v -> {
            QuestionFetcher.refreshAsync(this, true);
            Toast.makeText(this, "正在后台刷新题库，稍等几秒后返回本页查看题库来源", Toast.LENGTH_LONG).show();
        });
        root.addView(bBank);

        TextView pinLabel = label("修改家长PIN码（4-8位数字）：", 24, "#FFFFFF"); root.addView(pinLabel);
        etPin = new EditText(this);
        etPin.setText(prefs.getPin());
        etPin.setTextSize(26); etPin.setTextColor(Color.BLACK);
        etPin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        root.addView(etPin);
        Button bPin = btn("保存新PIN码");
        bPin.setOnClickListener(v -> {
            String p = etPin.getText().toString().trim();
            if (p.length() >= 4 && p.length() <= 8) { prefs.setPin(p); Toast.makeText(this, "PIN码已保存", Toast.LENGTH_SHORT).show(); }
            else Toast.makeText(this, "PIN码需要4-8位数字", Toast.LENGTH_LONG).show();
        });
        root.addView(bPin);

        Button bReset = btn("重置计时和锁屏（立即解锁）");
        bReset.setOnClickListener(v -> { prefs.setWatchedSeconds(0); prefs.setLockUntil(0); prefs.clearRecent(); Toast.makeText(this, "已重置", Toast.LENGTH_SHORT).show(); refresh(); });
        root.addView(bReset);

        Button bStop = btn("停止计时监控");
        bStop.setOnClickListener(v -> { prefs.setMonitoring(false); stopService(new android.content.Intent(this, WatchService.class)); Toast.makeText(this, "已停止计时，重新打开App点开始可恢复", Toast.LENGTH_LONG).show(); });
        root.addView(bStop);

        Button bDone = btn("完成，返回");
        bDone.setOnClickListener(v -> finish());
        root.addView(bDone);

        setContentView(scroll);
        bGrade.requestFocus();
        refresh();
    }

    private int cycle(int cur, int[] vals) {
        for (int i = 0; i < vals.length; i++) if (vals[i] == cur) return vals[(i + 1) % vals.length];
        return vals[0];
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        tvGrade.setText("当前年级：" + prefs.getGrade() + "年级");
        tvWatch.setText("每轮观看：" + prefs.getWatchMinutes() + "分钟");
        tvLock.setText("答错休息：" + prefs.getLockMinutes() + "分钟");
        if (tvBank != null) {
            int g = prefs.getGrade();
            if (QuestionBank.isUsingRemote() && QuestionBank.remoteGrade() == g) {
                StringBuilder sb = new StringBuilder();
                sb.append("题库来源：在线题库（开源仓库）（").append(g).append("年级共 ")
                  .append(QuestionBank.remoteCount(g, null)).append(" 题）");
                String missing = "";
                for (String s : new String[]{"语文", "数学", "英语"}) {
                    if (QuestionBank.remoteCount(g, s) == 0) {
                        if (!missing.isEmpty()) missing += "、";
                        missing += s;
                    }
                }
                if (!missing.isEmpty()) sb.append("，其中").append(missing).append("为内置题库");
                tvBank.setText(sb.toString());
            } else {
                tvBank.setText("题库来源：内置题库（共 " + QuestionBank.all().size() + " 题）");
            }
        }
    }

    private TextView label(String s, int sp, String color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(Color.parseColor(color));
        t.setGravity(Gravity.LEFT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 22; t.setLayoutParams(lp);
        return t;
    }

    private Button btn(String s) {
        Button b = new Button(this);
        b.setText(s); b.setTextSize(24); b.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(760, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 10; b.setLayoutParams(lp);
        return b;
    }
}
