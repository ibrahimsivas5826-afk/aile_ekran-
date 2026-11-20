package com.aile.ekran;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Telefon yeniden açılınca (ya da uygulama güncellenince) kilit bildirimi servisini geri başlatır. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        try {
            if ("child".equals(Config.mode(c))) {
                c.startForegroundService(new Intent(c, UnlockService.class));
            } else if (Config.usesLocalRelay(c)) {
                c.startForegroundService(new Intent(c, RelayService.class));
            }
        } catch (Exception ignored) {
        }
    }
}
