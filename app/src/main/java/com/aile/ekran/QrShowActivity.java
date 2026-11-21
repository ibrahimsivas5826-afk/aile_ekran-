package com.aile.ekran;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONObject;

/**
 * Ebeveyn telefonunda: adres + anahtarı bir QR koda çevirip gösterir.
 * Çocuğun telefonu bunu kamerayla okutunca elle yazmaya gerek kalmaz.
 */
public class QrShowActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        String addr = Config.usesLocalRelay(this) ? firstLocalAddress() : Config.server(this);
        String key = Config.key(this);

        TextView status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setPadding(40, 40, 40, 40);

        if (addr == null || addr.isEmpty() || key.isEmpty()) {
            status.setText("Önce ana ekranda anahtarı yazıp Kaydet'e bas,\nTailscale'in bağlı olduğundan emin ol.");
            root.addView(status, center());
            setContentView(root);
            return;
        }

        String payload;
        try {
            JSONObject o = new JSONObject();
            o.put("addr", addr);
            o.put("key", key);
            payload = o.toString();
        } catch (Exception e) {
            payload = "";
        }

        Bitmap qr = makeQr(payload, 900);
        if (qr != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(qr);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER;
            root.addView(iv, lp);
        }

        TextView caption = new TextView(this);
        caption.setText("Çocuğun telefonunda \"QR ile Kur\" düğmesiyle bu kodu okut.\n\n" + addr);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(40, 0, 40, 40);
        FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp2.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        root.addView(caption, lp2);

        setContentView(root);
    }

    private FrameLayout.LayoutParams center() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        return lp;
    }

    private String firstLocalAddress() {
        try {
            for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                for (java.net.InetAddress a : java.util.Collections.list(ni.getInetAddresses())) {
                    if (a instanceof java.net.Inet4Address && !a.isLoopbackAddress()) {
                        String h = a.getHostAddress();
                        if (h != null && h.startsWith("100.")) {
                            return "http://" + h + ":" + RelayService.PORT;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Bitmap makeQr(String text, int size) {
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size);
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bmp.setPixel(x, y, m.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bmp;
        } catch (WriterException e) {
            return null;
        }
    }
}
