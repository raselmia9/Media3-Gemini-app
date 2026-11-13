package com.raselahomed467.advancedmedia3player;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.database.DatabaseProvider;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.offline.DefaultDownloadIndex;
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.offline.DownloadService;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

/**
 * Shared download store for RasVibe.
 * One DownloadManager, one cache and one database for the whole process.
 * Downloads are stored in the app's private internal storage, so they do not
 * appear in the file manager, the gallery or the media scanner.
 */
public final class RasDownloads {

    private static final String PREFS = "ras_download_headers";
    private static final String PREFS_META = "ras_download_metadata";
    private static final String KEY_PRIVATE_STORAGE = "private_storage_v2";
    private static final String CHANNEL_ID = "ras_downloads";
    private static final int USER_STOP_REASON = 1;

    private static DownloadManager manager;
    private static Cache cache;
    private static CacheDataSource.Factory offlineReadFactory;

    private RasDownloads() {}

    public static synchronized void init(Context context) {
        if (manager != null) return;

        Context app = context.getApplicationContext();
        ensureNotificationChannel(app);

        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean firstPrivateRun = !prefs.getBoolean(KEY_PRIVATE_STORAGE, false);
        if (firstPrivateRun) {
            File oldDir = app.getExternalFilesDir(null) == null
                    ? null
                    : new File(app.getExternalFilesDir(null), "downloads");
            deleteRecursively(oldDir);
        }

        DatabaseProvider databaseProvider = new StandaloneDatabaseProvider(app);

        File base = new File(app.getFilesDir(), "offline_media");
        if (!base.exists()) base.mkdirs();
        try {
            File nomedia = new File(base, ".nomedia");
            if (!nomedia.exists()) nomedia.createNewFile();
        } catch (IOException ignored) {}

        cache = new SimpleCache(base, new NoOpCacheEvictor(), databaseProvider);

        OkHttpDataSource.Factory okFactory = new OkHttpDataSource.Factory(new OkHttpClient.Builder().build());
        CacheDataSource.Factory downloadCacheFactory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(new HeaderFactory(app, okFactory));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        DefaultDownloaderFactory downloaderFactory = new DefaultDownloaderFactory(downloadCacheFactory, executor);

        manager = new DownloadManager(app, new DefaultDownloadIndex(databaseProvider), downloaderFactory);

        if (firstPrivateRun) {
            manager.removeAllDownloads();
            prefs.edit().putBoolean(KEY_PRIVATE_STORAGE, true).commit();
        }

        offlineReadFactory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(null);

        // Global listener: on complete, assemble offline video file and show completion notification
        manager.addListener(new DownloadManager.Listener() {
            @Override
            public void onDownloadChanged(DownloadManager downloadManager, Download download, @Nullable Exception finalException) {
                if (download.state == Download.STATE_COMPLETED) {
                    assembleOfflineVideoIfNeeded(app, download, null);
                    generateThumbnailIfNeeded(app, download, null);
                    showCompletedNotification(app, download);
                }
            }
        });
    }

