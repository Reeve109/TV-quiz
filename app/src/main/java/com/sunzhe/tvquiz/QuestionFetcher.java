package com.sunzhe.tvquiz;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches quiz questions in real time from the open-source bank
 * ChinaTextbookStudyFree (GitHub repo renamed to "congcong-school", MIT license).
 * Questions live upstream as per-textbook JSON files under
 * output/{chinese,math,english}/quizzes/. The app downloads only the files for
 * the currently selected grade, cleans/filters them on-device into 4-choice
 * questions (see cleanFiles), caches the result per grade, and silently falls
 * back to the built-in bank whenever anything fails.
 *
 * Routes (re-verified 2026-11-15, see jsdelivr-verification.md + TEST_REPORT):
 * jsDelivr's listing API cannot serve this repo (403 "package size exceeded
 * 50 MB" on both flat and tree listings), so discovery does NOT need any
 * listing at all: the app resolves the latest release tag via the
 * data.jsdelivr.com versions API (which does work), then probes the
 * deterministic textbook file names ({series}{grade}{semester}_unitN.json,
 * unit-1 gated, stop after 2 misses) directly on the jsDelivr CDN edges.
 * The whole primary pipeline (versions + files) runs on *.jsdelivr.net /
 * *.jsdelivr.com only — github.com is never touched. Only if probing yields
 * too few questions does it fall back to the GitHub git/trees listing
 * (api.github.com, then a gh-proxy mirror) with raw/gh-proxy file fetches.
 *
 * Upstream caveats: questions are AI-generated and not teacher-reviewed;
 * English exists only for grades 3-6 upstream (PEP starts in grade 3), so
 * grades 1-2 English always comes from the built-in bank.
 */
public class QuestionFetcher {
    public static final String UPSTREAM_REPO = "wuwangzhang1216/congcong-school";

    private static final String VERSIONS_URL =
            "https://data.jsdelivr.com/v1/packages/gh/" + UPSTREAM_REPO;
    private static final String[] LISTING_URLS = {
            "https://api.github.com/repos/" + UPSTREAM_REPO + "/git/trees/main?recursive=1",
            "https://gh-proxy.com/https://api.github.com/repos/" + UPSTREAM_REPO + "/git/trees/main?recursive=1",
    };
    private static final String[] JSDELIVR_HOSTS = {
            "https://cdn.jsdelivr.net/gh/", "https://fastly.jsdelivr.net/gh/", "https://gcore.jsdelivr.net/gh/",
    };

    /** jsDelivr file bases pinned to a resolved ref (release tag or "main"). */
    private static String[] jsdelivrBases(String ref) {
        String[] out = new String[JSDELIVR_HOSTS.length];
        for (int i = 0; i < JSDELIVR_HOSTS.length; i++) out[i] = JSDELIVR_HOSTS[i] + UPSTREAM_REPO + "@" + ref + "/";
        return out;
    }

    private static String[] fallbackBases() {
        return new String[]{
                "https://raw.githubusercontent.com/" + UPSTREAM_REPO + "/main/",
                "https://gh-proxy.com/https://raw.githubusercontent.com/" + UPSTREAM_REPO + "/main/",
        };
    }

    private static final long MIN_INTERVAL_MS = 30L * 60L * 1000L; // auto-refresh at most every 30 min
    private static final int LIST_TIMEOUT_MS = 10000;
    private static final int FILE_TIMEOUT_MS = 6000;
    private static final int MAX_LIST_BYTES = 4 * 1024 * 1024;
    private static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final int FIRST_REMOTE_ID = 100001;

    // Cleaning thresholds (a TV screen must show the whole question at once).
    static final int MAX_TEXT_LEN = 100;   // question stem, chars
    static final int MAX_OPT_LEN = 40;     // single option, chars
    static final int MATERIAL_MIN_LEN = 80; // "根据短文…" stems shorter than this lack their passage

    private static final String[] SUBJECTS = {"语文", "数学", "英语"};
    private static final String[] DIRS = {"chinese", "math", "english"};
    private static final String[] GRADE_CN = {"", "一", "二", "三", "四", "五", "六"};

    private static final String[] MATERIAL_KEYS = {
            "根据短文", "根据材料", "根据课文", "阅读下面", "读下面", "根据上文",
            "根据图", "看图", "如图", "根据 passage", "根据短文内容"};
    private static final String[] MULTI_KEYS = {"多选题", "多选）", "(多选)", "（多选）"};

    private static volatile boolean fetching = false;

