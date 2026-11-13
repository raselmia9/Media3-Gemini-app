package com.raselahomed467.advancedmedia3player;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.media3.exoplayer.scheduler.Scheduler;

import java.util.List;

/**
 * Keeps movie and series downloads running in the background, shows a progress
 * notification with Pause / Resume / Stop buttons, and resumes after the app
 * is closed or restarted.
 */
public class RasDownloadService extends DownloadService {

    private static final String CHANNEL_ID = "ras_downloads";
    private static final String PKG = "com.raselahomed467.advancedmedia3player";

    static final String ACTION_PAUSE_ALL = PKG + ".DOWNLOAD_PAUSE_ALL";
    static final String ACTION_RESUME_ALL = PKG + ".DOWNLOAD_RESUME_ALL";
    static final String ACTION_STOP_ALL = PKG + ".DOWNLOAD_STOP_ALL";

    public RasDownloadService() {
        super(1001, 1000L, CHANNEL_ID,
                R.string.download_channel_name,
                R.string.download_channel_description);
    }

    @Override
    protected DownloadManager getDownloadManager() {
        RasDownloads.init(this);
        return RasDownloads.getManager();
    }

    @Nullable
    @Override
    protected Scheduler getScheduler() {
        return null;
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_PAUSE_ALL.equals(action)) {
            DownloadService.sendPauseDownloads(this, RasDownloadService.class, false);
            return START_STICKY;
        }
        if (ACTION_RESUME_ALL.equals(action)) {
            DownloadService.sendResumeDownloads(this, RasDownloadService.class, false);
            return START_STICKY;
        }
        if (ACTION_STOP_ALL.equals(action)) {
            stopUnfinished();
            return START_STICKY;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /** Stop removes unfinished downloads. Completed downloads are kept. */
    private void stopUnfinished() {
        DownloadManager manager = RasDownloads.getManager();
        if (manager == null) return;
        for (Download d : manager.getCurrentDownloads()) {
            if (d.state != Download.STATE_COMPLETED) {
                DownloadService.sendRemoveDownload(this, RasDownloadService.class, d.request.id, false);
            }
        }
    }

    @Override
    protected Notification getForegroundNotification(List<Download> downloads, int notMetRequirements) {
        int active = 0;
        int downloading = 0;
        int stopped = 0;
        int knownCount = 0;
        float percentSum = 0f;
        Download single = null;

        for (Download d : downloads) {
            if (d.state == Download.STATE_COMPLETED || d.state == Download.STATE_FAILED) continue;
            active++;
            single = d;
            if (d.state == Download.STATE_DOWNLOADING) downloading++;
            if (d.state == Download.STATE_STOPPED) stopped++;
            int p = RasDownloads.percentOf(d);
            if (p >= 0) {
                percentSum += p;
                knownCount++;
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.app_icon)
                .setContentIntent(openAppIntent())
                .setOngoing(active > 0)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (active == 0) {
            builder.setContentTitle("ডাউনলোড সম্পন্ন")
                    .setContentText("Downloads পেজ থেকে দেখতে পারবেন")
                    .setProgress(0, 0, false);
            return builder.build();
        }

        int percent = knownCount > 0 ? Math.round(percentSum / knownCount) : -1;

        String title = active == 1
                ? RasDownloads.titleOf(this, single)
                : active + "টি ডাউনলোড চলছে";

        String status;
        if (downloading > 0) {
            status = "ডাউনলোড হচ্ছে";
        } else if (stopped > 0) {
            status = "থামানো আছে";
        } else {
            status = "অপেক্ষায় আছে";
        }
        String text = percent >= 0 ? status + " • " + percent + "%" : status;

        builder.setContentTitle(title)
                .setContentText(text);
        if (percent >= 0) {
            builder.setProgress(100, percent, false);
        } else {
            builder.setProgress(0, 0, true);
        }

        if (downloading > 0) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause",
                    actionIntent(ACTION_PAUSE_ALL, 1));
        }
        if (stopped > 0) {
            builder.addAction(android.R.drawable.ic_media_play, "Resume",
                    actionIntent(ACTION_RESUME_ALL, 2));
        }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop",
                actionIntent(ACTION_STOP_ALL, 3));

        return builder.build();
    }

    private PendingIntent actionIntent(String action, int requestCode) {
        Intent intent = new Intent(this, RasDownloadService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent openAppIntent() {
        Intent intent = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("open_downloads", true);
        return PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}