package com.rasvibe;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SNTT page support for RasVibe.
 * Mirrors the SNTT App Build "Download All" flow: fetch the JSON list,
 * download every item into local storage (same keys the SNTT HTML expects),
 * then show the stored home page.
 */
final class SnttHome {

    static final String JSON_LIST_URL =
            "https://raw.githubusercontent.com/raselmia9/SNTT-ALL-DATA/refs/heads/main/SNTT%20Download%20List.json";
    static final String HOME_TAG = "SNTT HOME UI.html";
    static final String CALIBRATION_TAG = "Ship Calibration.html";
    static final String ALL_TANK_TAG = "All Tank Calibration.html";

    static final int STATE_OK = 1;
    static final int STATE_FAILED = 2;
    static final int STATE_SKIPPED = 3;

    private static final String PREFS = "SnttStore";
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Callback {
        /** The JSON list was read: file names only, in list order. */
        void onListReady(String[] names);
        /** An item is about to download. */
        void onItemStart(int index, String name);
        /** Bytes received so far for an item; total is -1 when the server does not say. */
        void onItemProgress(int index, long read, long total);
        /** An item finished: state is STATE_OK, STATE_FAILED or STATE_SKIPPED. */
        void onItemDone(int index, int state, long bytes, String note);
        /** All items processed. error is non-null when the list itself could not be read. */
        void onFinished(int saved, int failed, int skipped, String error);
    }

    interface ByteProgress {
        void onBytes(long read, long total);
    }

    private SnttHome() {}

    static String loadHome(Context context) {
        return getValue(context, HOME_TAG, "");
    }

    static String getValue(Context context, String tag, String defaultValue) {
        String value = getPrefs(context).getString(tag, null);
        return value == null ? defaultValue : value;
    }

    static void putValue(Context context, String tag, String value) {
        getPrefs(context).edit().putString(tag, value == null ? "" : value).apply();
    }

    static boolean hasTag(Context context, String tag) {
        return getPrefs(context).contains(tag);
    }

    static Set<String> allTags(Context context) {
        return getPrefs(context).getAll().keySet();
    }

    static void downloadAll(Context context, Callback callback) {
        final SharedPreferences prefs = getPrefs(context.getApplicationContext());
        EXEC.execute(() -> {
            final List<String[]> items = new ArrayList<>();
            try {
                String json = fetch(JSON_LIST_URL, 0, null);
                if (json == null || json.trim().isEmpty()) throw new Exception("তালিকা খালি");
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String name = obj.optString("TinyDB Name", "").trim();
                    String link = obj.optString("File Link", "").trim();
                    if (!name.isEmpty()) items.add(new String[]{name, link});
                }
                if (items.isEmpty()) throw new Exception("তালিকায় কোনো আইটেম নেই");
            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : "তালিকা আনা যায়নি";
                MAIN.post(() -> callback.onFinished(0, 0, 0, err));
                return;
            }

            final int total = items.size();
            final String[] names = new String[total];
            for (int i = 0; i < total; i++) names[i] = items.get(i)[0];
            MAIN.post(() -> callback.onListReady(names));

            int ok = 0;
            int fail = 0;
            int skip = 0;
            for (int i = 0; i < total; i++) {
                final int index = i;
                final String name = names[i];
                final String link = items.get(i)[1];
                MAIN.post(() -> callback.onItemStart(index, name));

                final long[] lastRead = {0};
                final long[] lastPostAt = {0};
                ByteProgress progress = (read, length) -> {
                    lastRead[0] = read;
                    long now = System.currentTimeMillis();
                    if (read < length && now - lastPostAt[0] < 120) return;
                    lastPostAt[0] = now;
                    MAIN.post(() -> callback.onItemProgress(index, read, length));
                };

                int state;
                String note = "";
                if (link.isEmpty() || link.equalsIgnoreCase("none")) {
                    skip++;
                    state = STATE_SKIPPED;
                    note = "ডাউনলোড লিংক নেই";
                } else {
                    String clean = link.replaceAll("\\s+", "");
                    if (clean.startsWith("Https://")) clean = "https://" + clean.substring(8);
                    try {
                        String content = fetch(clean, 0, progress);
                        if (content != null && !content.isEmpty()) {
                            prefs.edit().putString(name, content).commit();
                            ok++;
                            state = STATE_OK;
                        } else {
                            fail++;
                            state = STATE_FAILED;
                            note = "ফাইল খালি";
                        }
                    } catch (Exception e) {
                        fail++;
                        state = STATE_FAILED;
                        note = e.getMessage() != null ? e.getMessage() : "অজানা সমস্যা";
                    }
                }

                final int finalState = state;
                final String finalNote = note;
                final long bytes = lastRead[0];
                MAIN.post(() -> callback.onItemDone(index, finalState, bytes, finalNote));
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ignored) {}
            }

            final int fOk = ok, fFail = fail, fSkip = skip;
            MAIN.post(() -> callback.onFinished(fOk, fFail, fSkip, null));
        });
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String fetch(String urlString, int depth, ByteProgress progress) throws Exception {
        if (depth > 5) throw new Exception("Too many redirects");
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0)");

            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_SEE_OTHER) {
                String location = conn.getHeaderField("Location");
                if (location != null) return fetch(location, depth + 1, progress);
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP " + status);
            }

            long length = conn.getContentLength();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] buffer = new byte[16384];
                long read = 0;
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    read += n;
                    if (progress != null) progress.onBytes(read, length);
                }
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }
}