    private static String cacheName(int grade) { return "bank_cache_g" + grade + ".json"; }

    // ------------------------------------------------------------------ cache

    /** Load the cached cleaned bank for the given grade, if present and sane. */
    public static void loadCache(Context ctx, int grade) {
        try {
            File f = new File(ctx.getFilesDir(), cacheName(grade));
            if (!f.exists() || f.length() == 0 || f.length() > 16L * 1024 * 1024) return;
            String json = readFully(new FileInputStream(f), 16 * 1024 * 1024);
            if (json == null) return;
            List<Question> parsed = parseCache(json, grade);
            if (parsed != null) QuestionBank.setRemote(grade, parsed);
        } catch (Exception ignored) {
            // Corrupt cache must never break the app: built-in bank stays in use.
        }
        // Legacy single-file cache from the previous questions.json design: drop it.
        try { new File(ctx.getFilesDir(), "questions_cache.json").delete(); } catch (Exception ignored) {}
    }

    private static void saveCache(Context ctx, int grade, List<Question> qs) {
        try {
            String json = toCacheJson(qs);
            File tmp = new File(ctx.getFilesDir(), cacheName(grade) + ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
            File dst = new File(ctx.getFilesDir(), cacheName(grade));
            if (!tmp.renameTo(dst)) {
                try (FileOutputStream fos = new FileOutputStream(dst)) {
                    fos.write(json.getBytes(StandardCharsets.UTF_8));
                }
                tmp.delete();
            }
        } catch (Exception ignored) {}
    }

    static String toCacheJson(List<Question> qs) throws Exception {
        JSONArray arr = new JSONArray();
        for (Question q : qs) {
            JSONObject o = new JSONObject();
            o.put("id", q.id);
            o.put("grade", q.grade);
            o.put("subject", q.subject);
            o.put("text", q.text);
            o.put("correct", q.correct);
            JSONArray opts = new JSONArray();
            for (String op : q.options) opts.put(op);
            o.put("options", opts);
            arr.put(o);
        }
        JSONObject root = new JSONObject();
        root.put("questions", arr);
        return root.toString();
    }

    /** Lenient structural validation of our own cache format; partial damage skips entries. */
    static List<Question> parseCache(String json, int grade) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("questions");
            if (arr == null) return null;
            List<Question> out = new ArrayList<>();
            int cn = 0, math = 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (o.optInt("grade", -1) != grade) continue;
                String subject = o.optString("subject", "");
                String text = o.optString("text", "");
                int correct = o.optInt("correct", -1);
                JSONArray opts = o.optJSONArray("options");
                if (text.trim().isEmpty() || correct < 0 || correct > 3) continue;
                if (opts == null || opts.length() != 4) continue;
                String[] options = new String[4];
                boolean ok = true;
                for (int j = 0; j < 4; j++) {
                    options[j] = opts.optString(j, "");
                    if (options[j].trim().isEmpty()) ok = false;
                }
                if (!ok) continue;
                int id = o.optInt("id", -1);
                if (id < FIRST_REMOTE_ID) id = FIRST_REMOTE_ID + out.size();
                out.add(new Question(id, grade, subject, text, correct, options));
                if ("语文".equals(subject)) cn++;
                if ("数学".equals(subject)) math++;
            }
            if (out.size() < 30 || cn < 10 || math < 10) return null;
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- refresh

    public static void refreshAsync(Context ctx) { refreshAsync(ctx, false); }

    /**
     * Background refresh for the currently selected grade. Throttled unless
     * force=true (parent settings button). Never blocks UI/timer; all failures
     * are silent and keep the current bank.
     */
    public static void refreshAsync(Context ctx, boolean force) {
        final Context app = ctx.getApplicationContext();
        final Prefs prefs = new Prefs(app);
        final int grade = prefs.getGrade();
        long now = System.currentTimeMillis();
        if (!force && now - prefs.getLastBankFetch() < MIN_INTERVAL_MS) return;
        if (fetching) return;
        fetching = true;
        prefs.setLastBankFetch(now);
        new Thread(() -> {
            try {
                List<Question> bank = fetchBank(grade);
                if (bank != null) {
                    saveCache(app, grade, bank);
                    QuestionBank.setRemote(grade, bank);
                }
            } catch (Exception ignored) {
                // Offline / bad data: keep whatever bank is currently active.
            } finally {
                fetching = false;
            }
        }, "QuestionFetch").start();
    }

