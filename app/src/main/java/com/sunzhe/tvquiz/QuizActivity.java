package com.sunzhe.tvquiz;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class QuizActivity extends Activity {
    private Prefs prefs;
    private List<Question> quiz = new ArrayList<>();
    private int index = 0;
    private int correctCount = 0;
    private boolean practice = false;
    private TextView tvSubject, tvQuestion;
    private Button[] optBtns = new Button[4];
    private static final int REQ_PIN = 2001;
    private boolean finished = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);
        prefs = new Prefs(this);
        practice = getIntent().getBooleanExtra("practice", false);

        // If already locked (and not practice), go to lock screen instead
        if (!practice && prefs.isLocked()) {
            startActivity(new Intent(this, LockActivity.class));
            finish(); return;
        }

        tvSubject = findViewById(R.id.tvSubject);
        tvQuestion = findViewById(R.id.tvQuestion);
        optBtns[0] = findViewById(R.id.btnOpt0);
        optBtns[1] = findViewById(R.id.btnOpt1);
        optBtns[2] = findViewById(R.id.btnOpt2);
        optBtns[3] = findViewById(R.id.btnOpt3);

        QuestionBank.init(this); // make sure a cached online bank is loaded even if MainActivity never ran in this process
        quiz = QuestionBank.pickQuiz(prefs.getGrade(), prefs.getRecentIds(), new Random());
        if (quiz.size() < 6) {
            Toast.makeText(this, "题库不足，请联系家长", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        List<Integer> ids = new ArrayList<>();
        for (Question q : quiz) ids.add(q.id);
        prefs.addRecentIds(ids);

        for (int i = 0; i < 4; i++) {
            final int opt = i;
            optBtns[i].setOnClickListener(v -> answer(opt));
        }
        Button parentBtn = findViewById(R.id.btnParentQuiz);
        parentBtn.setOnClickListener(v -> startActivityForResult(new Intent(this, PinActivity.class), REQ_PIN));
        showQuestion();
    }

    private void showQuestion() {
        Question q = quiz.get(index);
        tvSubject.setText(q.subject + " · 第 " + (index + 1) + " / " + quiz.size() + " 题" + (practice ? "（练习）" : ""));
        tvQuestion.setText(q.text);
        String[] labels = {"A.  ", "B.  ", "C.  ", "D.  "};
        for (int i = 0; i < 4; i++) optBtns[i].setText(labels[i] + q.options[i]);
        optBtns[0].requestFocus();
    }

    private void answer(int opt) {
        if (finished) return;
        Question q = quiz.get(index);
        if (opt == q.correct) {
            correctCount++;
            Toast.makeText(this, "答对啦！👍", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "这道答错了", Toast.LENGTH_SHORT).show();
        }
        index++;
        if (index < quiz.size()) {
            showQuestion();
        } else {
            finishQuiz();
        }
    }

    private void finishQuiz() {
        finished = true;
        if (practice) {
            Toast.makeText(this, "练习结束：答对 " + correctCount + " / " + quiz.size() + " 题", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        if (correctCount == quiz.size()) {
            prefs.setWatchedSeconds(0);
            prefs.setLockUntil(0);
            Toast.makeText(this, "全部答对！可以继续看啦 🎉", Toast.LENGTH_LONG).show();
            finish();
        } else {
            long until = System.currentTimeMillis() + (long) prefs.getLockMinutes() * 60L * 1000L;
            prefs.setLockUntil(until);
            Intent i = new Intent(this, LockActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
        }
    }

    @Override protected void onResume() { super.onResume(); WatchService.quizActive = true; OverlayGate.hide(); }
    @Override protected void onPause() { super.onPause(); WatchService.quizActive = false; }

    @Override public void onBackPressed() {
        // Do not allow dismissing the quiz with Back.
        Toast.makeText(this, "要答完题目才能继续看哦", Toast.LENGTH_SHORT).show();
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
            // Parent bypass: unlock and reset timer
            prefs.setWatchedSeconds(0);
            prefs.setLockUntil(0);
            finished = true;
            Toast.makeText(this, "家长已解锁", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
