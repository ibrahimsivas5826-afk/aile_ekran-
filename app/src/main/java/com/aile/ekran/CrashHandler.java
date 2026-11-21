package com.aile.ekran;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Bir çökme olduğunda tam hata metnini dosyaya yazar, sonra Android'in normal çökme
 * davranışına devam eder. Cihaza fiziksel erişim / ADB olmadan hatayı teşhis edebilmek için.
 */
class CrashHandler implements Thread.UncaughtExceptionHandler {
    static final String FILE_NAME = "last_crash.txt";

    private final Thread.UncaughtExceptionHandler prev;
    private final Context appCtx;

    private CrashHandler(Context c, Thread.UncaughtExceptionHandler prev) {
        this.appCtx = c.getApplicationContext();
        this.prev = prev;
    }

    static void install(Context c) {
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (current instanceof CrashHandler) return;
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(c, current));
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            File f = new File(appCtx.getFilesDir(), FILE_NAME);
            FileWriter w = new FileWriter(f, false);
            w.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
            w.write("\n");
            w.write(sw.toString());
            w.close();
        } catch (Exception ignored) {
        }
        if (prev != null) prev.uncaughtException(t, e);
    }

    /** Kaydedilmiş son hata metni, yoksa null. */
    static String readLast(Context c) {
        try {
            File f = new File(c.getApplicationContext().getFilesDir(), FILE_NAME);
            if (!f.exists()) return null;
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            in.read(buf);
            in.close();
            return new String(buf, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}
