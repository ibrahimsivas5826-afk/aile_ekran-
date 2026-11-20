package com.aile.ekran;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.ContactsContract;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Çocuk telefonunda arama kaydını izler. Her yeni arama bittiğinde Android onu
 * CallLog'a hemen yazar; biz de o anda (çocuk silmeden önce) sunucuya gönderiyoruz.
 * Sadece süre/numara/tür bilgisi — konuşma içeriği yok.
 */
class CallWatcher {
    private final Context app;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private ContentObserver observer;
    private final Handler handler;
    private volatile boolean registered = false;

    CallWatcher(Context c, Handler h) {
        app = c.getApplicationContext();
        handler = h;
    }

    boolean hasPermission() {
        return app.checkSelfPermission(android.Manifest.permission.READ_CALL_LOG)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Güvenle tekrar tekrar çağrılabilir: izin sonradan verilmişse o zaman devreye girer. */
    void start() {
        if (registered || !hasPermission()) return;
        registered = true;
        syncNew(); // açılışta kaçırılmış olanları da yakala
        observer = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                syncNew();
            }
        };
        try {
            app.getContentResolver().registerContentObserver(
                    CallLog.Calls.CONTENT_URI, true, observer);
        } catch (Exception ignored) {
        }
    }

    void stop() {
        if (observer != null) {
            try {
                app.getContentResolver().unregisterContentObserver(observer);
            } catch (Exception ignored) {
            }
        }
        ex.shutdownNow();
    }

    private String typeName(int t) {
        switch (t) {
            case CallLog.Calls.INCOMING_TYPE: return "gelen";
            case CallLog.Calls.OUTGOING_TYPE: return "giden";
            case CallLog.Calls.MISSED_TYPE: return "cevapsız";
            case CallLog.Calls.REJECTED_TYPE: return "reddedildi";
            case CallLog.Calls.BLOCKED_TYPE: return "engellendi";
            default: return "bilinmeyen";
        }
    }

    private String contactName(String number) {
        if (number == null || number.isEmpty()) return "";
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(number));
            Cursor c = app.getContentResolver().query(uri,
                    new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) return c.getString(0);
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private void syncNew() {
        ex.execute(new Runnable() {
            @Override
            public void run() {
                readAndSend();
            }
        });
    }

    private void readAndSend() {
        if (!hasPermission()) return;
        long lastId = Config.lastCallId(app);
        long maxId = lastId;
        ContentResolver cr = app.getContentResolver();
        Cursor c = null;
        try {
            c = cr.query(CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.TYPE,
                            CallLog.Calls.DATE, CallLog.Calls.DURATION},
                    CallLog.Calls._ID + " > ?", new String[]{String.valueOf(lastId)},
                    CallLog.Calls._ID + " ASC");
            if (c == null) return;
            JSONArray arr = new JSONArray();
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String number = c.getString(1);
                int type = c.getInt(2);
                long date = c.getLong(3);
                long duration = c.getLong(4);
                if (id > maxId) maxId = id;
                JSONObject o = new JSONObject();
                try {
                    o.put("id", id);
                    o.put("number", number == null ? "" : number);
                    o.put("name", contactName(number));
                    o.put("calltype", typeName(type));
                    o.put("date", date);
                    o.put("duration", duration);
                } catch (Exception ignored) {
                }
                arr.put(o);
            }
            if (arr.length() == 0) return;

            JSONObject ev = new JSONObject();
            ev.put("type", "calls");
            ev.put("items", arr);
            byte[] body = ev.toString().getBytes("UTF-8");

            for (int i = 0; i < 24; i++) { // ebeveyn telefonu geçiciyse ~2 dk dener
                try {
                    Net.post(app, "/event", "application/json", body);
                    Config.setLastCallId(app, maxId);
                    return;
                } catch (Exception e) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
    }
}
