package com.raselahomed467.advancedmedia3player;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.media3.exoplayer.scheduler.Scheduler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps movie and series downloads running in the background, shows multi-card
 * notifications with movie poster, title, single-line status, full progress bar,
 * and individual Pause / Play / Stop controls.
 */
public class RasDownloadService extends DownloadService {

    private static final String CHANNEL_ID = "ras_downloads";
    private static final String PKG = "com.raselahomed467.advancedmedia3player";
    private static final String GROUP_KEY = "ras_downloads_group";

    static final String ACTION_PAUSE_ALL = PKG + ".DOWNLOAD_PAUSE_ALL";
    static final String ACTION_RESUME_ALL = PKG + ".DOWNLOAD_RESUME_ALL";
    static final String ACTION_STOP_ALL = PKG + ".DOWNLOAD_STOP_ALL";

    static final String ACTION_PAUSE_ITEM = PKG + ".DOWNLOAD_PAUSE_ITEM";
    static final String ACTION_RESUME_ITEM = PKG + ".DOWNLOAD_RESUME_ITEM";
    static final String ACTION_STOP_ITEM = PKG + ".DOWNLOAD_STOP_ITEM";
    static final String EXTRA_DOWNLOAD_ID = "download_id";

    private final Set<Integer> previousActiveNotifIds = new HashSet<>();

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
        if (ACTION_PAUSE_ITEM.equals(action)) {
            String id = intent.getStringExtra(EXTRA_DOWNLOAD_ID);
            if (id != null) {
                DownloadService.sendSetStopReason(this, RasDownloadService.class, id, 1, false);
            }
            return START_STICKY;
        }
        if (ACTION_RESUME_ITEM.equals(action)) {
            String id = intent.getStringExtra(EXTRA_DOWNLOAD_ID);
            if (id != null) {
                DownloadService.sendSetStopReason(this, RasDownloadService.class, id, 0, false);
            }
            return START_STICKY;
        }
        if (ACTION_STOP_ITEM.equals(action)) {
            String id = intent.getStringExtra(EXTRA_DOWNLOAD_ID);
            if (id != null) {
                DownloadService.sendRemoveDownload(this, RasDownloadService.class, id, false);
                try {
                    int notifId = getNotifId(id);
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm != null) nm.cancel(notifId);
                } catch (Exception ignored) {}
            }
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

    private int getNotifId(String downloadId) {
        int id = Math.abs(downloadId.hashCode());
        if (id == 1001) id = 1002;
        return id;
    }

    @Override
    protected Notification getForegroundNotification(List<Download> downloads, int notMetRequirements) {
        int active = 0;
        int downloading = 0;
        int stopped = 0;
        int knownCount = 0;
        float percentSum = 0f;
        Download single = null;

        Set<Integer> currentActiveIds = new HashSet<>();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

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

        // When multiple items are active, post distinct notification cards for each active download
        if (active > 1 && nm != null) {
            for (Download d : downloads) {
                if (d.state == Download.STATE_COMPLETED || d.state == Download.STATE_FAILED) continue;
                int notifId = getNotifId(d.request.id);
                currentActiveIds.add(notifId);

                NotificationCompat.Builder subBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.app_icon)
                        .setContentIntent(openAppIntent())
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setGroup(GROUP_KEY);

                Bitmap thumb = RasDownloads.loadThumbnail(this, d);
                if (thumb != null) {
                    subBuilder.setLargeIcon(thumb);
                }

                subBuilder.setContentTitle(RasDownloads.titleOf(this, d));
                subBuilder.setContentText(RasDownloads.statusLineOf(d));

                int p = RasDownloads.percentOf(d);
                if (p >= 0) {
                    subBuilder.setProgress(100, p, false);
                } else {
                    subBuilder.setProgress(0, 0, true);
                }

                if (d.state == Download.STATE_DOWNLOADING) {
                    subBuilder.addAction(android.R.drawable.ic_media_pause, "❚❚ পজ",
                            itemActionIntent(ACTION_PAUSE_ITEM, d.request.id, notifId * 10 + 1));
                } else if (d.state == Download.STATE_STOPPED) {
                    subBuilder.addAction(android.R.drawable.ic_media_play, "▶ প্লে",
                            itemActionIntent(ACTION_RESUME_ITEM, d.request.id, notifId * 10 + 2));
                }
                subBuilder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "✕ স্টপ",
                        itemActionIntent(ACTION_STOP_ITEM, d.request.id, notifId * 10 + 3));

                nm.notify(notifId, subBuilder.build());
            }
        }

        // Clean up sub-notifications that are no longer active
        if (nm != null) {
            for (int oldId : previousActiveNotifIds) {
                if (!currentActiveIds.contains(oldId)) {
                    nm.cancel(oldId);
                }
            }
        }
        previousActiveNotifIds.clear();
        previousActiveNotifIds.addAll(currentActiveIds);

        // Build the foreground service notification
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

        if (active == 1 && single != null) {
            // Single download card
            Bitmap thumb = RasDownloads.loadThumbnail(this, single);
            if (thumb != null) {
                builder.setLargeIcon(thumb);
            }

            builder.setContentTitle(RasDownloads.titleOf(this, single));
            builder.setContentText(RasDownloads.statusLineOf(single));

            int p = RasDownloads.percentOf(single);
            if (p >= 0) {
                builder.setProgress(100, p, false);
            } else {
                builder.setProgress(0, 0, true);
            }

            if (single.state == Download.STATE_DOWNLOADING) {
                builder.addAction(android.R.drawable.ic_media_pause, "❚❚ পজ",
                        actionIntent(ACTION_PAUSE_ALL, 1));
            } else if (single.state == Download.STATE_STOPPED) {
                builder.addAction(android.R.drawable.ic_media_play, "▶ প্লে",
                        actionIntent(ACTION_RESUME_ALL, 2));
            }
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "✕ স্টপ",
                    actionIntent(ACTION_STOP_ALL, 3));

            return builder.build();
        }

        // Multiple downloads summary
        int percent = knownCount > 0 ? Math.round(percentSum / knownCount) : -1;
        builder.setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setContentTitle(active + "টি ডাউনলোড চলছে")
                .setContentText(percent >= 0 ? "মোট গড় প্রগ্রেস • " + percent + "%" : "ডাউনলোড চলছে...");

        if (percent >= 0) {
            builder.setProgress(100, percent, false);
        } else {
            builder.setProgress(0, 0, true);
        }

        if (downloading > 0) {
            builder.addAction(android.R.drawable.ic_media_pause, "সব পজ করুন",
                    actionIntent(ACTION_PAUSE_ALL, 1));
        }
        if (stopped > 0) {
            builder.addAction(android.R.drawable.ic_media_play, "সব প্লে করুন",
                    actionIntent(ACTION_RESUME_ALL, 2));
        }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "সব বাতিল",
                    actionIntent(ACTION_STOP_ALL, 3));

        return builder.build();
    }

    private PendingIntent actionIntent(String action, int requestCode) {
        Intent intent = new Intent(this, RasDownloadService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent itemActionIntent(String action, String downloadId, int requestCode) {
        Intent intent = new Intent(this, RasDownloadService.class)
                .setAction(action)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId);
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
