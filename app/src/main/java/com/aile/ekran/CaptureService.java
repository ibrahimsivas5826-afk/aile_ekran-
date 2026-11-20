package com.aile.ekran;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Display;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Çocuk telefonunda ekranı yakalar (kullanıcının verdiği MediaProjection izniyle).
 * Sunucu "ebeveyn izliyor" derse JPEG kareleri gönderir, aksi halde sadece bekler.
 * Android bu sırada ekranda kalıcı bir "ekran kaydediliyor" göstergesi gösterir.
 */
public class CaptureService extends Service {
    static final String EXTRA_CODE = "code";
    static final String EXTRA_DATA = "data";
    static volatile boolean running = false;

    private static final int MIN_SIDE = 432;     // yayın çözünürlüğünün kısa kenarı
    private static final int JPEG_QUALITY = 55;
    private static final long FRAME_GAP_MS = 250; // izlenirken en fazla ~4 kare/sn

    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler handler;
    private final Object lock = new Object();
    private volatile boolean watching = false;
    private volatile boolean alive = false;
    private volatile long lastSent = 0;
    private Bitmap lastBmp;
    private long lastGrab = 0;
    private int curW, curH, dpi;
    private final ExecutorService uploader = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !intent.hasExtra(EXTRA_DATA)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (alive) return START_NOT_STICKY;

        Notification n = Util.notif(this, "Ekran ebeveynle paylaşılabilir durumda");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(2, n);
        }

        try {
            int code = intent.getIntExtra(EXTRA_CODE, 0);
            Intent data = intent.getParcelableExtra(EXTRA_DATA);
            thread = new HandlerThread("capture");
            thread.start();
            handler = new Handler(thread.getLooper());

            MediaProjectionManager mpm =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(code, data);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    stopSelf();
                }
            }, handler);

            alive = true;
            running = true;
            setupDisplay();
            new Thread(this::pollLoop, "poll").start();
        } catch (Exception e) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private int[] targetSize() {
        DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
        DisplayMetrics m = new DisplayMetrics();
        d.getRealMetrics(m);
        float s = (float) MIN_SIDE / Math.min(m.widthPixels, m.heightPixels);
        if (s > 1f) s = 1f;
        return new int[]{
                Math.max(2, Math.round(m.widthPixels * s)),
                Math.max(2, Math.round(m.heightPixels * s)),
                m.densityDpi};
    }

    private void setupDisplay() {
        int[] sz = targetSize();
        synchronized (lock) {
            curW = sz[0];
            curH = sz[1];
            dpi = sz[2];
            ImageReader old = reader;
            reader = ImageReader.newInstance(curW, curH, PixelFormat.RGBA_8888, 2);
            reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader r) {
                    onImage(r);
                }
            }, handler);
            if (display == null) {
                display = projection.createVirtualDisplay("aile", curW, curH, dpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, handler);
            } else {
                display.resize(curW, curH, dpi);
                display.setSurface(reader.getSurface());
            }
            if (old != null) old.close();
        }
    }

    private void onImage(ImageReader r) {
        Image img = null;
        try {
            img = r.acquireLatestImage();
            if (img == null) return;
            long now = SystemClock.elapsedRealtime();
            long gap = watching ? FRAME_GAP_MS : 1000;
            if (now - lastGrab < gap) return;
            lastGrab = now;

            Image.Plane p = img.getPlanes()[0];
            int bw = p.getRowStride() / p.getPixelStride();
            Bitmap full = Bitmap.createBitmap(bw, img.getHeight(), Bitmap.Config.ARGB_8888);
            full.copyPixelsFromBuffer(p.getBuffer());
            Bitmap bmp = full;
            if (bw != img.getWidth()) {
                bmp = Bitmap.createBitmap(full, 0, 0, img.getWidth(), img.getHeight());
                full.recycle();
            }
            synchronized (lock) {
                lastBmp = bmp;
            }
            if (watching) sendBitmap(bmp);
        } catch (Exception ignored) {
            // okuyucu kapanmış olabilir (ekran döndürme vb.)
        } finally {
            if (img != null) img.close();
        }
    }

    private void sendBitmap(final Bitmap bmp) {
        if (!busy.compareAndSet(false, true)) return;
        lastSent = SystemClock.elapsedRealtime();
        uploader.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bos);
                    Net.post(CaptureService.this, "/frame", "image/jpeg", bos.toByteArray());
                } catch (Exception ignored) {
                } finally {
                    busy.set(false);
                }
            }
        });
    }

    /** Sunucudan "ebeveyn izliyor mu?" bilgisini sorar; izleniyorsa durgun ekranda da kalp atışı kare gönderir. */
    private void pollLoop() {
        while (alive) {
            try {
                String r = Net.get(this, "/cmd", 6000).trim();
                boolean w = "1".equals(r);
                boolean started = w && !watching;
                watching = w;
                if (watching) {
                    Bitmap b;
                    synchronized (lock) {
                        b = lastBmp;
                    }
                    if (b != null && (started || SystemClock.elapsedRealtime() - lastSent > 2000)) {
                        sendBitmap(b);
                    }
                }
            } catch (Exception e) {
                watching = false;
            }
            try {
                int[] sz = targetSize();
                if (sz[0] != curW || sz[1] != curH) setupDisplay();
            } catch (Exception ignored) {
            }
            try {
                Thread.sleep(watching ? 1000 : 2000);
            } catch (InterruptedException ie) {
                break;
            }
        }
    }

    @Override
    public void onDestroy() {
        alive = false;
        running = false;
        try {
            if (display != null) display.release();
        } catch (Exception ignored) {
        }
        try {
            if (reader != null) reader.close();
        } catch (Exception ignored) {
        }
        try {
            if (projection != null) projection.stop();
        } catch (Exception ignored) {
        }
        if (thread != null) thread.quitSafely();
        uploader.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
