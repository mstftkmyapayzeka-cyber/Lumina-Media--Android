# Lumina Player — Android 🎬

[Lumina Player](https://github.com/mstftkmyapayzeka-cyber/Lumina-Media) masaüstü medya oynatıcısının Android sürümü.
Masaüstündeki gibi **libVLC** motorunu kullanır; bu sayede VLC seviyesinde geniş format desteği sunar,
arayüzü ise Jetpack Compose ile yazılmış modern bir Material 3 tasarımıdır (Lumina koyu tema + vurgu renkleri).

---

## 📲 APK'yı telefona kurma (bilgisayar gerekmez)

Uygulama her `push` işleminde **GitHub Actions** üzerinde otomatik derlenir.

1. Telefonun tarayıcısından bu depoda **Actions** sekmesine girin → en son başarılı **“Android APK”** çalıştırmasını açın.
2. Sayfanın altındaki **Artifacts** bölümünden **`LuminaPlayer-APK`** dosyasını indirin (GitHub'a giriş yapmış olmanız gerekir) ve zip'i açın.
3. Telefonunuza uygun APK'yı kurun:
   | Dosya | Hangi cihaz? |
   |---|---|
   | `LuminaPlayer-…-arm64-v8a.apk` | **Neredeyse tüm güncel telefonlar** (önerilen) |
   | `LuminaPlayer-…-armeabi-v7a.apk` | Eski 32-bit telefonlar |
   | `LuminaPlayer-…-x86_64.apk` / `x86` | Emülatörler, bazı tabletler/Chromebook'lar |
   | `LuminaPlayer-…-universal.apk` | Emin değilseniz (tüm mimariler, daha büyük) |
4. Kurulumda “Bilinmeyen kaynaklardan yüklemeye izin ver” onayı istenir.

> **Kalıcı indirme bağlantısı:** `v0.2.0` gibi bir etiket (tag) oluşturulduğunda APK'lar otomatik olarak
> **Releases** sayfasına da eklenir; Artifacts'ın aksine süresi dolmaz ve giriş gerektirmez.

CI derlemeleri depodaki sabit geliştirici anahtarıyla (`app/lumina-debug.keystore`) imzalanır; böylece her yeni APK
bir öncekinin **üzerine güncelleme** olarak kurulur. Play Store'a yüklemek için kendi anahtarınızı oluşturup
GitHub Secrets'a ekleyin (`LUMINA_KEYSTORE_BASE64`, `LUMINA_KEYSTORE_PASSWORD`, `LUMINA_KEY_ALIAS`, `LUMINA_KEY_PASSWORD`).

---

## 🌟 Özellikler

### Format ve oynatma
- **libVLC 3.6 (Android)** — MKV, AVI, MOV, WMV, FLV, WebM, TS/M2TS, VOB, RMVB, 3GP, OGV…; MP3, FLAC, WAV, AAC, OPUS, WMA, APE, ALAC…
- **Donanım hızlandırma** (MediaCodec), ayarlardan kapatılabilir.
- **Ağ akışları:** HTTP/HTTPS, HLS (`.m3u8`), RTSP, RTMP, UDP… (DRM'li içerik desteklenmez).
- **İçerik (sihirli bayt) tespiti:** Eklenen klasörlerde uzantısı olmayan/yanlış (`.dat` vb.) medya dosyaları bulunur.
- **Diğer uygulamalardan “Birlikte aç”:** Dosyalar, tarayıcı, WhatsApp, Telegram…

### Kütüphane
- **Videolar / Müzik / Klasörler / Geçmiş** sekmeleri, arama ve sıralama (ad, tarih, boyut, süre).
- **Doğal sıralama:** *Bölüm 2*, *Bölüm 10*'dan önce gelir; Türk alfabesi sırası (ç, ğ, ı, ö, ş, ü) doğru.
- **Klasör ekle (SAF):** Sistemin dizinlemediği biçimler dahil, alt klasörlerle birlikte taranır.
- **İzlemeye devam et** satırı ve listelerde izlenme ilerleme çubukları.

### Oynatıcı
- **Kaldığın yerden devam et** (SQLite) — “Baştan başla” seçeneğiyle.
- **Jestler:** çift dokunuş ±10 sn (ayarlanabilir), yatay kaydırma ile sarma, solda parlaklık, sağda **%200'e kadar ses**.
- **Altyazı:** Gömülü izler + harici dosya yükleme; aynı adlı `.srt/.ass` dosyaları otomatik yüklenir.
  **Türkçe karakter koruması:** UTF-8 / UTF-16 / Windows-1254 / ISO-8859-9 otomatik tespit edilip UTF-8'e çevrilir.
- Ses izi seçimi, **altyazı ve ses gecikmesi** ayarı.
- **10 bantlı ekolayzer** (libVLC hazır ayarları + özel bantlar, ön yükseltme).
- **Oynatma hızı** 0.25× – 4× (ses perdesi korunur).
- **Görüntü oranı:** Sığdır, Doldur, Uzat, 16:9, 4:3, Orijinal.
- **Çalma modları:** Sırayla, Karışık, Listeyi tekrarla, Tekini tekrarla, Bitince dur.
- **Çalma sırası** paneli; **M3U/M3U8** içe/dışa aktarma.
- **Resim içinde resim (PiP)**, ekran kilidi, ekran döndürme, **ekran görüntüsü** (Resimler/Lumina Player).
- **Arka planda çalma:** Medya bildirimi, kilit ekranı kontrolleri, Bluetooth/kulaklık tuşları; kulaklık çıkınca duraklatma.
- Video oynarken ekranın kapanması engellenir; son çalma listesi uygulama açılınca geri yüklenir.

### Görünüm
- Masaüstüyle aynı tasarım token'ları: antrasit koyu tema (`#0F1115`), açık tema ve 5 vurgu rengi
  (Mavi, Mor, Kırmızı, Yeşil, Turuncu). Türkçe ve İngilizce arayüz (sistem diline göre).

### Klavye (Chromebook, tablet klavyesi, Android TV)
| Tuş | Eylem |
|---|---|
| Boşluk | Oynat / Duraklat |
| ← / → (Shift: 30 sn, Ctrl: 60 sn) | Geri / ileri sar |
| ↑ / ↓ | Ses ±%5 |
| M | Sessiz |
| N / B | Sonraki / Önceki |
| [ / ] | Hızı azalt / artır |
| S | Ekran görüntüsü |
| Esc | Oynatıcıdan çık |

---

## 🧱 Mimari

Masaüstü sürümdeki katman yapısı korunmuştur:

```
Lumina-Media--Android/
├─ core/                     # Saf Kotlin (Android'e bağımlı değil) — JVM birim testleri
│  └─ Formats (uzantı + sihirli bayt), NaturalSort, Playlist (çalma modları),
│     M3u, SubtitleDecoder (Türkçe kodlama), ResumePolicy, TimeFormat, MediaItem
├─ app/
│  ├─ LuminaApp.kt           # Uygulama; ayarlar, geçmiş, kütüphane, motor
│  ├─ MainActivity.kt        # Kütüphane (Compose + Navigation)
│  ├─ PlayerActivity.kt      # Tam ekran oynatıcı, PiP, ekran görüntüsü, klavye
│  ├─ playback/
│  │  ├─ PlayerEngine.kt     # libVLC sarmalayıcı (StateFlow ile durum yayını)
│  │  └─ PlaybackService.kt  # Ön plan hizmeti, MediaSession, bildirim
│  ├─ data/                  # SettingsStore, HistoryStore (SQLite), MediaRepository, Thumbnails
│  └─ ui/                    # theme/, library/, player/, settings/, components/
└─ .github/workflows/android.yml   # Test + APK derleme + Release
```

| Masaüstü (Python) | Android (Kotlin) |
|---|---|
| `core/player.py` — PlayerEngine (Qt sinyalleri) | `playback/PlayerEngine.kt` (StateFlow) |
| `core/playlist.py`, `natural_sort.py`, `m3u.py`, `formats.py` | `core/` modülü (aynı mantık, birim testli) |
| `services/subtitles.py` (charset-normalizer) | `core/SubtitleDecoder.kt` |
| `services/history.py` (SQLite) | `data/HistoryStore.kt` (SQLite) |
| `services/settings.py` (JSON) | `data/SettingsStore.kt` (SharedPreferences) |
| `services/windows/power.py` (uyku engelleme) | `FLAG_KEEP_SCREEN_ON` |
| SMTC / görev çubuğu | MediaSession + medya bildirimi |

---

## 🛠️ Geliştirme

Yerel kurulum gerekmez; tarayıcıdan düzenleyip `push` etmeniz yeterli, APK'yı Actions üretir.
Android Studio ile çalışmak isterseniz:

```bash
./gradlew :core:test          # Çekirdek birim testleri
./gradlew assembleDebug       # Hata ayıklama APK'ları (app/build/outputs/apk/debug)
./gradlew assembleRelease     # Küçültülmüş yayın APK'ları
```

Gereksinimler: JDK 17, Android SDK 35. En düşük Android sürümü: **7.0 (API 24)**.

---

## ⚖️ Lisans bildirimleri

- **libVLC (LGPL 2.1+):** Lumina Player, medya çözme için [VideoLAN](https://www.videolan.org/) projesinin
  libVLC kütüphanesini (`org.videolan.android:libvlc-all`) dinamik olarak bağlar.
- **DRM:** Lumina Player yerel dosyalar ve şifresiz akışlar için tasarlanmıştır; Widevine/PlayReady gibi DRM ile
  korunan ticari içerikleri oynatmaz.
