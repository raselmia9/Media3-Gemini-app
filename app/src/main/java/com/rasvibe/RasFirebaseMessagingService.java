package com.rasvibe;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class RasFirebaseMessagingService extends FirebaseMessagingService {
    public static final String CHANNEL_ID = "announcements";

    @Override
    public void onMessageReceived(RemoteMessage msg) {
        String title = null, body = null;
        if (msg.getNotification() != null) {
            title = msg.getNotification().getTitle();
            body = msg.getNotification().getBody();
        }
        if (title == null) title = msg.getData().get("title");
        if (body == null) body = msg.getData().get("body");
        if (title == null && body == null) return;
        show(this, title != null ? title : "RasVibe", body != null ? body : "");
    }

    @Override
    public void onNewToken(String token) {
        // topic ব্যবহার করলে token সংরক্ষণের দরকার নেই
    }

    public static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Announcements", NotificationManager.IMPORTANCE_HIGH);
            c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private static void show(Context c, String title, String body) {
        createChannel(c);
        Intent i = new Intent(c, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);
        try {
            NotificationManagerCompat.from(c).notify((int) System.currentTimeMillis(), b.build());
        } catch (SecurityException ignored) { }
    }
}
