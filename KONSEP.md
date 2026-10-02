# KONSEP FINAL — KeuanganKu (Aplikasi Android Native)

Tanggal finalisasi: 2026-10-02

## 1. Ringkasan
Aplikasi pencatatan keuangan pribadi multi-user, Android native (Kotlin), ringan di HP, UI modern dengan animasi halus. Fitur unggulan: foto struk belanja → AI membaca dan mengisi data transaksi otomatis (dengan konfirmasi user). Data tersimpan di cloud (Supabase) dengan isolasi penuh antar-user.

## 2. Platform & Teknologi
| Komponen | Pilihan |
|---|---|
| Bahasa | Kotlin saja (tanpa WebView/wrapper web) |
| Min. Android | 8.0 (API 26) |
| UI | Jetpack Compose + Material 3 (animasi bawaan: transisi layar, stagger list, progress animasi) |
| Database | Supabase (PostgreSQL) |
| Auth | Supabase Auth (email + password) |
| OTP email | Supabase Auth, pengiriman email via **Brevo SMTP** (bukan email bawaan Supabase) |
| Penyimpanan file | Supabase Storage (bucket privat `receipts`) |
| OCR struk | ML Kit Text Recognition (on-device, gratis, offline) |
| AI penstruktur data | **9RouterProxy** milik user (OpenAI-compatible, direct dari HP) |
| Offline | Cache lokal + antrean tulis, sinkron otomatis saat online |

Build dilakukan user di laptop sendiri (Android Studio). Deliverable dari pengembang: proyek Android lengkap (.zip) + script + panduan build langkah demi langkah (termasuk pembuatan keystore untuk APK rilis).

## 3. Alur Pengguna (Auth)
1. Buka aplikasi → layar sambutan.
2. **Daftar**: nama + email → OTP 6 digit dikirim ke email (via Brevo) → verifikasi → buat password → masuk.
3. **Masuk**: email + password.
4. **Lupa password**: email → OTP via email → buat password baru.

## 4. Fitur
1. **Dashboard** — total saldo, pemasukan/pengeluaran bulan berjalan, transaksi terakhir, tren 6 bulan, widget "Anggaran Bulan Ini".
2. **Transaksi** — tambah/edit/hapus, transfer antar akun, filter (tipe/bulan/kategori/akun), pencarian.
3. **Akun** — tambah/edit/hapus (dompet, bank, e-wallet).
4. **Anggaran** — batas bulanan per kategori, progress bar + peringatan (amber ≥75%, merah saat lewat).
5. **Berulang** — jadwal harian/mingguan/bulanan (gaji, cicilan, langganan); transaksi terbuat otomatis saat jatuh tempo, termasuk catch-up periode terlewat.
6. **Tabungan** — target dengan progress, setor/tarik, penanda tercapai.
7. **Laporan** — ringkasan, grafik per kategori, tren, filter per akun, export CSV.
8. **Pengaturan** — kelola kategori, pintasan fitur.
9. **Foto Struk (AI)** — dari kamera atau galeri (lihat alur di §6).

## 5. Model Data (Supabase)
Tabel (semua punya `user_id` mengacu ke `auth.users`):
- `accounts` (name, type, balance, currency)
- `categories` (name, type, icon)
- `transactions` (account_id, to_account_id, category_id, type, amount, note, date, receipt_path)
- `budgets` (category_id, amount — unik per user+kategori)
- `recurring_transactions` (account_id, to_account_id, category_id, type, amount, note, frequency, start_date, next_date, active)
- `savings_goals` (name, target_amount, current_amount, deadline, icon)

**Aturan bisnis yang dipertahankan dari versi web:**
- Akun yang masih punya transaksi tidak bisa dihapus (ditolak dengan pesan jelas).
- Menghapus kategori membuat transaksinya menjadi "Tanpa kategori" (bukan error).
- Transfer memvalidasi akun tujuan (wajib, tidak boleh sama, harus milik sendiri).

## 6. Alur AI Struk
1. User memilih **kamera** (foto langsung) atau **galeri**.
2. Gambar dikompresi di HP (±150–300 KB), disimpan sementara.
3. **ML Kit** membaca teks struk (on-device, tanpa internet).
4. Teks dikirim ke **9Router** dengan prompt penstruktur → kembali sebagai JSON: `{merchant, date, total, items[], currency}`.
5. **Layar konfirmasi**: user memeriksa/mengoreksi, memilih akun & kategori, lalu menyimpan.
6. Gambar struk di-upload ke **Supabase Storage** (`receipts/{user_id}/{tahun}/{bulan}/...`); path-nya tercatat di transaksi.
7. Di HP, file asli hanya jadi cache sementara — memori internal tidak penuh.

Estimasi kapasitas paket gratis (1 GB): ribuan struk.

## 7. Keamanan
- **Isolasi per user via Row Level Security (RLS)**: setiap tabel hanya bisa diakses baris milik `auth.uid()` sendiri (baca/tambah/edit/hapus). Berlaku juga untuk folder Storage per user. Berlaku di level database, bukan sekadar di kode aplikasi.
- API key (9Router, Supabase) disimpan di konfigurasi aplikasi, tidak di-hardcode di repo publik.
- OTP sekali pakai & kedaluwarsa singkat; email via Brevo (300 email/hari di paket gratis).

## 8. Offline
- Saat offline: transaksi/anggaran/dll tersimpan di antrean lokal, tampil normal di aplikasi.
- Saat online kembali: antrean tersinkron otomatis ke Supabase.

## 9. Build & Rilis (di laptop user)
1. Install Android Studio.
2. Buka proyek dari zip.
3. Isi 3 konfigurasi: URL + key Supabase, alamat + key 9Router, SMTP Brevo (di dashboard Supabase, bukan di aplikasi).
4. Run di emulator/HP untuk uji.
5. Build APK debug untuk coba-coba; build APK rilis (butuh keystore — dibuat sekali via panduan) untuk dipakai harian.

## 10. Hal yang Masih Terbuka
- Verifikasi model yang tersedia di 9Router (apakah ada model vision, atau cukup jalur ML Kit + model teks). Perlu akses endpoint 9Router — dilakukan saat implementasi dengan persetujuan user.
