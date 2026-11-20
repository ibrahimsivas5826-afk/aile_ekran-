package com.aile.ekran;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Sunucuyla konuşan küçük HTTP yardımcıları. Ana thread'den ÇAĞIRMAYIN. */
class Net {

    static HttpURLConnection open(Context c, String path, String method, int readTimeoutMs) throws IOException {
        URL u = new URL(Config.server(c) + path);
        HttpURLConnection h = (HttpURLConnection) u.openConnection();
        h.setRequestMethod(method);
        h.setConnectTimeout(6000);
        h.setReadTimeout(readTimeoutMs);
        h.setRequestProperty("X-Key", Config.key(c));
        return h;
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        in.close();
        return bos.toByteArray();
    }

    static String get(Context c, String path, int timeoutMs) throws IOException {
        HttpURLConnection h = open(c, path, "GET", timeoutMs);
        try {
            int code = h.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            return new String(readAll(h.getInputStream()), "UTF-8");
        } finally {
            h.disconnect();
        }
    }

    static void post(Context c, String path, String contentType, byte[] body) throws IOException {
        HttpURLConnection h = open(c, path, "POST", 10000);
        try {
            h.setDoOutput(true);
            h.setRequestProperty("Content-Type", contentType);
            h.setFixedLengthStreamingMode(body.length);
            OutputStream os = h.getOutputStream();
            os.write(body);
            os.close();
            int code = h.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            readAll(h.getInputStream());
        } finally {
            h.disconnect();
        }
    }
}