    private static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Downloads",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Video download progress and notifications");
                channel.setShowBadge(true);
                nm.createNotificationChannel(channel);
            }
        }
    }

    private static void deleteRecursively(@Nullable File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    public static DownloadManager getManager() {
        return manager;
    }

    public static CacheDataSource.Factory getOfflineReadFactory() {
        return offlineReadFactory;
    }

    /** True once the saved download list has been loaded from the database. */
    public static boolean isReady() {
        return manager != null && manager.isInitialized();
    }

    /** All downloads from persistent index, newest activity first. */
    public static List<Download> listForPage() {
        List<Download> out = new ArrayList<>();
        if (!isReady()) return out;
        try (DownloadCursor cursor = manager.getDownloadIndex().getDownloads()) {
            while (cursor.moveToNext()) {
                out.add(cursor.getDownload());
            }
        } catch (Exception e) {
            out.addAll(manager.getCurrentDownloads());
        }
        Collections.sort(out, (a, b) -> Long.compare(b.updateTimeMs, a.updateTimeMs));
        return out;
    }

    // ------------------------------------------------------------------
    // Start / control downloads
    // ------------------------------------------------------------------

    public static void enqueue(Context context, String url, Map<String, String> headers, String title) {
        enqueue(context, url, headers, title, null);
    }

    public static void enqueue(Context context, String url, Map<String, String> headers, String title, @Nullable String thumbnailUrl) {
        init(context);
        saveHeaders(context, url, headers);
        saveMetadata(context, url, title, thumbnailUrl);

        if (thumbnailUrl != null && !thumbnailUrl.trim().isEmpty()) {
            fetchThumbnailFromUrl(context, url, thumbnailUrl.trim());
        }

        Uri uri = Uri.parse(url);
        String mimeType = guessMimeType(url);

        DownloadRequest request = new DownloadRequest.Builder(url, uri)
                .setMimeType(mimeType)
                .setData(title.getBytes(StandardCharsets.UTF_8))
                .build();

        DownloadService.sendAddDownload(context, RasDownloadService.class, request, 0, true);
    }

    private static String guessMimeType(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        int q = lower.indexOf('?');
        if (q >= 0) lower = lower.substring(0, q);
        if (lower.endsWith(".m3u8")) return "application/x-mpegURL";
        if (lower.endsWith(".mpd")) return "application/dash+xml";
        return null;
    }

    public static void pause(Context context, Download download) {
        DownloadService.sendSetStopReason(context, RasDownloadService.class,
                download.request.id, USER_STOP_REASON, false);
    }

    public static void resume(Context context, Download download) {
        DownloadService.sendSetStopReason(context, RasDownloadService.class,
                download.request.id, 0, false);
    }

    public static void remove(Context context, Download download) {
        DownloadService.sendRemoveDownload(context, RasDownloadService.class,
                download.request.id, false);
        deleteThumbnail(context, download);
        deleteLocalVideo(context, download);
        removeMetadata(context, download.request.id);
    }

    public static void retry(Context context, Download download) {
        DownloadService.sendRemoveDownload(context, RasDownloadService.class,
                download.request.id, false);
        DownloadService.sendAddDownload(context, RasDownloadService.class,
                download.request, 0, true);
    }

    public static boolean hasUnfinished(Context context) {
        init(context);
        if (!isReady()) return false;
        try (DownloadCursor cursor = manager.getDownloadIndex().getDownloads(Download.STATE_QUEUED, Download.STATE_DOWNLOADING, Download.STATE_RESTARTING)) {
            if (cursor.getCount() > 0) return true;
        } catch (Exception ignored) {}
        for (Download d : manager.getCurrentDownloads()) {
            if (d.state != Download.STATE_COMPLETED && d.state != Download.STATE_FAILED) return true;
        }
        return false;
    }

    public static boolean isAlreadyAdded(Context context, String url) {
        init(context);
        if (!isReady()) return false;
        try {
            Download d = manager.getDownloadIndex().getDownload(url);
            if (d != null && d.state != Download.STATE_REMOVING) return true;
        } catch (Exception ignored) {}
        for (Download d : manager.getCurrentDownloads()) {
            if (url.equals(d.request.id)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Metadata Persistence (Title, Thumbnail URL, Local File Path)
    // ------------------------------------------------------------------

    private static void saveMetadata(Context context, String url, String title, @Nullable String thumbnailUrl) {
        try {
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_META, Context.MODE_PRIVATE);
            JSONObject json = new JSONObject();
            json.put("title", title != null ? title : "");
            json.put("thumbUrl", thumbnailUrl != null ? thumbnailUrl : "");
            prefs.edit().putString(keyForUrl(url), json.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static String getSavedTitle(Context context, String url) {
        try {
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_META, Context.MODE_PRIVATE);
            String raw = prefs.getString(keyForUrl(url), null);
            if (raw != null) {
                JSONObject json = new JSONObject(raw);
                String t = json.optString("title", "");
                if (!t.isEmpty()) return t;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static String getSavedThumbUrl(Context context, String url) {
        try {
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_META, Context.MODE_PRIVATE);
            String raw = prefs.getString(keyForUrl(url), null);
            if (raw != null) {
                JSONObject json = new JSONObject(raw);
                return json.optString("thumbUrl", null);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static void saveLocalPath(Context context, String url, String path) {
        try {
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_META, Context.MODE_PRIVATE);
            String raw = prefs.getString(keyForUrl(url), null);
            JSONObject json = raw != null ? new JSONObject(raw) : new JSONObject();
            json.put("localPath", path != null ? path : "");
            prefs.edit().putString(keyForUrl(url), json.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static String getLocalPath(Context context, Download d) {
        try {
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_META, Context.MODE_PRIVATE);
            String raw = prefs.getString(keyOf(d), null);
            if (raw != null) {
                JSONObject json = new JSONObject(raw);
                String p = json.optString("localPath", "");
                if (!p.isEmpty()) return p;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void removeMetadata(Context context, String url) {
        try {
            context.getApplicationContext()
                    .getSharedPreferences(PREFS_META, Context.MODE_PRIVATE)
                    .edit()
                    .remove(keyForUrl(url))
                    .apply();
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------
    // Offline Video File Assembly / Location
    // ------------------------------------------------------------------

    public static File getOfflineFolder(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), "offline_videos");
        if (!dir.exists()) dir.mkdirs();
        try {
            File nomedia = new File(dir, ".nomedia");
            if (!nomedia.exists()) nomedia.createNewFile();
        } catch (Exception ignored) {}
        return dir;
    }

    public static File getLocalVideoFile(Context context, Download d) {
        String path = getLocalPath(context, d);
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (f.exists() && f.length() > 0) return f;
        }
        File f = new File(getOfflineFolder(context), keyOf(d) + ".mp4");
        if (f.exists() && f.length() > 0) return f;
        return null;
    }

    private static final Set<String> assembleJobs = new HashSet<>();

    public static void assembleOfflineVideoIfNeeded(Context context, Download d, @Nullable Runnable onDone) {
        if (d.state != Download.STATE_COMPLETED) return;
        Context app = context.getApplicationContext();

        String mime = guessMimeType(d.request.id);
        if (mime != null) {
            saveLocalPath(app, d.request.id, "Media3 Cache (HLS/DASH)");
            if (onDone != null) onDone.run();
            return;
        }

        File outFile = new File(getOfflineFolder(app), keyOf(d) + ".mp4");
        if (outFile.exists() && outFile.length() > 0) {
            saveLocalPath(app, d.request.id, outFile.getAbsolutePath());
            if (onDone != null) onDone.run();
            return;
        }

        String key = keyOf(d);
        synchronized (RasDownloads.class) {
            if (!assembleJobs.add(key)) return;
        }

        new Thread(() -> {
            try {
                if (offlineReadFactory != null) {
                    DataSource ds = offlineReadFactory.createDataSource();
                    long bytes = ds.open(new DataSpec(d.request.uri));
                    if (bytes != 0) {
                        try (FileOutputStream fos = new FileOutputStream(outFile)) {
                            byte[] buffer = new byte[128 * 1024];
                            int read;
                            while ((read = ds.read(buffer, 0, buffer.length)) != androidx.media3.common.C.RESULT_END_OF_INPUT) {
                                fos.write(buffer, 0, read);
                            }
                            fos.flush();
                        }
                        if (outFile.exists() && outFile.length() > 0) {
                            saveLocalPath(app, d.request.id, outFile.getAbsolutePath());
                        }
                    }
                    try { ds.close(); } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                if (outFile.exists()) outFile.delete();
            } finally {
                synchronized (RasDownloads.class) {
                    assembleJobs.remove(key);
                }
                if (onDone != null) {
                    onDone.run();
                }
            }
        }, "ras-assemble-video").start();
    }

    public static void deleteLocalVideo(Context context, Download d) {
        try {
            File f = getLocalVideoFile(context, d);
            if (f != null && f.exists()) f.delete();
        } catch (Exception ignored) {}
    }

    public static void showCompletedNotification(Context context, Download d) {
        try {
            String title = titleOf(context, d);
            Intent intent = new Intent(context, MainActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("open_downloads", true);
            PendingIntent pi = PendingIntent.getActivity(context, d.request.id.hashCode() + 2000, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.app_icon)
                    .setContentTitle("ডাউনলোড সম্পন্ন: " + title)
                    .setContentText("প্লে করতে ট্যাপ করুন")
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .setPriority(NotificationCompat.PRIORITY_HIGH);

            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(d.request.id.hashCode() + 10000, builder.build());
            }
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------
    // Labels and progress
    // ------------------------------------------------------------------

    public static String titleOf(Context context, Download download) {
        String saved = getSavedTitle(context, download.request.id);
        if (saved != null && !saved.isEmpty()) return saved;
        return titleOf(download);
    }

    public static String titleOf(Download download) {
        if (download.request.data != null && download.request.data.length > 0) {
            return new String(download.request.data, StandardCharsets.UTF_8);
        }
        return titleFromUrl(download.request.id);
    }

    public static String titleFromUrl(String url) {
        try {
            String last = Uri.parse(url).getLastPathSegment();
            if (last != null && !last.trim().isEmpty()) return Uri.decode(last);
        } catch (Exception ignored) {}
        return "Video";
    }

    public static int percentOf(Download d) {
        float p = d.getPercentDownloaded();
        return p >= 0 ? Math.round(p) : -1;
    }

    public static String stateLabel(Download d) {
        String size = megabytes(d.getBytesDownloaded());
        switch (d.state) {
            case Download.STATE_QUEUED:
                return "অপেক্ষায় আছে";
            case Download.STATE_DOWNLOADING: {
                int p = percentOf(d);
                return p >= 0
                        ? "ডাউনলোড হচ্ছে • " + p + "% • " + size
                        : "ডাউনলোড হচ্ছে • " + size;
            }
            case Download.STATE_STOPPED: {
                if (d.stopReason == 0) return "অপেক্ষায় আছে";
                int p = percentOf(d);
                String text = "থামানো আছে";
                if (p >= 0) text += " • " + p + "%";
                return text + " • " + size;
            }
            case Download.STATE_COMPLETED:
                return "সম্পূর্ণ হয়েছে • " + size;
            case Download.STATE_FAILED:
                return "ব্যর্থ হয়েছে, আবার চেষ্টা করুন";
            case Download.STATE_REMOVING:
                return "মুছে ফেলা হচ্ছে";
            case Download.STATE_RESTARTING:
                return "আবার শুরু হচ্ছে";
            default:
                return "";
        }
    }

    private static String megabytes(long bytes) {
        return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
    }

    // ------------------------------------------------------------------
    // Per-host request headers (needed by some providers)
    // ------------------------------------------------------------------

    private static void saveHeaders(Context context, String url, Map<String, String> headers) {
        try {
            String host = Uri.parse(url).getHost();
            if (host == null || headers == null || headers.isEmpty()) return;
            JSONObject json = new JSONObject();
            for (Map.Entry<String, String> e : headers.entrySet()) {
                json.put(e.getKey(), e.getValue());
            }
            context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(host, json.toString())
                    .apply();
        } catch (Exception ignored) {}
    }

    static Map<String, String> headersFor(Context context, @Nullable String host) {
        Map<String, String> out = new HashMap<>();
        if (host == null) return out;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String raw = prefs.getString(host, null);
            if (raw == null) return out;
            JSONObject json = new JSONObject(raw);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                out.put(key, json.getString(key));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static final class HeaderFactory implements DataSource.Factory {
        private final Context context;
        private final OkHttpDataSource.Factory okFactory;

        HeaderFactory(Context context, OkHttpDataSource.Factory okFactory) {
            this.context = context;
            this.okFactory = okFactory;
        }

        @Override
        public DataSource createDataSource() {
            return new HeaderDataSource(context, okFactory.createDataSource());
        }
    }

    private static final class HeaderDataSource implements DataSource {
        private final Context context;
        private final DataSource delegate;

        HeaderDataSource(Context context, DataSource delegate) {
            this.context = context;
            this.delegate = delegate;
        }

        @Override
        public void addTransferListener(TransferListener transferListener) {
            delegate.addTransferListener(transferListener);
        }

        @Override
        public long open(DataSpec dataSpec) throws IOException {
            Map<String, String> headers = headersFor(context, dataSpec.uri.getHost());
            DataSpec spec = headers.isEmpty()
                    ? dataSpec
                    : dataSpec.buildUpon().setHttpRequestHeaders(headers).build();
            return delegate.open(spec);
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return delegate.read(buffer, offset, length);
        }

        @Nullable
        @Override
        public Uri getUri() {
            return delegate.getUri();
        }

        @Override
        public Map<String, List<String>> getResponseHeaders() {
            return delegate.getResponseHeaders();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    // ------------------------------------------------------------------
    // Thumbnails (Remote Banner or Video Frame)
    // ------------------------------------------------------------------

    private static final Map<String, Bitmap> thumbBitmaps = new HashMap<>();
    private static final Set<String> thumbJobs = new HashSet<>();

    private static File thumbDir(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), "offline_thumbs");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static String keyForUrl(String url) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1")
                    .digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(url.hashCode());
        }
    }

    public static String keyOf(Download d) {
        return keyForUrl(d.request.id);
    }

    private static File thumbFile(Context context, Download d) {
        return new File(thumbDir(context), keyOf(d) + ".jpg");
    }

    private static File thumbFileForUrl(Context context, String url) {
        return new File(thumbDir(context), keyForUrl(url) + ".jpg");
    }

    private static File failFile(Context context, Download d) {
        return new File(thumbDir(context), keyOf(d) + ".fail");
    }

    public static void fetchThumbnailFromUrl(Context context, String url, String thumbnailUrl) {
        if (thumbnailUrl == null || thumbnailUrl.trim().isEmpty()) return;
        Context app = context.getApplicationContext();
        File thumb = thumbFileForUrl(app, url);
        if (thumb.exists() && thumb.length() > 0) return;

        new Thread(() -> {
            try {
                URL u = new URL(thumbnailUrl);
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoInput(true);
                conn.connect();
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(thumb)) {
                    Bitmap bmp = BitmapFactory.decodeStream(in);
                    if (bmp != null) {
                        Bitmap small = scaleTo(bmp, 480);
                        small.compress(Bitmap.CompressFormat.JPEG, 85, out);
                        synchronized (RasDownloads.class) {
                            thumbBitmaps.put(keyForUrl(url), small);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }, "ras-thumb-fetch").start();
    }

    public static synchronized Bitmap loadThumbnail(Context context, Download d) {
        String key = keyOf(d);
        Bitmap cached = thumbBitmaps.get(key);
        if (cached != null) return cached;
        File f = thumbFile(context, d);
        if (!f.exists()) return null;
        Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (bmp != null) thumbBitmaps.put(key, bmp);
        return bmp;
    }

    public static synchronized void deleteThumbnail(Context context, Download d) {
        thumbBitmaps.remove(keyOf(d));
        thumbFile(context, d).delete();
        failFile(context, d).delete();
    }

    public static void generateThumbnailIfNeeded(Context context, Download d, @Nullable Runnable onDone) {
        if (d.state != Download.STATE_COMPLETED) return;
        Context app = context.getApplicationContext();
        if (thumbFile(app, d).exists() || failFile(app, d).exists()) {
            if (onDone != null) onDone.run();
            return;
        }

        String savedThumbUrl = getSavedThumbUrl(app, d.request.id);
        if (savedThumbUrl != null && !savedThumbUrl.trim().isEmpty()) {
            fetchThumbnailFromUrl(app, d.request.id, savedThumbUrl.trim());
            if (thumbFile(app, d).exists()) {
                if (onDone != null) onDone.run();
                return;
            }
        }

        String key = keyOf(d);
        synchronized (RasDownloads.class) {
            if (!thumbJobs.add(key)) return;
        }

        new Thread(() -> {
            boolean ok = false;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                String lower = d.request.id.toLowerCase(Locale.ROOT);
                if (lower.contains(".m3u8") || lower.contains(".mpd")) {
                    throw new IOException("segmented stream");
                }
                retriever.setDataSource(new CacheMediaSource(Uri.parse(d.request.id), offlineReadFactory));

                String durText = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                long durationMs = durText == null ? 0 : Long.parseLong(durText);
                long atMs = durationMs > 0 ? Math.min(durationMs / 10, 30_000) : 2_000;

                Bitmap frame = retriever.getFrameAtTime(
                        atMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (frame == null) frame = retriever.getFrameAtTime(0);
                if (frame != null) {
                    Bitmap small = scaleTo(frame, 480);
                    try (FileOutputStream out = new FileOutputStream(thumbFile(app, d))) {
                        small.compress(Bitmap.CompressFormat.JPEG, 85, out);
                    }
                    ok = true;
                }
            } catch (Exception ignored) {
            } finally {
                try { retriever.release(); } catch (Exception ignored) {}
            }

            if (!ok) {
                try { failFile(app, d).createNewFile(); } catch (IOException ignored) {}
            }
            synchronized (RasDownloads.class) {
                thumbJobs.remove(key);
            }
            if (onDone != null) onDone.run();
        }, "ras-thumb").start();
    }

    private static Bitmap scaleTo(Bitmap src, int width) {
        if (src.getWidth() <= width) return src;
        int height = Math.round(src.getHeight() * (width / (float) src.getWidth()));
        return Bitmap.createScaledBitmap(src, width, height, true);
    }

    private static final class CacheMediaSource extends MediaDataSource {
        private final Uri uri;
        private final CacheDataSource.Factory factory;
        private DataSource source;
        private long position = -1;
        private long length = -2;

        CacheMediaSource(Uri uri, CacheDataSource.Factory factory) {
            this.uri = uri;
            this.factory = factory;
        }

        @Override
        public synchronized long getSize() throws IOException {
            if (length == -2) {
                closeSource();
                source = factory.createDataSource();
                length = source.open(new DataSpec(uri));
                position = 0;
            }
            return length;
        }

        @Override
        public synchronized int readAt(long pos, byte[] buffer, int offset, int size) throws IOException {
            if (length >= 0 && pos >= length) return -1;
            if (source == null || pos != position) {
                closeSource();
                source = factory.createDataSource();
                source.open(new DataSpec(uri, pos, androidx.media3.common.C.LENGTH_UNSET));
                position = pos;
            }
            int read = source.read(buffer, offset, size);
            if (read == androidx.media3.common.C.RESULT_END_OF_INPUT) return -1;
            position += read;
            return read;
        }

        @Override
        public synchronized void close() {
            closeSource();
        }

        private void closeSource() {
            if (source != null) {
                try { source.close(); } catch (IOException ignored) {}
                source = null;
            }
        }
    }
}