package com.aile.ekran;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Çocuk telefonunda sürekli çalışır. Kilit açılınca (ACTION_USER_PRESENT)
 * sunucuya "unlock" olayı gönderir; sunucu da ebeveyne bildirim atar.
 * Ayrıca arama kaydını izler (CallWatcher) ve yeni aramaları anında gönderir.
 */
public class UnlockService extends Service {
    private BroadcastReceiver receiver;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private HandlerThread callThread;
    private CallWatcher callWatcher;

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

        callThread = new HandlerThread("calllog");
        callThread.start();
        callWatcher = new CallWatcher(this, new Handler(callThread.getLooper()));
        callWatcher.start();
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
        // İzin sonradan verilmiş olabilir (ör. Ayarlar'dan dönüş); her başlatmada yeniden dene.
        if (callWatcher != null) callWatcher.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
        if (callWatcher != null) callWatcher.stop();
        if (callThread != null) callThread.quitSafely();
        ex.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