    /**
     * Full pipeline. Primary: resolve latest release tag on data.jsdelivr.com,
     * probe this grade's textbook files on jsDelivr CDN only, clean on-device.
     * Fallback (only when probing cleans to too little): git/trees listing via
     * api.github.com / gh-proxy + multi-base file fetch. Null on total failure
     * (caller keeps cache / built-in bank).
     */
    static List<Question> fetchBank(int grade) {
        String ref = resolveRef();
        List<Question> probed = cleanFiles(probeFiles(ref, grade), grade);
        if (passesMinimum(probed)) return probed;

        String listing = null;
        for (String u : LISTING_URLS) {
            listing = httpGet(u, LIST_TIMEOUT_MS, MAX_LIST_BYTES);
            if (listing != null) break;
        }
        if (listing == null) return null;
        List<String> paths = selectPaths(listing, grade);
        if (paths.isEmpty()) return null;

        List<String> bases = new ArrayList<>();
        Collections.addAll(bases, jsdelivrBases(ref));
        Collections.addAll(bases, fallbackBases());
        List<String[]> files = new ArrayList<>();
        for (String path : paths) {
            String body = null;
            for (String base : bases) {
                body = httpGet(base + encodePath(path), FILE_TIMEOUT_MS, MAX_FILE_BYTES);
                if (body != null) break;
            }
            if (body == null) continue;
            files.add(new String[]{subjectOf(path), body});
        }
        if (files.isEmpty()) return null;
        List<Question> cleaned = cleanFiles(files, grade);
        return passesMinimum(cleaned) ? cleaned : null;
    }

    private static final Pattern SEMVER = Pattern.compile("\\d+\\.\\d+\\.\\d+");

    /** Latest release tag (e.g. "1.8.3") from the data.jsdelivr.com versions API; "main" if unreachable. */
    static String resolveRef() {
        String body = httpGet(VERSIONS_URL, LIST_TIMEOUT_MS, MAX_LIST_BYTES);
        if (body != null) {
            try {
                JSONArray versions = new JSONObject(body).optJSONArray("versions");
                if (versions != null) {
                    for (int i = 0; i < versions.length(); i++) {
                        String v = versions.optJSONObject(i) == null ? "" : versions.optJSONObject(i).optString("version", "");
                        if (SEMVER.matcher(v).matches()) return v;
                    }
                }
            } catch (Exception ignored) {}
        }
        return "main";
    }

    /**
     * Candidate textbook file stems (without "_unitN.json") for one subject+grade,
     * from the upstream naming scheme for the mainstream Renjiao/Tongbian series.
     * Every real stem has a unit1 file, so probing is unit-1 gated.
     */
    static List<String> candidateStems(String dir, int grade) {
        List<String> stems = new ArrayList<>();
        String g = GRADE_CN[grade];
        for (String sem : new String[]{"上册", "下册"}) {
            if ("chinese".equals(dir)) {
                String base = "义务教育教科书·语文" + g + "年级" + sem;
                stems.add(base); stems.add(base + "（2026秋版）");
            } else if ("math".equals(dir)) {
                for (String pre : new String[]{"义务教育教科书·数学", "义务教育教科书 · 数学"})
                    for (String suf : new String[]{"", "（2022课标修订版）", "（2026秋版）"})
                        stems.add(pre + g + "年级" + sem + suf);
            } else if ("english".equals(dir)) {
                if (grade < 3) break; // PEP starts in grade 3; grades 1-2 use the built-in bank
                for (String pre : new String[]{"义务教育教科书·英语（PEP）（三年级起点）", "义务教育教科书·英语（三年级起点）", "义务教育教科书·英语（精通）"})
                    for (String suf : new String[]{"", "（2026秋版）"})
                        stems.add(pre + g + "年级" + sem + suf);
            }
        }
        return stems;
    }

    /** Download this grade's files by probing candidate names on jsDelivr edges only. */
    static List<String[]> probeFiles(String ref, int grade) {
        String[] bases = jsdelivrBases(ref);
        List<String[]> files = new ArrayList<>();
        for (int s = 0; s < DIRS.length; s++) {
            for (String stem : candidateStems(DIRS[s], grade)) {
                int misses = 0;
                for (int unit = 1; unit <= 16; unit++) {
                    String path = "output/" + DIRS[s] + "/quizzes/" + stem + "_unit" + unit + ".json";
                    String body = null;
                    for (String base : bases) {
                        body = httpGet(base + encodePath(path), FILE_TIMEOUT_MS, MAX_FILE_BYTES);
                        if (body != null) break;
                    }
                    if (body == null) {
                        if (unit == 1 || ++misses >= 2) break;
                        continue;
                    }
                    misses = 0;
                    files.add(new String[]{SUBJECTS[s], body});
                }
            }
        }
        return files;
    }

