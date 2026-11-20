package com.aile.ekran;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ebeveyn telefonunda çalışan küçük sunucu (PC'ye gerek kalmaz).
 * Çocuk telefonu buraya bağlanır: kilit olayı gelince bildirim gösterilir,
 * izlenirken ekran kareleri buradan Viewer'a akar.
 */
public class RelayService extends Service {
    static final int PORT = 8080;
    static volatile boolean running = false;
    private static final String ALERT = "aile_uyari";

    private ServerSocket server;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Object cond = new Object();
    private long seq = System.currentTimeMillis();
    private byte[] frame;
    private long frameTs;
    private volatile long childSeen = 0;
    private volatile long watchSeen = 0;
    private final ArrayList<String> events = new ArrayList<String>();
    private final AtomicInteger nid = new AtomicInteger(100);

    @Override
    public void onCreate() {
        super.onCreate();
        Notification n = Util.notif(this, "Çocuğun telefonundan haber bekleniyor");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(3, n);
        }
        running = true;
        pool.execute(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        });
    }

    private void acceptLoop() {
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(PORT));
            while (running) {
                final Socket s = server.accept();
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        handle(s);
                    }
                });
            }
        } catch (Exception ignored) {
            // port dolu ya da servis kapatıldı
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') bos.write(c);
            if (bos.size() > 8192) throw new IOException("satir cok uzun");
        }
        if (c == -1 && bos.size() == 0) return null;
        return bos.toString("UTF-8");
    }

    private static byte[] readFully(InputStream in, int n) throws IOException {
        byte[] b = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(b, off, n - off);
            if (r < 0) throw new IOException("govde eksik");
            off += r;
        }
        return b;
    }

    private static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 404: return "Not Found";
            case 413: return "Payload Too Large";
            default: return "Error";
        }
    }

    private void respond(OutputStream out, int code, String type, byte[] body, String[][] headers) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(code).append(' ').append(reason(code)).append("\r\n");
        sb.append("Content-Type: ").append(type).append("\r\n");
        sb.append("Content-Length: ").append(body.length).append("\r\n");
        sb.append("Connection: close\r\n");
        if (headers != null) {
            for (String[] h : headers) {
                sb.append(h[0]).append(": ").append(h[1]).append("\r\n");
            }
        }
        sb.append("\r\n");
        out.write(sb.toString().getBytes("UTF-8"));
        if (body.length > 0) out.write(body);
        out.flush();
    }

    private void text(OutputStream out, int code, String s) throws IOException {
        respond(out, code, "text/plain; charset=utf-8", s.getBytes("UTF-8"), null);
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(20000);
            InputStream in = new java.io.BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();

            String reqLine = readLine(in);
            if (reqLine == null) return;
            String[] parts = reqLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String target = parts[1];

            int contentLen = 0;
            String key = "";
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int i = line.indexOf(':');
                if (i < 0) continue;
                String name = line.substring(0, i).trim().toLowerCase(Locale.ROOT);
                String val = line.substring(i + 1).trim();
                if (name.equals("content-length")) {
                    contentLen = Integer.parseInt(val);
                } else if (name.equals("x-key")) {
                    key = val;
                }
            }

            String path = target;
            String query = "";
            int qi = target.indexOf('?');
            if (qi >= 0) {
                path = target.substring(0, qi);
                query = target.substring(qi + 1);
            }

            if (path.equals("/")) {
                text(out, 200, "Aile Ekrani sunucusu calisiyor");
                return;
            }
            String expected = Config.key(this);
            if (expected.isEmpty() || !MessageDigest.isEqual(key.getBytes("UTF-8"), expected.getBytes("UTF-8"))) {
                text(out, 401, "yetkisiz");
                return;
            }

            byte[] body = new byte[0];
            if (contentLen > 2000000) {
                text(out, 413, "cok buyuk");
                return;
            }
            if (contentLen > 0) body = readFully(in, contentLen);

            long now = System.currentTimeMillis();

            if (path.equals("/cmd")) {
                childSeen = now;
                text(out, 200, (now - watchSeen < 8000) ? "1" : "0");

            } else if (path.equals("/frame") && method.equals("GET")) {
                long since = 0;
                for (String kv : query.split("&")) {
                    if (kv.startsWith("since=")) {
                        try {
                            since = Long.parseLong(kv.substring(6));
                        } catch (Exception ignored) {
                        }
                    }
                }
                watchSeen = now;
                byte[] f;
                long sq;
                long fts;
                synchronized (cond) {
                    long deadline = System.currentTimeMillis() + 6000;
                    while (!(seq > since && frame != null)) {
                        long left = deadline - System.currentTimeMillis();
                        if (left <= 0) break;
                        cond.wait(left);
                    }
                    f = frame;
                    sq = seq;
                    fts = frameTs;
                }
                long t2 = System.currentTimeMillis();
                watchSeen = t2;
                String child = (t2 - childSeen < 10000) ? "1" : "0";
                if (f == null || sq <= since) {
                    respond(out, 204, "text/plain", new byte[0], new String[][]{{"X-Child", child}});
                } else {
                    respond(out, 200, "image/jpeg", f, new String[][]{
                            {"X-Seq", String.valueOf(sq)},
                            {"X-Age", String.format(Locale.US, "%.1f", (t2 - fts) / 1000.0)},
                            {"X-Child", child}});
                }

            } else if (path.equals("/frame") && method.equals("POST")) {
                synchronized (cond) {
                    frame = body;
                    frameTs = System.currentTimeMillis();
                    seq++;
                    cond.notifyAll();
                }
                childSeen = now;
                text(out, 200, "ok");

            } else if (path.equals("/event") && method.equals("POST")) {
                JSONObject ev = new JSONObject(new String(body, "UTF-8"));
                if ("unlock".equals(ev.optString("type"))) {
                    long ts = ev.optLong("ts", now);
                    String when = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(new Date(ts));
                    synchronized (events) {
                        events.add(when);
                        if (events.size() > 200) events.remove(0);
                    }
                    childSeen = now;
                    notifyUnlock(when.substring(11));
                }
                text(out, 200, "ok");

            } else if (path.equals("/status")) {
                JSONObject o = new JSONObject();
                o.put("child_online", now - childSeen < 10000);
                o.put("watching", now - watchSeen < 8000);
                JSONArray arr = new JSONArray();
                String last = "";
                synchronized (events) {
                    for (String e : events) arr.put(e);
                    if (!events.isEmpty()) last = events.get(events.size() - 1);
                }
                o.put("last_unlock", last);
                o.put("recent_unlocks", arr);
                respond(out, 200, "application/json; charset=utf-8", o.toString().getBytes("UTF-8"), null);

            } else {
                text(out, 404, "yok");
            }
        } catch (Exception ignored) {
            // bozuk istek / bağlantı koptu
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void notifyUnlock(String time) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(ALERT) == null) {
            nm.createNotificationChannel(new NotificationChannel(
                    ALERT, "Çocuğun telefonu açıldı", NotificationManager.IMPORTANCE_HIGH));
        }
        Intent i = new Intent(this, ViewerActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, ALERT)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle("Çocuğun telefonu açıldı")
                .setContentText(time + " — ekranı görmek için dokun")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(nid.incrementAndGet(), n);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
        pool.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
