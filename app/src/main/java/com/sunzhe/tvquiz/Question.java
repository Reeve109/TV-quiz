package com.sunzhe.tvquiz;

public class Question {
    public final int id;
    public final int grade; // 1-6
    public final String subject; // 语文 / 数学 / 英语
    public final String text;
    public final String[] options; // 4
    public final int correct; // 0-3

    public Question(int id, int grade, String subject, String text, int correct, String... options) {
        this.id = id; this.grade = grade; this.subject = subject;
        this.text = text; this.correct = correct; this.options = options;
    }
}
