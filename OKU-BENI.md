# Aile Ekranı

Çocuğun telefonu kilit açılınca **senin telefonuna bildirim** düşer; dokununca (ya da uygulamayı açınca) çocuğun ekranını canlı görürsün. Tamamen ücretsiz; bilgisayar, sunucu kiralama ya da Telegram gerekmez.

```
Çocuk telefonu ──Tailscale──► Ebeveyn telefonu (uygulamanın içindeki küçük sunucu)
 (kilit açıldı, ekran kareleri)      ├─ bildirim gösterir
                                     └─ canlı ekranı gösterir
```

Tek APK iki iş yapar: ilk açılışta "çocuğun telefonu" ya da "benim telefonum" seçersin.

## 1) APK'yı derle
### Yol A: Android Studio kurmadan, GitHub ile (ücretsiz)
1. github.com'da ücretsiz hesap aç → **New repository** (Private seç) → oluştur.
2. Depo sayfasında **uploading an existing file** de → bu zip'in içindeki **tüm dosya ve klasörleri** (`.github` klasörü dahil) pencereye sürükle → **Commit changes**.
3. Üstteki **Actions** sekmesinde "APK derle" kendiliğinden çalışır (2-4 dk). Yeşil tik çıkınca işe tıkla, en altta **Artifacts → aile-ekrani-apk** indir. Zip'in içinde `app-debug.apk` var.
4. Kırmızı çarpı çıkarsa işe tıklayıp hata satırını bana yapıştır, düzeltirim.

### Yol B: Android Studio
1. Android Studio'da **Open** → bu klasörü (`aile-ekrani`) seç. İlk açılışta Gradle/SDK indirmesini bekle.
2. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
3. Çıkan dosya: `app/build/outputs/apk/debug/app-debug.apk`. İki telefona da yükle ("bilinmeyen kaynaklardan yükleme" izni gerekir).

## 2) Tailscale (iki telefonda, ücretsiz)
Telefonların internet üzerinden birbirini bulması için. Kişisel kullanım ücretsiz.
1. Her iki telefonda da Aile Ekranı'nı aç, en üstteki **"1) Tailscale'i Aç / Kur"**a bas — kurulu değilse Play Store'a götürür, kuruluysa doğrudan açar.
2. İkisinde de **aynı hesapla** giriş yap (Google hesabı yeterli) ve bağlan.
3. Tailscale uygulamalarında pil kısıtlamasını da kaldır (yoksa Android arka planda kapatabilir).

## 3) Senin telefonun (ebeveyn)
1. Aile Ekranı'nda **Ortak anahtar** kutusuna bir kelime yaz (harf/rakam, örn. `aile2026x`), **BENİM telefonum**'u seç, adres kutusunu **boş bırak**, **Kaydet**'e bas.
2. **İzinleri ver** (bildirim + pil).
3. **"Eşleştirme kodunu göster (QR)"**'e bas — ekranda bir QR kod çıkar.
   - İsteğe bağlı (çocuğun telefonu Tailscale'e **hiç giriş yapmadan** otomatik katılsın istiyorsan): aynı ekrandaki **"Tailscale panelini aç"** düğmesiyle `login.tailscale.com/admin/settings/keys` sayfasını aç, yeni bir erişim kodu (authkey) üret, kopyala, uygulamadaki kutuya yapıştır, **"QR'ı güncelle"**'ye bas. Bu kodu kimseyle paylaşma; artık gerekmediğinde panelden iptal edebilirsin.
   - Bu adımı atlarsan sorun değil: çocuğun telefonunda Tailscale'e girişi bir kere elle yaparsın, sonrasında otomatik bağlı kalır.
4. "Arama kayıtlarını gör" ile kimi ne zaman ne kadar aradığını listeleyebilirsin — çocuk kendi telefonundan kaydı silse bile, arama biter bitmez sana iletildiği için elinde kalır.

## 4) Çocuğun telefonu
1. Aile Ekranı'nı aç, en üstteki **"2) QR ile Kur (Kodu Tara)"**a bas, kamerayı senin telefonundaki QR koda doğrult. Adres ve anahtar otomatik dolar, kaydedilir; Tailscale kurulu değilse Play Store otomatik açılır; erişim kodu eklediysen hesaba girmeden ağa katılım denenir (bir dakika içinde bağlanmazsa Tailscale'i açıp aynı hesapla elle giriş yap).
   - QR okutamazsan (kamera yoksa vb.) adres/anahtarı elle de yazabilirsin: adrese senin telefonunda QR ekranının altında yazan `http://100.x.y.z:8080`'i, anahtara da senin yazdığın kelimeyi gir, **ÇOCUĞUN telefonu**'nu seç, Kaydet.
2. **1) İzinleri ver** (bildirim, **arama kayıtları**, **kişiler** + pil). Arama kayıtları izni verilmezse sadece o özellik çalışmaz, kilit bildirimi ve ekran paylaşımı yine çalışır.
3. **2) Ekran paylaşımını başlat** → Android'in sorduğu pencerede **"Tüm ekran"** (Entire screen) seç → Başlat.

Hepsi bu. Çocuk telefonu açınca sende "Çocuğun telefonu açıldı" bildirimi çıkar; dokununca canlı ekran gelir. Senin telefonunda uygulamayı açınca da doğrudan canlı ekran açılır.

## Bilmen gerekenler
- Erişim koduyla otomatik Tailscale katılımı, Tailscale'in resmi olarak desteklemediği bir yöntemle çalışır; her zaman garanti değildir. Çalışmazsa tek yapman gereken Tailscale'i açıp çocuğun telefonunda da aynı hesapla elle giriş yapmaktır (bir kere yapılır, sonra kalıcıdır).
- Senin telefonunda uygulama ve Tailscale açık kalmalı (kalıcı bir "Aile Ekranı" bildirimi görürsün). Telefon kapalı ya da internetsizken çocuğun açılış olayları yaklaşık 2 dakika denenir, sonra düşer.
- Çocuğun telefonunda uygulama simgesi, "Aile Ekranı" bildirimi, Tailscale'in VPN anahtarı ve Android'in ekran paylaşım göstergesi görünür. Gizlenemez; çocuğa söylemen en doğrusu.
- Çocuğun telefonu yeniden başlayınca kilit bildirimi kendiliğinden döner, ama **ekran paylaşımı için o telefonda uygulamayı açıp 2. adımı tekrarlamak gerekir** (Android güvenlik kuralı).
- Ekran paylaşımı sadece sen izlerken kare gönderir.
- Telefon kilitliyken ya da ekran kapalıyken son görüntü kalır (üstte "X sn önceki görüntü" yazar). Bazı uygulamalar (banka, DRM'li video) ekran kaydında siyah görünür.
- Trafik Tailscale tarafından şifrelenir; adres `http://` olsa da güvenlidir.

## İsteğe bağlı: bilgisayarda sunucu + Telegram bildirimi
Telefon yerine bilgisayarda çalışan sürüm de var (`sunucu/server.py`, Windows için `sunucuyu-baslat.bat`; Python gerekir). Bu durumda bildirim Telegram'dan gelir (BotFather'dan bot + chat id gerekir). İki telefondaki adres kutusuna bilgisayarın Tailscale adresini (`http://100.x.y.z:8080`) yaz, ebeveyn telefonunda da aynı adresi doldur (boş bırakırsan telefon kendi sunucusunu kullanır).