    private static boolean passesMinimum(List<Question> qs) {
        if (qs == null || qs.size() < 30) return false;
        int cn = 0, math = 0;
        for (Question q : qs) {
            if ("语文".equals(q.subject)) cn++;
            if ("数学".equals(q.subject)) math++;
        }
        return cn >= 10 && math >= 10;
    }

    private static String subjectOf(String path) {
        for (int i = 0; i < DIRS.length; i++)
            if (path.startsWith("output/" + DIRS[i] + "/quizzes/")) return SUBJECTS[i];
        return "";
    }

    /**
     * Pick this grade's quiz files out of a git/trees listing. A file belongs
     * to the grade when its name contains e.g. "三年级上册"/"三年级下册" (the
     * grade right before 上册/下册; safe against "（三年级起点）六年级上册").
     */
    static List<String> selectPaths(String treesJson, int grade) {
        List<String> out = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(treesJson);
            JSONArray tree = root.optJSONArray("tree");
            if (tree == null) return out;
            String up = GRADE_CN[grade] + "年级上册", down = GRADE_CN[grade] + "年级下册";
            for (int i = 0; i < tree.length(); i++) {
                JSONObject o = tree.optJSONObject(i);
                if (o == null) continue;
                String path = o.optString("path", "");
                if (subjectOf(path).isEmpty() || !path.endsWith(".json")) continue;
                String fn = path.substring(path.lastIndexOf('/') + 1);
                if (fn.contains(up) || fn.contains(down)) out.add(path);
            }
        } catch (Exception ignored) {}
        Collections.sort(out);
        return out;
    }

    // ---------------------------------------------------------------- cleaning

    /**
     * Clean upstream textbook files into app questions. Input: pairs of
     * {subject, fileJson}. Rules: only type=choice with exactly 4 distinct
     * non-empty options; answer normalized to an index (letter or verbatim,
     * case-insensitive); LaTeX reduced to plain text (questions still
     * containing backslash commands are dropped); multi-select, passage-less
     * material questions and over-long stems/options dropped; dedup by stem.
     */
    static List<Question> cleanFiles(List<String[]> files, int grade) {
        List<Question> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int nextId = FIRST_REMOTE_ID;
        for (String[] pair : files) {
            String subject = pair[0];
            JSONObject root;
            try { root = new JSONObject(pair[1]); } catch (Exception e) { continue; }
            for (String section : new String[]{"unit_test", "exam"}) {
                JSONObject sec = root.optJSONObject(section);
                if (sec == null) continue;
                JSONArray qs = sec.optJSONArray("questions");
                if (qs == null) continue;
                for (int i = 0; i < qs.length(); i++) {
                    JSONObject o = qs.optJSONObject(i);
                    if (o == null) continue;
                    Question q = cleanOne(o, grade, subject, nextId);
                    if (q == null) continue;
                    if (!seen.add(q.grade + "|" + q.subject + "|" + q.text)) continue;
                    out.add(q);
                    nextId++;
                }
            }
        }
        return out;
    }

    private static Question cleanOne(JSONObject o, int grade, String subject, int id) {
        try {
            if (!"choice".equals(o.optString("type", ""))) return null;
            JSONArray optsArr = o.optJSONArray("options");
            if (optsArr == null || optsArr.length() != 4) return null;

            String text = cleanLatex(o.optString("question", "").trim());
            String[] options = new String[4];
            for (int j = 0; j < 4; j++)
                options[j] = cleanLatex(cleanOption(optsArr.optString(j, "")));

            if (text.isEmpty()) return null;
            Set<String> distinct = new HashSet<>();
            for (String op : options) {
                if (op.isEmpty()) return null;
                distinct.add(op);
            }
            if (distinct.size() != 4) return null;
            if (text.indexOf('\\') >= 0) return null;
            for (String op : options) if (op.indexOf('\\') >= 0) return null;
            if (text.length() > MAX_TEXT_LEN) return null;
            for (String op : options) if (op.length() > MAX_OPT_LEN) return null;
            for (String k : MULTI_KEYS) if (text.contains(k)) return null;
            if (text.length() < MATERIAL_MIN_LEN)
                for (String k : MATERIAL_KEYS) if (text.contains(k)) return null;

            int correct = normalizeAnswer(o.optString("answer", ""), options);
            if (correct < 0) return null;
            return new Question(id, grade, subject, text, correct, options);
        } catch (Exception e) {
            return null;
        }
    }

    private static final Pattern OPT_PREFIX = Pattern.compile("^\\s*[A-Da-d]\\s*[.、．：:)）]\\s*");
    static String cleanOption(String raw) {
        return OPT_PREFIX.matcher(raw.trim()).replaceFirst("").trim();
    }

    private static final Pattern FRAC = Pattern.compile("\\\\d?frac\\{([^{}]*)\\}\\{([^{}]*)\\}");
    private static final Pattern SQRT = Pattern.compile("\\\\sqrt\\{([^{}]*)\\}");
    private static final Pattern DELIM = Pattern.compile("\\\\[()\\[\\]]");

    static String cleanLatex(String s) {
        // \frac{a}{b} / \dfrac{a}{b} -> a/b, iterating for nesting
        String prev;
        int guard = 0;
        do {
            prev = s;
            Matcher m = FRAC.matcher(s);
            StringBuffer sb = new StringBuffer();
            while (m.find())
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + "/" + m.group(2)));
            m.appendTail(sb);
            s = sb.toString();
        } while (!s.equals(prev) && ++guard < 5);
        Matcher sm = SQRT.matcher(s);
        StringBuffer ssb = new StringBuffer();
        while (sm.find()) sm.appendReplacement(ssb, Matcher.quoteReplacement("√" + sm.group(1)));
        sm.appendTail(ssb);
        s = ssb.toString();
        s = s.replace("$$", "").replace("$", "");
        s = DELIM.matcher(s).replaceAll("");
        s = s.replace("\\times", "×").replace("\\div", "÷").replace("\\pi", "π")
             .replace("\\le", "≤").replace("\\ge", "≥").replace("\\neq", "≠")
             .replace("\\cdot", "·").replace("\\%", "%");
        return s.trim();
    }

    private static final Pattern MULTI_SEP = Pattern.compile("[A-Da-d][、,，和][A-Da-d]");
    private static final Pattern LETTER_ONLY = Pattern.compile("([A-Da-d])\\s*[.、．：:)）]?\\s*");
    private static final Pattern LETTER_TEXT = Pattern.compile("^([A-Da-d])\\s*[.、．：:)）]\\s*(.+)$");

    /** Map an upstream answer (letter, "B. text", or verbatim text) to an option index; -1 if impossible/multi. */
    static int normalizeAnswer(String answer, String[] options) {
        String a = answer == null ? "" : answer.trim();
        if (a.isEmpty()) return -1;
        if (a.length() == 3 && MULTI_SEP.matcher(a).matches()) return -1; // e.g. "A、C"
        String squashed = a.replace(" ", "");
        if (squashed.length() >= 2 && squashed.length() <= 4) {
            boolean allLetters = true;
            for (int i = 0; i < squashed.length(); i++)
                if ("ABCDabcd".indexOf(squashed.charAt(i)) < 0) allLetters = false;
            if (allLetters) return -1; // e.g. "AC" multi-select
        }
        Matcher lm = LETTER_ONLY.matcher(a);
        if (lm.matches()) return "ABCD".indexOf(Character.toUpperCase(lm.group(1).charAt(0)));
        Matcher tm = LETTER_TEXT.matcher(a);
        if (tm.matches()) {
            int idx = "ABCD".indexOf(Character.toUpperCase(tm.group(1).charAt(0)));
            String rest = tm.group(2).trim();
            if (idx >= 0 && idx < options.length && options[idx].equalsIgnoreCase(rest)) return idx;
            a = rest;
        }
        String target = a.trim().toLowerCase();
        for (int i = 0; i < options.length; i++)
            if (options[i].trim().toLowerCase().equals(target)) return i;
        return -1;
    }

    // ------------------------------------------------------------------- http

    private static String httpGet(String url, int timeoutMs, int maxBytes) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "TVQuiz");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            return readFully(conn.getInputStream(), maxBytes);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Percent-encode a repo path (UTF-8), keeping '/' and common safe chars. */
    static String encodePath(String path) {
        StringBuilder sb = new StringBuilder();
        for (byte b : path.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            boolean safe = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '/' || c == '.' || c == '_' || c == '-' || c == '~';
            if (safe) sb.append((char) c);
            else sb.append('%').append(String.format("%02X", c));
        }
        return sb.toString();
    }

    private static String readFully(InputStream in, int maxBytes) throws Exception {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) return null;
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            try { in.close(); } catch (Exception ignored) {}
        }
    }
}
