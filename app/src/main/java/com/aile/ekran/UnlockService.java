package com.aile.ekran;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Çocuk telefonunda sürekli çalışır. Kilit açılınca (ACTION_USER_PRESENT)
 * sunucuya "unlock" olayı gönderir; sunucu da Telegram'dan ebeveyne bildirim atar.
 */
public class UnlockService extends Service {
    private BroadcastReceiver receiver;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        Notification n = Util.notif(this, "Telefon açılınca ebeveyne haber verilir");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                sendUnlock();
            }
        };
        registerReceiver(receiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
    }

    private void sendUnlock() {
        final long ts = System.currentTimeMillis();
        ex.execute(() -> {
            String body = "{\"type\":\"unlock\",\"ts\":" + ts + "}";
            for (int i = 0; i < 24; i++) { // ebeveyn telefonu geçici kapalıysa ~2 dk dener
                try {
                    Net.post(UnlockService.this, "/event", "application/json", body.getBytes("UTF-8"));
                    return;
                } catch (Exception e) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        return;
                    }
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
        ex.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
