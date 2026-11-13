package com.raselahomed467.advancedmedia3player;

import android.app.Notification;

import androidx.annotation.Nullable;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadNotificationHelper;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.media3.exoplayer.scheduler.Scheduler;

import java.util.List;

/**
 * Keeps movie and series downloads running in the background and lets them
 * resume after the app is closed or restarted.
 */
public class RasDownloadService extends DownloadService {

    private static final String CHANNEL_ID = "ras_downloads";

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
    protected Notification getForegroundNotification(List<Download> downloads, int notMetRequirements) {
        return new DownloadNotificationHelper(this, CHANNEL_ID)
                .buildProgressNotification(
                        this,
                        R.drawable.app_icon,
                        null,
                        null,
                        downloads,
                        notMetRequirements);
    }
}
