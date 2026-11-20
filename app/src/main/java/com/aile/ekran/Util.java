package com.aile.ekran;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

class Util {
    static final String CHANNEL = "aile_ekran";

    static void ensureChannel(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                    new NotificationChannel(CHANNEL, "Aile Ekranı", NotificationManager.IMPORTANCE_LOW));
        }
    }

    static Notification notif(Context c, String text) {
        ensureChannel(c);
        return new Notification.Builder(c, CHANNEL)
                .setContentTitle("Aile Ekranı")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_app)
                .setOngoing(true)
                .build();
    }
}
