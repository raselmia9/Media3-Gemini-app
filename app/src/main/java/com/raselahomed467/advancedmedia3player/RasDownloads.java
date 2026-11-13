package com.raselahomed467.advancedmedia3player;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.Nullable;
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
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.offline.DownloadService;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final String KEY_PRIVATE_STORAGE = "private_storage_v2";
    private static final int USER_STOP_REASON = 1;

    private static DownloadManager manager;
    private static Cache cache;
    private static CacheDataSource.Factory offlineReadFactory;

    private RasDownloads() {}

    public static synchronized void init(Context context) {
        if (manager != null) return;

        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean firstPrivateRun = !prefs.getBoolean(KEY_PRIVATE_STORAGE, false);
        if (firstPrivateRun) {
            // Downloads from the older build lived in a folder the file manager could see.
            // They cannot be played from the new private folder, so that old folder is removed once.
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

        // Download network calls: our headers are added per host, then OkHttp does the request.
        OkHttpDataSource.Factory okFactory = new OkHttpDataSource.Factory(new OkHttpClient.Builder().build());
        CacheDataSource.Factory downloadCacheFactory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(new HeaderFactory(app, okFactory));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        DefaultDownloaderFactory downloaderFactory = new DefaultDownloaderFactory(downloadCacheFactory, executor);

        manager = new DownloadManager(app, new DefaultDownloadIndex(databaseProvider), downloaderFactory);

        if (firstPrivateRun) {
            // Remove the index entries that pointed to the old folder.
            manager.removeAllDownloads();
            prefs.edit().putBoolean(KEY_PRIVATE_STORAGE, true).commit();
        }

        // Offline playback: read only from the cache, never from the network.
        offlineReadFactory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(null);
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

    /** All downloads, newest activity first. Empty until the list is loaded. */
    public static List<Download> listForPage() {
        List<Download> out = new ArrayList<>();
        if (!isReady()) return out;
        out.addAll(manager.getCurrentDownloads());
        Collections.sort(out, (a, b) -> Long.compare(b.updateTimeMs, a.updateTimeMs));
        return out;
    }

    // ------------------------------------------------------------------
    // Start / control downloads
    // ------------------------------------------------------------------

    public static void enqueue(Context context, String url, Map<String, String> headers, String title) {
        init(context);
        saveHeaders(context, url, headers);

        Uri uri = Uri.parse(url);
        String mimeType = guessMimeType(url);

        DownloadRequest request = new DownloadRequest.Builder(url, uri)
                .setMimeType(mimeType)
                .setData(title.getBytes(StandardCharsets.UTF_8))
                .build();

        DownloadService.sendAddDownload(context, RasDownloadService.class, request, 0, true);
    }

    // Returns the MIME type for HLS / DASH streams, or null for progressive files
    // (Media3 infers the type from the URL when this is null).
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
    }

    /** Failed downloads cannot simply resume, so remove them and add the same request again. */
    public static void retry(Context context, Download download) {
        DownloadService.sendRemoveDownload(context, RasDownloadService.class,
                download.request.id, false);
        DownloadService.sendAddDownload(context, RasDownloadService.class,
                download.request, 0, true);
    }

    /** Resumes whatever is still unfinished when the app starts again. */
    public static boolean hasUnfinished(Context context) {
        init(context);
        if (!isReady()) return false;
        for (Download d : manager.getCurrentDownloads()) {
            if (d.state != Download.STATE_COMPLETED) return true;
        }
        return false;
    }

    public static boolean isAlreadyAdded(Context context, String url) {
        init(context);
        if (!isReady()) return false;
        for (Download d : manager.getCurrentDownloads()) {
            if (url.equals(d.request.id)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Labels and progress
    // ------------------------------------------------------------------

    public static String titleOf(Download download) {
        if (download.request.data != null && download.request.data.length > 0) {
            return new String(download.request.data, StandardCharsets.UTF_8);
        }
        return download.request.id;
    }

    public static String titleFromUrl(String url) {
        try {
            String last = Uri.parse(url).getLastPathSegment();
            if (last != null && !last.trim().isEmpty()) return Uri.decode(last);
        } catch (Exception ignored) {}
        return "Video";
    }

    /** Percent downloaded (0-100), or -1 while the total size is still unknown. */
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
    // Thumbnails, made from the finished local copy
    // ------------------------------------------------------------------

    private static final Map<String, android.graphics.Bitmap> thumbBitmaps = new HashMap<>();
    private static final java.util.Set<String> thumbJobs = new java.util.HashSet<>();

    private static File thumbDir(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), "offline_thumbs");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static String keyOf(Download d) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-1")
                    .digest(d.request.id.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(d.request.id.hashCode());
        }
    }

    private static File thumbFile(Context context, Download d) {
        return new File(thumbDir(context), keyOf(d) + ".jpg");
    }

    private static File failFile(Context context, Download d) {
        return new File(thumbDir(context), keyOf(d) + ".fail");
    }

    /** The saved thumbnail, or null when none has been made yet. */
    public static synchronized android.graphics.Bitmap loadThumbnail(Context context, Download d) {
        String key = keyOf(d);
        android.graphics.Bitmap cached = thumbBitmaps.get(key);
        if (cached != null) return cached;
        File f = thumbFile(context, d);
        if (!f.exists()) return null;
        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
        if (bmp != null) thumbBitmaps.put(key, bmp);
        return bmp;
    }

    public static synchronized void deleteThumbnail(Context context, Download d) {
        thumbBitmaps.remove(keyOf(d));
        thumbFile(context, d).delete();
        failFile(context, d).delete();
    }

    /**
     * Makes a thumbnail from a completed single-file download (MP4, MKV and similar).
     * Segmented streams (HLS / DASH) keep the placeholder. Runs in the background.
     */
    public static void generateThumbnailIfNeeded(Context context, Download d, Runnable onDone) {
        if (d.state != Download.STATE_COMPLETED) return;
        Context app = context.getApplicationContext();
        if (thumbFile(app, d).exists() || failFile(app, d).exists()) return;

        String key = keyOf(d);
        synchronized (RasDownloads.class) {
            if (!thumbJobs.add(key)) return;
        }

        new Thread(() -> {
            boolean ok = false;
            android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
            try {
                String lower = d.request.id.toLowerCase(Locale.ROOT);
                if (lower.contains(".m3u8") || lower.contains(".mpd")) {
                    throw new IOException("segmented stream");
                }
                retriever.setDataSource(new CacheMediaSource(Uri.parse(d.request.id), offlineReadFactory));

                String durText = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                long durationMs = durText == null ? 0 : Long.parseLong(durText);
                long atMs = durationMs > 0 ? Math.min(durationMs / 10, 30_000) : 2_000;

                android.graphics.Bitmap frame = retriever.getFrameAtTime(
                        atMs * 1000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (frame == null) frame = retriever.getFrameAtTime(0);
                if (frame != null) {
                    android.graphics.Bitmap small = scaleTo(frame, 480);
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(thumbFile(app, d))) {
                        small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out);
                    }
                    ok = true;
                }
            } catch (Exception ignored) {
                // No frame could be read; the placeholder stays.
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

    private static android.graphics.Bitmap scaleTo(android.graphics.Bitmap src, int width) {
        if (src.getWidth() <= width) return src;
        int height = Math.round(src.getHeight() * (width / (float) src.getWidth()));
        return android.graphics.Bitmap.createScaledBitmap(src, width, height, true);
    }

    /** Lets the metadata reader read the cached copy of a finished download. */
    private static final class CacheMediaSource extends android.media.MediaDataSource {
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
