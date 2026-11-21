package com.aile.ekran;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONObject;

/**
 * Ebeveyn telefonunda: adres + anahtarı (ve istersen Tailscale erişim kodunu) bir QR koda çevirip gösterir.
 * Çocuğun telefonu bunu kamerayla okutunca elle yazmaya gerek kalmaz; erişim kodu da verilmişse
 * Tailscale'e hesaba girmeden otomatik katılım denenir.
 */
public class QrShowActivity extends Activity {
    private EditText tsKeyEt;
    private FrameLayout qrBox;
    private String addr, key;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 40, 40, 40);
        sv.addView(root);

        addr = Config.usesLocalRelay(this) ? firstLocalAddress() : Config.server(this);
        key = Config.key(this);

        if (addr == null || addr.isEmpty() || key.isEmpty()) {
            TextView status = new TextView(this);
            status.setText("Önce ana ekranda anahtarı yazıp Kaydet'e bas,\nTailscale'in bağlı olduğundan emin ol.");
            status.setGravity(Gravity.CENTER);
            status.setPadding(0, 200, 0, 0);
            root.addView(status);
            setContentView(sv);
            return;
        }

        qrBox = new FrameLayout(this);
        root.addView(qrBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(320)));

        TextView caption = new TextView(this);
        caption.setText("Çocuğun telefonunda \"QR ile Kur\" düğmesiyle bu kodu okut.\n\n" + addr);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, dp(12), 0, dp(24));
        root.addView(caption);

        TextView tsLabel = new TextView(this);
        tsLabel.setText("İsteğe bağlı: çocuğun telefonu Tailscale'e HİÇ GİRİŞ YAPMADAN otomatik katılsın.\n"
                + "login.tailscale.com/admin/settings/keys sayfasından bir erişim kodu (authkey) üret, "
                + "aşağıya yapıştır. Bu kodu kimseyle paylaşma; bittiğinde panelden iptal edebilirsin.");
        tsLabel.setPadding(0, dp(8), 0, dp(8));
        root.addView(tsLabel);

        root.addView(btn("Tailscale panelini aç (kod üretmek için)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://login.tailscale.com/admin/settings/keys")));
            }
        }));

        tsKeyEt = new EditText(this);
        tsKeyEt.setSingleLine(true);
        tsKeyEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        tsKeyEt.setHint("tskey-auth-... (boş bırakılabilir)");
        tsKeyEt.setText(Config.tsAuthKey(this));
        root.addView(tsKeyEt);

        root.addView(btn("QR'ı güncelle", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Config.setTsAuthKey(QrShowActivity.this, tsKeyEt.getText().toString().trim());
                drawQr();
            }
        }));

        setContentView(sv);
        drawQr();
    }

    private void drawQr() {
        qrBox.removeAllViews();
        String payload;
        try {
            JSONObject o = new JSONObject();
            o.put("addr", addr);
            o.put("key", key);
            String tsKey = tsKeyEt.getText().toString().trim();
            if (!tsKey.isEmpty()) o.put("tskey", tsKey);
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
            qrBox.addView(iv, lp);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private Button btn(String s, View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(s);
        bt.setOnClickListener(l);
        return bt;
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
