package com.example.alertuppp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Handles incoming FCM messages and displays them as system notifications.
 *
 * Payload expected (sent from Supabase Edge Function):
 *   notification.title  — alert title
 *   notification.body   — alert message
 *   data.level          — "danger" | "warning" | "info"
 *   data.area           — affected area string
 */
public class AlertFirebaseMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID   = "alertup_alerts";
    private static final String CHANNEL_NAME = "Emergency Alerts";
    private static final int    NOTIF_ID     = 1001;

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        String title = "Emergency Alert";
        String body  = "";

        // Prefer notification payload (set by FCM console / Edge Function)
        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null)
                title = message.getNotification().getTitle();
            if (message.getNotification().getBody() != null)
                body = message.getNotification().getBody();
        }

        // Fall back to data payload
        if (body.isEmpty() && message.getData().containsKey("body"))
            body = message.getData().get("body");
        if (message.getData().containsKey("title"))
            title = message.getData().get("title");

        String level = message.getData().getOrDefault("level", "info");
        showNotification(title, body, level);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        // Save the new token so it can be sent to Supabase on next login
        getSharedPreferences("alertup_fcm", Context.MODE_PRIVATE)
                .edit()
                .putString("fcm_token", token)
                .apply();
    }

    private void showNotification(String title, String body, String level) {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Create channel (required on Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = "danger".equals(level)
                    ? NotificationManager.IMPORTANCE_HIGH
                    : NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel =
                    new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance);
            channel.setDescription("AlertUp emergency notifications");
            nm.createNotificationChannel(channel);
        }

        // Tap opens the app
        Intent intent = new Intent(this, SplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        int iconRes = android.R.drawable.ic_dialog_alert;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(iconRes)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi);

        if ("danger".equals(level)) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        }

        nm.notify(NOTIF_ID, builder.build());
    }
}
