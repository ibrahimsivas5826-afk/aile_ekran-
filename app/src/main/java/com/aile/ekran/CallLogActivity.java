package com.aile.ekran;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Date;

/** Ebeveyn telefonu: çocuğun arama kayıtlarını (kiminle, ne zaman, kaç dakika) listeler. */
public class CallLogActivity extends Activity {
    private LinearLayout list;
    private TextView status;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(20));
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("Arama Kayıtları");
        title.setTextSize(22);
        root.addView(title);

        Button refresh = new Button(this);
        refresh.setText("Yenile");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load();
            }
        });
        root.addView(refresh);

        status = new TextView(this);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        setContentView(sv);
        load();
    }

    private String fmtDuration(long sec) {
        long m = sec / 60, s = sec % 60;
        return String.format("%d:%02d", m, s);
    }

    private void load() {
        status.setText("Yükleniyor…");
        list.removeAllViews();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String body = Net.get(CallLogActivity.this, "/calls", 10000);
                    final JSONArray arr = new JSONArray(body);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            render(arr);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            status.setText("Alınamadı: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void render(JSONArray arr) {
        list.removeAllViews();
        if (arr.length() == 0) {
            status.setText("Henüz arama kaydı yok.");
            return;
        }
        status.setText(arr.length() + " kayıt");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String name = o.optString("name", "");
            String number = o.optString("number", "");
            String who = name.isEmpty() ? (number.isEmpty() ? "(numara yok)" : number) : name + " (" + number + ")";
            String type = o.optString("calltype", "");
            long date = o.optLong("date", 0);
            long dur = o.optLong("duration", 0);
            String when = date > 0
                    ? DateFormat.format("dd.MM.yyyy HH:mm", new Date(date)).toString()
                    : "";

            TextView row = new TextView(this);
            row.setPadding(0, dp(10), 0, dp(10));
            row.setTextColor(Color.BLACK);
            row.setText(when + "  —  " + who + "\n" + type + ", süre " + fmtDuration(dur));
            list.addView(row);

            View div = new View(this);
            div.setBackgroundColor(0x22000000);
            list.addView(div, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
        }
    }
}
