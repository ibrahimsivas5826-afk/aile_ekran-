package com.aile.ekran;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Tailscale uygulamasıyla ilgili yardımcılar: kurulu mu, aç/kur, authkey ile sessizce ağa katıl. */
class TailscaleHelper {
    private static final String PKG = "com.tailscale.ipn";

    static boolean isInstalled(Context c) {
        return c.getPackageManager().getLaunchIntentForPackage(PKG) != null;
    }

    /** Kuruluysa açar, değilse Play Store'a (yoksa tarayıcıya) yönlendirir. */
    static void openOrInstall(Context c) {
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(PKG);
        if (launch != null) {
            if (!(c instanceof android.app.Activity)) launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(launch);
            return;
        }
        try {
            Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + PKG));
            if (!(c instanceof android.app.Activity)) market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(market);
        } catch (Exception e) {
            Intent web = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + PKG));
            if (!(c instanceof android.app.Activity)) web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(web);
        }
    }

    /**
     * Tailscale'in resmi olmayan ama kendi kodunda bulunan INIT_WITH_AUTHKEY broadcast'ini dener;
     * böyle bir cihaz zaten Tailscale hesabına girmeden, elle "oturum aç" yapmadan ağa katılabilir.
     * Tailscale bu yayını üçüncü taraf uygulamalara kapatmış olabilir (güvenlik için) — o yüzden bu
     * tamamen "iyi niyetli deneme": başarısız olursa sessizce yutulur, hiçbir hata göstermez.
     * @return gerçekten gönderilip gönderilmediği (ağa katılımın başarılı olduğu anlamına gelmez).
     */
    static boolean tryJoinWithAuthKey(Context c, String authKey) {
        if (authKey == null || authKey.trim().isEmpty()) return false;
        try {
            Intent i = new Intent("com.tailscale.ipn.INIT_WITH_AUTHKEY");
            i.setClassName(PKG, "com.tailscale.ipn.IPNReceiver");
            i.putExtra("authkey", authKey.trim());
            c.sendBroadcast(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
