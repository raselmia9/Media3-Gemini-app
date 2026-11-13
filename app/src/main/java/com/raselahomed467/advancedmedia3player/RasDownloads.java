package com.raselahomed467.advancedmedia3player;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.util.Util;
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
 * One DownloadManager, one cache and one database for the whole process, so the
 * activity and the download service always see the same downloads.
 */
public final class RasDownloads {

    private static final String HEADER_PREFS = "ras_download_headers";
    private static final int USER_STOP_REASON = 1;

    private static DownloadManager manager;
    private static Cache cache;
    private static CacheDataSource.Factory offlineReadFactory;

    private RasDownloads() {}

    public static synchronized void init(Context context) {
        if (manager != null) return;

        Context app = context.getApplicationContext();
        DatabaseProvider databaseProvider = new StandaloneDatabaseProvider(app);

        File base = app.getExternalFilesDir(null);
        if (base == null) base = app.getFilesDir();
        File cacheDir = new File(base, "downloads");
        cache = new SimpleCache(cacheDir, new NoOpCacheEvictor(), databaseProvider);

        // Download network calls: our headers are added per host, then OkHttp does the request.
        OkHttpDataSource.Factory okFactory = new OkHttpDataSource.Factory(new OkHttpClient.Builder().build());
        CacheDataSource.Factory downloadCacheFactory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(new HeaderFactory(app, okFactory));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        DefaultDownloaderFactory downloaderFactory = new DefaultDownloaderFactory(downloadCacheFactory, executor);

        manager = new DownloadManager(app, new DefaultDownloadIndex(databaseProvider), downloaderFactory);

        // Offline playback: read only from the cache, never from the network.
        offlineReadFactory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(null);
    }

    public static DownloadManager getManager() {
        return manager;
    }

    public static CacheDataSource.Factory getOfflineReadFactory() {
        return offlineReadFactory;
    }

    // ------------------------------------------------------------------
    // Start / control downloads
    // ------------------------------------------------------------------

    public static void enqueue(Context context, String url, Map<String, String> headers, String title) {
        init(context);
        saveHeaders(context, url, headers);

        Uri uri = Uri.parse(url);
        String mimeType = Util.inferContentTypeForUriAndMimeType(uri, null);

        DownloadRequest request = new DownloadRequest.Builder(url, uri)
                .setMimeType(mimeType)
                .setData(title.getBytes(StandardCharsets.UTF_8))
                .build();

        DownloadService.sendAddDownload(context, RasDownloadService.class, request, 0, true);
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
        for (Download d : manager.getCurrentDownloads()) {
            if (d.state != Download.STATE_COMPLETED) return true;
        }
        return false;
    }

    public static boolean isAlreadyAdded(Context context, String url) {
        init(context);
        for (Download d : manager.getCurrentDownloads()) {
            if (url.equals(d.request.id)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Labels
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

    public static String stateLabel(Download d) {
        switch (d.state) {
            case Download.STATE_QUEUED:
                return "অপেক্ষায় আছে";
            case Download.STATE_DOWNLOADING:
                return "ডাউনলোড হচ্ছে " + percentText(d);
            case Download.STATE_STOPPED:
                return d.stopReason == 0 ? "অপেক্ষায় আছে" : "থামানো আছে " + percentText(d);
            case Download.STATE_COMPLETED:
                return "সম্পূর্ণ হয়েছে • " + megabytes(d.getBytesDownloaded());
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

    private static String percentText(Download d) {
        float p = d.getPercentDownloaded();
        return p >= 0 ? String.format(Locale.US, "%.0f%%", p) : "";
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
                    .getSharedPreferences(HEADER_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(host, json.toString())
                    .apply();
        } catch (Exception ignored) {}
    }

    static Map<String, String> headersFor(Context context, @Nullable String host) {
        Map<String, String> out = new HashMap<>();
        if (host == null) return out;
        try {
            SharedPreferences prefs = context.getSharedPreferences(HEADER_PREFS, Context.MODE_PRIVATE);
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
}
