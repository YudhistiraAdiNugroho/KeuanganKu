# Panduan Build KeuanganKu Android

Panduan ini untuk membangun APK di **laptop kamu sendiri** memakai Android Studio.
APK rilis tidak bisa saya build-kan dari sini — keystore harus milikmu.

## 1. Yang perlu disiapkan

| Kebutuhan | Keterangan |
|---|---|
| Android Studio | Versi Ladybug (2024.2) atau lebih baru — unduh di developer.android.com |
| JDK 17 | Sudah termasuk di Android Studio (jangan pakai JDK 8) |
| Akun Supabase | Gratis di supabase.com — 1 project |
| Akun Brevo | Gratis — untuk SMTP pengiriman OTP |
| 9Router | VPS kamu `43.106.119.94:20128` (sudah jalan) |

## 2. Siapkan Supabase (sekali saja)

1. Buat project baru di supabase.com → catat **Project URL** dan **anon key**
   (Project Settings → API).
2. Buka **SQL Editor** → New query → salin-tempel seluruh isi
   `supabase/schema.sql` dari proyek ini → **Run**.
   Ini membuat 6 tabel + RLS (data user terisolasi) + bucket privat `receipts`.
3. **SMTP Brevo** (agar OTP daftar & lupa password terkirim):
   - Daftar di brevo.com → dapatkan SMTP login & password
     (Settings → SMTP & API).
   - Di Supabase: **Authentication → SMTP Settings** → centang
     *Enable custom SMTP*, isi host `smtp-relay.brevo.com`, port `587`,
     username & password dari Brevo.
4. **Template email OTP**: Authentication → Email Templates →
   pilih *Confirm signup* dan *Magic Link* — pastikan isinya memakai
   variabel `{{ .Token }}` (6 digit) agar kode OTP tampil di email.
5. (Opsional, disarankan) Authentication → Sign In / Up →
   matikan *Confirm email* tidak perlu karena kita pakai OTP manual.

## 3. Buka proyek di Android Studio

1. Ekstrak ZIP → buka folder `keuanganku-android` via
   **File → Open** di Android Studio.
2. Tunggu **Gradle sync** selesai (pertama kali unduh dependensi ±5–10 menit).
3. Salin `local.properties.example` menjadi `local.properties`
   (di root proyek), lalu isi:

```properties
supabase.url=https://xyzcompany.supabase.co
supabase.anonKey=ISI_ANON_KEY_KAMU
ninerouter.url=http://43.106.119.94:20128/v1
ninerouter.key=ISI_API_KEY_9ROUTER_KAMU
ninerouter.model=nama-model-chat
```

> `local.properties` **jangan** di-commit/di-share — isinya kredensial.

## 4. Jalankan & coba (APK debug)

1. Colok HP via USB (aktifkan *USB debugging*) atau buat emulator
   (Device Manager → Create Device, pilih Pixel + API 30+).
2. Klik **Run ▶** (atau Shift+F10).
3. Coba alur: daftar → OTP → login → tambah transaksi → pindai struk.

**Catatan 9Router**: HP harus bisa mencapai `43.106.119.94:20128`.
Kalau di jaringan yang sama aman; kalau gagal, cek firewall VPS
(port 20128 terbuka) atau pakai HTTPS + domain.

## 5. Build APK rilis (sekali setting keystore)

1. **Build → Generate Signed App Bundle / APK** → pilih **APK** → Next.
2. **Create new…** keystore:
   - Key store path: simpan mis. `~/keuanganku-release.jks`
   - Isi password, alias `keuanganku`, validity 25+ tahun.
   - **Simpan file .jks + password baik-baik** — hilang = tidak bisa update app di Play Store.
3. Pilih **release** → Finish. APK ada di
   `app/release/app-release.apk`.

### Build via terminal (alternatif)

```bash
# Debug
./gradlew assembleDebug
# Rilis (perlu keystore dikonfigurasi di app/build.gradle.kts -> signingConfigs)
./gradlew assembleRelease
```

## 6. Troubleshooting

| Gejala | Solusi |
|---|---|
| Gradle sync gagal / timeout | Cek internet; File → Invalidate Caches → Restart |
| `local.properties` tidak terbaca | Pastikan file di root proyek (sejajar `settings.gradle.kts`) |
| Login 401 terus | Cek `supabase.url` & `anonKey`; pastikan RLS policy `own_rows` terpasang |
| OTP tidak masuk email | Cek SMTP Brevo di dashboard Supabase; cek folder spam |
| Pindai struk gagal AI | Cek `ninerouter.url` bisa dijangkau HP; cek nama model benar |
| Foto kamera miring | Sudah ditangani via EXIF; kalau masih, laporkan |

## Struktur proyek (ringkas)

```
app/src/main/java/id/keuanganku/app/
├── MainActivity.kt            # entry + NavHost
├── KeuanganKuApp.kt           # DI manual + WorkManager sync
├── ai/                        # OCR (ML Kit) + ReceiptParser (9Router)
├── data/
│   ├── remote/                # Retrofit: AuthApi, RestApi, StorageApi, NineRouterApi
│   ├── local/                 # Room: cache offline + antrean dirty-flag
│   └── repo/                  # AuthRepository, FinanceRepository (offline-first)
├── ui/
│   ├── theme/                 # warna & tipografi brand
│   ├── navigation/            # Routes
│   ├── components/            # UiKit (kartu, field, tombol, dialog)
│   └── screens/
│       ├── auth/              # login, register+OTP, lupa password
│       ├── main/              # dashboard, transaksi, akun, anggaran, berulang, tabungan, laporan, pengaturan
│       └── receipt/           # pindai struk → konfirmasi AI
└── worker/SyncWorker.kt       # dorong antrean offline tiap 30 menit
supabase/schema.sql             # tabel + RLS + storage policy
```

## Menerbitkan pembaruan via GitHub (fitur "Cek Pembaruan")

Aplikasi punya route **Pembaruan** (menu Lainnya → Cek Pembaruan) yang memeriksa
`version.json` di repo GitHub-mu. Kalau `version_code` di sana lebih besar dari
yang terpasang, user bisa unduh & install APK baru langsung dari aplikasi.

Alur rilis versi baru:

1. Naikkan `versionCode` dan `versionName` di `app/build.gradle.kts`.
2. Build APK rilis: `./gradlew assembleRelease` → hasil di
   `app/build/outputs/apk/release/app-release.apk` (butuh keystore, lihat bawah).
3. Di GitHub repo → **Releases → Draft a new release** → tag `v1.0.1` →
   lampirkan `app-release.apk` → Publish.
4. Perbarui `version.json` di root repo:
   ```json
   {
     "version_code": 2,
     "version_name": "1.0.1",
     "apk_url": "https://github.com/USERNAME/REPO/releases/download/v1.0.1/app-release.apk",
     "changelog": "Apa yang baru di versi ini."
   }
   ```
   Commit & push — aplikasi user akan mendeteksi pembaruan.
5. Pastikan `update.url` di `local.properties` menunjuk ke file itu, contoh:
   `update.url=https://raw.githubusercontent.com/USERNAME/REPO/main/version.json`
   lalu rebuild sekali agar URL masuk ke BuildConfig.

Catatan: saat install APK unduhan, Android 8+ meminta izin "install unknown apps"
sekali saja — user setujui di dialog sistem.
