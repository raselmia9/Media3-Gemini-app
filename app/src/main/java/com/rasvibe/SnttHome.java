package com.rasvibe;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SNTT page support for RasVibe.
 * Mirrors the SNTT App Build "Download All" flow: fetch the JSON list,
 * download every item and store it locally, then show the stored home page.
 */
final class SnttHome {

    static final String JSON_LIST_URL =
            "https://raw.githubusercontent.com/raselmia9/SNTT-ALL-DATA/refs/heads/main/SNTT%20Download%20List.json";
    static final String HOME_TAG = "SNTT HOME UI.html";

    private static final String PREFS = "SnttStore";
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Callback {
        void onProgress(int done, int total, String name);
        void onFinished(int saved, int failed, int skipped, String error);
    }

    private SnttHome() {}

    static String loadHome(Context context) {
        return getPrefs(context).getString(HOME_TAG, "");
    }

    static void downloadAll(Context context, Callback callback) {
        final SharedPreferences prefs = getPrefs(context.getApplicationContext());
        EXEC.execute(() -> {
            final List<String[]> items = new ArrayList<>();
            try {
                String json = fetch(JSON_LIST_URL, 0);
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
            int ok = 0, fail = 0, skip = 0;
            for (int i = 0; i < total; i++) {
                final int done = i;
                final String name = items.get(i)[0];
                final String link = items.get(i)[1];
                MAIN.post(() -> callback.onProgress(done, total, name));

                if (link.isEmpty() || link.equalsIgnoreCase("none")) {
                    skip++;
                    continue;
                }
                String clean = link.replaceAll("\\s+", "");
                if (clean.startsWith("Https://")) clean = "https://" + clean.substring(8);

                try {
                    String content = fetch(clean, 0);
                    if (content != null && !content.isEmpty()) {
                        prefs.edit().putString(name, content).commit();
                        ok++;
                    } else {
                        fail++;
                    }
                } catch (Exception e) {
                    fail++;
                }
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

    private static String fetch(String urlString, int depth) throws Exception {
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
                if (location != null) return fetch(location, depth + 1);
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP " + status);
            }

            StringBuilder sb = new StringBuilder();
            try (InputStream in = conn.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
