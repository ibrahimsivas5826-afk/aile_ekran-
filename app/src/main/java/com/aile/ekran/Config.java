package com.aile.ekran;

import android.content.Context;
import android.content.SharedPreferences;

/** Ayarlar: sunucu adresi, ortak anahtar, mod (child / parent). */
class Config {
    /** Ebeveyn telefonunda yerleşik sunucu kullanılıyorsa adres budur. */
    static final String LOCAL = "http://127.0.0.1:8080";

    static boolean usesLocalRelay(Context c) {
        return "parent".equals(mode(c)) && LOCAL.equals(server(c));
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    static String server(Context c) {
        String s = p(c).getString("server", "").trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    static String key(Context c) {
        return p(c).getString("key", "");
    }

    /** "child", "parent" ya da "" */
    static String mode(Context c) {
        return p(c).getString("mode", "");
    }

    static void save(Context c, String server, String key, String mode) {
        p(c).edit().putString("server", server).putString("key", key).putString("mode", mode).apply();
    }
}
