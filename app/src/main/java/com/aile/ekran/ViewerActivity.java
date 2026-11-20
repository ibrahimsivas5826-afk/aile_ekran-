package com.aile.ekran;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.net.HttpURLConnection;

/** Ebeveyn telefonu: çocuğun ekranını canlı gösterir. */
public class ViewerActivity extends Activity {
    private ImageView iv;
    private TextView tv;
    private volatile boolean running = false;
    private Thread worker;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(iv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        tv = new TextView(this);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(0xAA000000);
        tv.setPadding(32, 24, 32, 24);
        tv.setText("Çocuğun telefonuna bağlanılıyor…");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = 80;
        root.addView(tv, lp);

        setContentView(root);
    }

    @Override
    protected void onStart() {
        super.onStart();
        running = true;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "viewer");
        worker.start();
    }

    @Override
    protected void onStop() {
        running = false;
        if (worker != null) worker.interrupt();
        super.onStop();
    }

    private void setStatus(String s) {
        tv.setText(s);
        tv.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void status(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setStatus(s);
            }
        });
    }

    private void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private void loop() {
        long since = 0;
        while (running) {
            HttpURLConnection c = null;
            try {
                c = Net.open(this, "/frame?since=" + since, "GET", 12000);
                int code = c.getResponseCode();
                boolean childOn = "1".equals(c.getHeaderField("X-Child"));
                if (code == 200) {
                    since = Long.parseLong(c.getHeaderField("X-Seq"));
                    double age = 0;
                    try {
                        age = Double.parseDouble(c.getHeaderField("X-Age"));
                    } catch (Exception ignored) {
                    }
                    byte[] data = Net.readAll(c.getInputStream());
                    final Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length);
                    final String st;
                    if (!childOn) {
                        st = "Çocuğun telefonu bağlı değil — son görüntü (" + (int) age + " sn önce)";
                    } else if (age > 5) {
                        st = (int) age + " sn önceki görüntü";
                    } else {
                        st = "";
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (bm != null) iv.setImageBitmap(bm);
                            setStatus(st);
                        }
                    });
                } else if (code == 204) {
                    status(childOn
                            ? "Çocuğun telefonuna bağlanılıyor…"
                            : "Çocuğun telefonu bağlı değil (yayın kapalı ya da internet yok)");
                } else if (code == 401) {
                    status("Anahtar yanlış: iki telefonda ve sunucuda aynı olmalı");
                    pause(3000);
                } else {
                    status("Sunucu hatası: " + code);
                    pause(3000);
                }
            } catch (Exception e) {
                if (!running) break;
                status("Sunucuya bağlanılamıyor…");
                pause(2500);
            } finally {
                if (c != null) c.disconnect();
            }
        }
    }
}
