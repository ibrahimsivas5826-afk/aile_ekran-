package com.aile.ekran;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 100;
    private static final int REQ_NOTIF = 101;

    private EditText serverEt, keyEt;
    private RadioButton childRb, parentRb;
    private LinearLayout actions;
    private TextView statusTv;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setPadding(0, dp(14), 0, dp(4));
        return t;
    }

    private Button btn(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setOnClickListener(l);
        return b;
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(40), dp(20), dp(20));
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("Aile Ekranı");
        title.setTextSize(26);
        root.addView(title);

        root.addView(btn("1) Tailscale'i Aç / Kur", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TailscaleHelper.openOrInstall(MainActivity.this);
            }
        }));

        root.addView(btn("2) QR ile Kur (Kodu Tara)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanQr();
            }
        }));
        root.addView(label("Yukarıdaki düğme ile diğer telefondaki QR kodu okut — adres ve anahtar otomatik dolar. "
                + "QR yoksa/çalışmazsa aşağıya elle de yazabilirsin."));

        root.addView(label("Ebeveyn telefonunun adresi (örn. http://100.x.y.z:8080)\n"
                + "ÇOCUĞUN telefonunda doldur. Ebeveyn telefonunda BOŞ bırak (sunucu bu telefonda çalışır)."));
        serverEt = new EditText(this);
        serverEt.setSingleLine(true);
        serverEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        String shown = Config.server(this);
        if (shown.equals(Config.LOCAL)) shown = "";
        serverEt.setText(shown);
        root.addView(serverEt);

        root.addView(label("Ortak anahtar (iki telefonda ve sunucuda aynı)"));
        keyEt = new EditText(this);
        keyEt.setSingleLine(true);
        keyEt.setText(Config.key(this));
        root.addView(keyEt);

        root.addView(label("Bu telefon kimin?"));
        RadioGroup rg = new RadioGroup(this);
        childRb = new RadioButton(this);
        childRb.setId(View.generateViewId());
        childRb.setText("ÇOCUĞUN telefonu (kilit bildirimi + ekran paylaşımı)");
        parentRb = new RadioButton(this);
        parentRb.setId(View.generateViewId());
        parentRb.setText("BENİM telefonum (çocuğun ekranını izle)");
        rg.addView(childRb);
        rg.addView(parentRb);
        root.addView(rg);
        String mode = Config.mode(this);
        if ("child".equals(mode)) childRb.setChecked(true);
        if ("parent".equals(mode)) parentRb.setChecked(true);

        root.addView(btn("Kaydet", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        }));

        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        root.addView(actions);

        statusTv = new TextView(this);
        statusTv.setPadding(0, dp(16), 0, 0);
        root.addView(statusTv);

        root.addView(btn("Son hatayı göster / kopyala", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLastCrash();
            }
        }));

        setContentView(sv);
        buildActions();

        applyServices();

        // Ebeveyn modunda uygulama açılınca doğrudan canlı ekranı göster.
        if (saved == null && "parent".equals(mode) && !Config.server(this).isEmpty()) {
            startActivity(new Intent(this, ViewerActivity.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyServices(); // izin ayarlarından dönüşte servisi yeniden tetikle (yeni izni yakalasın)
        checkPendingTailscaleJoin();
        refresh();
    }

    /** Tailscale kurulumuna gönderilip bekleyen bir authkey varsa, kurulum bittiyse şimdi dener. */
    private void checkPendingTailscaleJoin() {
        String pending = Config.pendingTsAuthKey(this);
        if (!pending.isEmpty() && TailscaleHelper.isInstalled(this)) {
            TailscaleHelper.tryJoinWithAuthKey(this, pending);
            Config.setPendingTsAuthKey(this, "");
            Toast.makeText(this, "Tailscale kuruldu, ağına otomatik katılım denendi. Bağlanmazsa Tailscale'i aç "
                    + "ve aynı hesapla elle giriş yap.", Toast.LENGTH_LONG).show();
        }
    }

    private void save() {
        String server = serverEt.getText().toString().trim();
        String key = keyEt.getText().toString().trim();
        String mode = childRb.isChecked() ? "child" : (parentRb.isChecked() ? "parent" : "");
        if (key.isEmpty() || mode.isEmpty()) {
            Toast.makeText(this, "Anahtarı yaz ve telefonun kime ait olduğunu seç", Toast.LENGTH_LONG).show();
            return;
        }
        if ("parent".equals(mode) && server.isEmpty()) server = Config.LOCAL;
        if (!server.startsWith("http://") && !server.startsWith("https://")) {
            Toast.makeText(this, "Çocuk telefonunda adres http:// ile başlamalı (örn. http://100.x.y.z:8080)",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Config.save(this, server, key, mode);
        applyServices();
        Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show();
        buildActions();
        refresh();
    }

    /** Moda göre doğru arka plan servisini başlatır / durdurur. */
    private void applyServices() {
        String mode = Config.mode(this);
        try {
            if ("child".equals(mode)) {
                startForegroundService(new Intent(this, UnlockService.class));
                stopService(new Intent(this, RelayService.class));
            } else if ("parent".equals(mode)) {
                stopService(new Intent(this, UnlockService.class));
                if (Config.usesLocalRelay(this)) {
                    startForegroundService(new Intent(this, RelayService.class));
                } else {
                    stopService(new Intent(this, RelayService.class));
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Bu telefonun Tailscale (100.x.y.z) adresleri. */
    private String myAddresses() {
        StringBuilder sb = new StringBuilder();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        String h = a.getHostAddress();
                        if (h != null && h.startsWith("100.")) {
                            sb.append("http://").append(h).append(":").append(RelayService.PORT).append("\n");
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    private void buildActions() {
        actions.removeAllViews();
        String mode = Config.mode(this);
        if ("child".equals(mode)) {
            actions.addView(label("Çocuk telefonu için adımlar:"));
            actions.addView(btn("1) İzinleri ver (bildirim ve pil)", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    askPermissions();
                }
            }));
            actions.addView(btn("2) Ekran paylaşımını başlat", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startCapture();
                }
            }));
            actions.addView(btn("Ekran paylaşımını durdur", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopService(new Intent(MainActivity.this, CaptureService.class));
                    refresh();
                }
            }));
            actions.addView(label("3) Telefon bağlantıyı koparmasın diye (özellikle Xiaomi/Redmi):"));
            actions.addView(btn("Pil kısıtlamasını kaldır", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    askBattery();
                }
            }));
            actions.addView(btn("Otomatik başlatma ekranını aç (Xiaomi)", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openAutostartSettings();
                }
            }));
            actions.addView(label("Sonra: telefonun Son Uygulamalar ekranında bu uygulamanın kartına "
                    + "uzun bas ve kilit simgesine dokun. Bu adımı ayarlardan otomatik açamıyoruz, elle yapılması gerekiyor."));
        } else if ("parent".equals(mode)) {
            actions.addView(btn("İzinleri ver (bildirim ve pil)", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    askPermissions();
                }
            }));
            actions.addView(btn("Eşleştirme kodunu göster (QR)", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(MainActivity.this, QrShowActivity.class));
                }
            }));
            actions.addView(btn("Çocuğun ekranını aç", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(MainActivity.this, ViewerActivity.class));
                }
            }));
            actions.addView(btn("Bağlantıyı dene", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    testConnection();
                }
            }));
            actions.addView(btn("Arama kayıtlarını gör", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(MainActivity.this, CallLogActivity.class));
                }
            }));
        }
    }

    private void refresh() {
        String mode = Config.mode(this);
        if ("child".equals(mode)) {
            statusTv.setText("Kilit açılış bildirimi: açık\nEkran paylaşımı: "
                    + (CaptureService.running ? "açık" : "kapalı (2. adımı yap)"));
        } else if ("parent".equals(mode)) {
            if (Config.usesLocalRelay(this)) {
                String addr = myAddresses();
                statusTv.setText("Sunucu bu telefonda: " + (RelayService.running ? "çalışıyor" : "başlatılıyor…")
                        + "\n\nÇOCUĞUN telefonuna yazacağın adres:\n"
                        + (addr.isEmpty() ? "(Tailscale bağlı görünmüyor; Tailscale uygulamasını açıp bağlan)" : addr));
            } else {
                statusTv.setText("Ebeveyn modu (harici sunucu). \"Çocuğun ekranını aç\" ile izle.");
            }
        } else {
            statusTv.setText("Önce ayarları doldurup Kaydet'e bas.");
        }
    }

    private void askPermissions() {
        java.util.ArrayList<String> need = new java.util.ArrayList<String>();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if ("child".equals(Config.mode(this))) {
            if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.READ_CALL_LOG);
            }
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.READ_CONTACTS);
            }
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), REQ_NOTIF);
        } else {
            askBattery();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF) askBattery();
    }

    /** Xiaomi/MIUI'nin "Otomatik başlatma" izin ekranını doğrudan açmayı dener; bu telefon marka/ROM'a
     * özgü, resmi olmayan bir ekran olduğu için bulunamazsa genel uygulama bilgisi ekranına döner. */
    private void openAutostartSettings() {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            startActivity(i);
            Toast.makeText(this, "Açılan listede Aile Ekranı'nı bul ve aç.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
                Toast.makeText(this, "Bu telefonda ayrı bir otomatik başlatma ekranı yok. "
                        + "Açılan sayfada izin/pil/arka plan ayarlarına bak.", Toast.LENGTH_LONG).show();
            } catch (Exception e2) {
                Toast.makeText(this, "Ayarlar açılamadı: " + e2.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void askBattery() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } else {
            Toast.makeText(this, "İzinler tamam", Toast.LENGTH_SHORT).show();
        }
    }

    /** Google'ın kendi kod tarayıcısı: kamerayı Play Services yönetir, bizim iznimiz/kodumuz gerekmez,
     * cihaza özgü kamera çökmelerinden etkilenmez. İlk kullanımda tarayıcı modülünü indirmek için
     * birkaç saniye internet gerekebilir. */
    private void scanQr() {
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, options);
        scanner.startScan()
                .addOnSuccessListener(new OnSuccessListener<Barcode>() {
                    @Override
                    public void onSuccess(Barcode barcode) {
                        String raw = barcode.getRawValue();
                        if (raw != null) applyScanned(raw);
                    }
                })
                .addOnCanceledListener(new OnCanceledListener() {
                    @Override
                    public void onCanceled() {
                        // kullanıcı taramayı iptal etti
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Toast.makeText(MainActivity.this, "QR tarama başarısız: " + e.getMessage()
                                + "\nAdres/anahtarı elle de yazabilirsin.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** QR'dan okunan {"addr":...,"key":...,"tskey":...} ile alanları doldurur, ÇOCUĞUN telefonu seçer, kaydeder
     * ve varsa Tailscale'i otomatik indirir / erişim koduyla ağa katmayı dener. */
    private void applyScanned(String text) {
        try {
            JSONObject o = new JSONObject(text);
            String addr = o.optString("addr", "");
            String key = o.optString("key", "");
            String tsKey = o.optString("tskey", "");
            if (addr.isEmpty() || key.isEmpty()) throw new Exception("eksik alan");
            serverEt.setText(addr);
            keyEt.setText(key);
            childRb.setChecked(true);
            parentRb.setChecked(false);
            save();
            Toast.makeText(this, "QR okundu, ayarlar dolduruldu. Şimdi izinleri ver.", Toast.LENGTH_LONG).show();
            handleTailscaleFromQr(tsKey);
        } catch (Exception e) {
            Toast.makeText(this, "Bu QR kod tanınmadı. Diğer telefonda \"Eşleştirme kodunu göster\" ekranındaki kodu okut.",
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Tailscale kurulu değilse Play Store'u açar; kuruluysa (ya da kurulduktan sonra) erişim koduyla
     * hesaba girmeden ağa katılmayı dener. Kod yoksa hiçbir şey yapmaz, kullanıcı elle giriş yapar. */
    private void handleTailscaleFromQr(String tsKey) {
        if (!TailscaleHelper.isInstalled(this)) {
            if (!tsKey.isEmpty()) {
                Config.setPendingTsAuthKey(this, tsKey);
                Toast.makeText(this, "Tailscale kuruluyor, kurulumu bitirip bu uygulamaya geri dön.", Toast.LENGTH_LONG).show();
            }
            TailscaleHelper.openOrInstall(this);
        } else if (!tsKey.isEmpty()) {
            TailscaleHelper.tryJoinWithAuthKey(this, tsKey);
            Toast.makeText(this, "Tailscale ağına otomatik katılım denendi. Bir dakika içinde bağlanmazsa "
                    + "Tailscale'i aç ve aynı hesapla elle giriş yap.", Toast.LENGTH_LONG).show();
        }
    }

    private void startCapture() {
        MediaProjectionManager m = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(m.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent i = new Intent(this, CaptureService.class);
                i.putExtra(CaptureService.EXTRA_CODE, resultCode);
                i.putExtra(CaptureService.EXTRA_DATA, data);
                startForegroundService(i);
                Toast.makeText(this, "Ekran paylaşımı başladı", Toast.LENGTH_SHORT).show();
                statusTv.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                }, 800);
            } else {
                Toast.makeText(this, "İzin verilmedi", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showLastCrash() {
        String log = CrashHandler.readLast(this);
        if (log == null || log.trim().isEmpty()) {
            Toast.makeText(this, "Kayıtlı bir hata yok.", Toast.LENGTH_SHORT).show();
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Son hata")
                .setMessage(log)
                .setPositiveButton("Kopyala", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        android.content.ClipboardManager cm =
                                (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("hata", CrashHandler.readLast(MainActivity.this)));
                        Toast.makeText(MainActivity.this, "Kopyalandı, Claude'a yapıştırabilirsin.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Kapat", null)
                .show();
    }

    private void testConnection() {
        statusTv.setText("Bağlantı deneniyor…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String msg;
                try {
                    JSONObject o = new JSONObject(Net.get(MainActivity.this, "/status", 8000));
                    String last = o.optString("last_unlock", "");
                    msg = "Sunucu tamam.\nÇocuğun telefonu: "
                            + (o.optBoolean("child_online") ? "bağlı" : "bağlı değil")
                            + "\nSon açılış: " + (last.isEmpty() ? "henüz yok" : last);
                } catch (Exception e) {
                    msg = "Bağlanılamadı: " + e.getMessage() + "\n(Adres ve anahtarı kontrol et)";
                }
                final String m = msg;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusTv.setText(m);
                    }
                });
            }
        }).start();
    }
}
