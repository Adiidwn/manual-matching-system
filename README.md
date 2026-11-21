# Transaction Reconciliation

Aplikasi rekonsiliasi transaksi: **file CSV → parse → PostgreSQL → manual matching maker → checker → signer**.

- **PostgreSQL** = sumber kebenaran.
- **Kafka** = memproses import secara asynchronous.
- **Redis** = cache kandidat (60 detik).
- **Elasticsearch** = pencarian teks.

> Tidak ada UI. Semua operasi lewat REST API.

Dokumentasi endpoint lengkap: [`API.md`](API.md).
Koleksi Postman: [`postman/`](postman/README.md).

Alur singkat:

```text
file → parse → transaksi UNMATCHED → pilih kandidat → maker → checker → signer
```

## Daftar isi

- [API reference](API.md)
- [Postman collection](postman/README.md)
- [Menjalankan](#menjalankan)
- [Format file](#format-file)
- [Import](#import)
- [Antrean](#antrean)
- [Kandidat](#kandidat)
- [Manual matching](#manual-matching)
- [Menolak pengajuan](#menolak-pengajuan)
- [Verifikasi](#verifikasi)
- [Elasticsearch](#elasticsearch)
- [Konfigurasi](#konfigurasi)
- [Troubleshooting](#troubleshooting)

## Menjalankan

Butuh Docker + Compose.

```shell
docker compose up --build -d
```

Tunggu sampai semua service `healthy`, lalu cek:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

```json
{"status":"UP"}
```

API: `http://localhost:8080`

Menghentikan aplikasi tanpa menghapus data:

```shell
docker compose down
```

Menghapus semua data (volume ikut terhapus):

```shell
docker compose down -v
```

> Contoh memakai PowerShell. Di Git Bash/Linux, tulis URL lengkap atau `export base=http://localhost:8080`. Gunakan `curl -sS` agar error terlihat.

## Format file

File `.csv`, UTF-8, dengan urutan kolom tetap:

```text
source,external_reference,transaction_date,amount,currency,description
```

| Kolom | Aturan |
|---|---|
| `source` | `BANK` atau `INTERNAL` |
| `external_reference` | 1–100 karakter, unik per source |
| `transaction_date` | `yyyy-MM-dd` |
| `amount` | angka positif, 2 desimal |
| `currency` | 3 huruf, misalnya `IDR` |
| `description` | maksimal 500 karakter, boleh kosong |

Jika deskripsi mengandung koma, bungkus dengan kutip:

```csv
BANK,INV-1001,2025-03-01,150000.00,IDR,"Transfer masuk, PT Alpha"
```

### Baris total (trailer)

Di baris terakhir boleh ada jumlah baris data:

```text
source,external_reference,transaction_date,amount,currency,description
BANK,INV-1001,2025-03-01,150000.00,IDR,Transfer masuk
INTERNAL,INV-1001,2025-03-01,150000.00,IDR,Transfer keluar
2
```

Aturan:

- satu kolom angka, harus baris terakhir,
- menghitung semua baris data, termasuk yang nanti ditolak,
- leading zero boleh (`09` = 9),
- opsional,
- jika tidak cocok, batch `FAILED` **sebelum** ada data dimasukkan.

Angka trailer ada di `declaredRows`; jumlah data aktual ada di `totalRows`.

### Contoh file

| File | Isi |
|---|---|
| [`samples/transactions-sample.csv`](samples/transactions-sample.csv) | 3 pasang, trailer `6` |
| [`samples/transactions-with-total.csv`](samples/transactions-with-total.csv) | 2 pasang, trailer `4` |
| [`samples/bank-statement-2025-03-01.csv`](samples/bank-statement-2025-03-01.csv) | hanya BANK, trailer `3` |
| [`samples/internal-ledger-2025-03-01.csv`](samples/internal-ledger-2025-03-01.csv) | hanya INTERNAL, trailer `3` |
| [`samples/transactions-with-errors.csv`](samples/transactions-with-errors.csv) | campuran valid/invalid, trailer `09` |
| [`samples/transactions-bad-total.csv`](samples/transactions-bad-total.csv) | trailer salah → `FAILED` |

Penjelasan lengkap: [`samples/README.md`](samples/README.md).

## Import

### 1. Scheduler

```powershell
New-Item -ItemType Directory -Force imports/inbox | Out-Null
Copy-Item samples/transactions-sample.csv imports/inbox/
```

Scheduler memindai folder setiap 30 detik. File harus `.csv` dan sudah stabil minimal 3 detik.

### 2. Upload

```powershell
curl.exe -F "file=@samples/transactions-sample.csv" http://localhost:8080/api/imports
```

Upload langsung diproses tanpa menunggu scheduler. Maksimal 100 MB.

### 3. Scan manual

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/imports/scan
```

### Status batch

```powershell
Invoke-RestMethod http://localhost:8080/api/imports |
    Format-Table id,fileName,status,totalRows,declaredRows,insertedRows,duplicateRows,rejectedRows

Invoke-RestMethod http://localhost:8080/api/imports/1
```

Status: `RECEIVED → PROCESSING → COMPLETED | FAILED`.

| Field | Arti |
|---|---|
| `totalRows` | jumlah baris data aktual |
| `declaredRows` | angka trailer, `null` jika tidak ada |
| `insertedRows` | baris baru masuk database |
| `duplicateRows` | dilewati karena referensi sudah ada |
| `rejectedRows` | baris invalid |
| `errorMessage` | penyebab jika `FAILED` |

Upload bersifat asynchronous. `RECEIVED` bukan error; poll `GET /api/imports/{id}` sampai `COMPLETED`/`FAILED`.

Import idempotent:

- SHA-256 mencegah file sama diproses dua kali.
- `(source, external_reference)` mencegah baris dobel.
- Baris invalid dilewati; baris valid tetap masuk.
- Batch `FAILED` bisa diulang dengan upload ulang.

## Antrean

```text
GET /api/transactions
```

| Parameter | Arti |
|---|---|
| `status` | `UNMATCHED`, `PENDING_APPROVAL`, `RECONCILED` |
| `source` | `BANK`, `INTERNAL` |
| `batchId` | filter batch import |
| `externalReference` | pencarian sebagian, case-insensitive |
| `minAmount` | amount minimum (inklusif) |
| `maxAmount` | amount maksimum (inklusif) |
| `fromDate` | tanggal mulai (inklusif) |
| `toDate` | tanggal akhir (inklusif) |
| `page`, `size` | pagination, maksimal 100 |

Contoh:

```text
GET /api/transactions?status=UNMATCHED&source=BANK&page=0&size=50
GET /api/transactions?externalReference=INV-1001
GET /api/transactions?minAmount=100000&maxAmount=500000
GET /api/transactions?fromDate=2025-03-01&toDate=2025-03-31
GET /api/transactions?externalReference=TRL&minAmount=80000&maxAmount=150000&fromDate=2025-05-01&toDate=2025-05-31
```

Amount persis: isi `minAmount` dan `maxAmount` dengan angka yang sama.

## Kandidat

```text
GET /api/transactions/{id}/candidates?toleranceDays=3&limit=10
```

Kandidat adalah transaksi:

- dari sumber seberang,
- berstatus `UNMATCHED`,
- amount dan currency sama persis,
- tanggal dalam `transactionDate ± toleranceDays`,
- maksimal 50 hasil.

Kandidat hanya saran. Maker tetap memilih pasangan.

## Manual matching

Header:

| Header | Contoh |
|---|---|
| `X-User` | `maker-1` |
| `X-Role` | `MAKER`, `CHECKER`, `SIGNER` |

Aturan:

- maker tidak boleh menjadi checker/signer,
- checker dan signer harus berbeda,
- penolakan wajib memakai alasan.

### Script satu pasangan

Script ini memilih satu BANK yang punya kandidat, lalu menjalankan maker → checker → signer:

```powershell
$base = 'http://localhost:8080'

# 1. Pilih BANK yang punya kandidat
$queue = Invoke-RestMethod "$base/api/transactions?status=UNMATCHED&source=BANK&page=0&size=50"
$bank = $null
$internal = $null
foreach ($row in $queue.content) {
    $candidates = @(Invoke-RestMethod "$base/api/transactions/$($row.id)/candidates?toleranceDays=3&limit=10")
    if ($candidates.Count -gt 0) {
        $bank = $row
        $internal = $candidates[0]
        break
    }
}
if (-not $bank) { throw 'Tidak ada pasangan yang siap dimatching' }
Write-Host "BANK $($bank.id) $($bank.externalReference) ↔ INTERNAL $($internal.id) $($internal.externalReference)"

# 2. Maker
$headers = @{ 'X-User' = 'maker-1'; 'X-Role' = 'MAKER' }
$body = @{ bankTransactionId = $bank.id; internalTransactionId = $internal.id } | ConvertTo-Json
$match = Invoke-RestMethod -Method Post "$base/api/matches" -Headers $headers -ContentType 'application/json' -Body $body
$matchId = $match.id

# 3. Checker
$headers = @{ 'X-User' = 'checker-1'; 'X-Role' = 'CHECKER' }
Invoke-RestMethod -Method Post "$base/api/matches/$matchId/checker-decision" `
    -Headers $headers -ContentType 'application/json' -Body '{"approve":true}' | Out-Null

# 4. Signer
$headers = @{ 'X-User' = 'signer-1'; 'X-Role' = 'SIGNER' }
Invoke-RestMethod -Method Post "$base/api/matches/$matchId/signer-decision" `
    -Headers $headers -ContentType 'application/json' -Body '{"approve":true}' | Out-Null

# 5. Hasil + audit
Invoke-RestMethod "$base/api/matches/$matchId" | ConvertTo-Json -Depth 5
```

### Status

| Aksi | Match | Transaksi |
|---|---|---|
| Maker submit | `SUBMITTED` | `PENDING_APPROVAL` |
| Checker approve | `CHECKED` | tetap `PENDING_APPROVAL` |
| Signer approve | `SIGNED` | `RECONCILED` |
| Checker/signer reject | `REJECTED` | kembali `UNMATCHED` |

### Daftar match

```text
GET /api/matches?status=SUBMITTED&page=0&size=20
GET /api/matches?maker=maker-1
GET /api/matches?checker=checker-1
GET /api/matches?signer=signer-1
GET /api/matches?status=CHECKED&signer=signer-1&page=0&size=20
```

Filter: `status`, `maker`, `checker`, `signer`, `page`, `size` (maksimal 100). Nama actor dicocokkan persis tanpa membedakan huruf besar/kecil, dan semua filter digabung dengan `AND`.

Contoh work queue:

- checker melihat yang perlu diperiksa: `status=SUBMITTED`
- signer melihat yang siap disahkan: `status=CHECKED`

Respons daftar hanya berisi data match. Audit lengkap tetap di `GET /api/matches/{id}`.

## Menolak pengajuan

```powershell
$headers = @{ 'X-User' = 'checker-1'; 'X-Role' = 'CHECKER' }
Invoke-RestMethod -Method Post "http://localhost:8080/api/matches/$matchId/checker-decision" `
    -Headers $headers -ContentType 'application/json' `
    -Body '{"approve":false,"reason":"Nominal tidak sesuai mutasi bank"}'
```

Transaksi kembali `UNMATCHED`, match menjadi `REJECTED`, alasan masuk audit trail.

## Verifikasi

```powershell
# Detail match + audit
Invoke-RestMethod "http://localhost:8080/api/matches/$matchId"

# Transaksi selesai
Invoke-RestMethod "http://localhost:8080/api/transactions?status=RECONCILED&page=0&size=20"

# Ringkasan
Invoke-RestMethod http://localhost:8080/api/reports/reconciliation

# Database
docker compose exec postgres psql -U reconciliation -d reconciliation -c "SELECT source, status, count(*) FROM transactions GROUP BY source, status ORDER BY source, status;"
docker compose exec postgres psql -U reconciliation -d reconciliation -c "SELECT id, status, maker, checker, signer FROM matches ORDER BY id DESC;"
```

## Elasticsearch

```text
POST /api/search/transactions/reindex
GET  /api/search/transactions/reindex
GET  /api/search/transactions?q=INV-1001&page=0&size=20
```

Reindex membaca PostgreSQL dan membangun ulang indeks. Status transaksi dan matching tetap dari PostgreSQL.

Tanpa Elasticsearch:

```text
APP_ELASTICSEARCH_ENABLED=false
SPRING_DATA_ELASTICSEARCH_REPOSITORIES_ENABLED=false
```

## Konfigurasi

| Variable | Default | Fungsi |
|---|---|---|
| `APP_IMPORT_INBOX` | `./inbox` | folder scheduler |
| `APP_IMPORT_PROCESSING` | `./import-processing` | file diproses |
| `APP_IMPORT_ARCHIVE` | `./import-archive` | file sukses |
| `APP_IMPORT_ERROR` | `./import-error` | file gagal |
| `APP_IMPORT_POLL_INTERVAL_MS` | `30000` | interval scan |
| `APP_IMPORT_CHUNK_SIZE` | `1000` | baris per batch insert |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/reconciliation` | PostgreSQL |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis |
| `SPRING_ELASTICSEARCH_URIS` | `http://localhost:9200` | Elasticsearch |

## Troubleshooting

| Gejala | Solusi |
|---|---|
| Aplikasi tidak sehat | tunggu Elasticsearch healthy; cek `docker compose ps` dan `docker compose logs app` |
| Batch tetap `RECEIVED` | import asynchronous; poll `GET /api/imports/{id}` |
| Batch `FAILED` | lihat `errorMessage`; perbaiki file lalu upload ulang |
| `rejectedRows > 0` | cek tanggal, amount, currency, jumlah kolom |
| `Row count mismatch` | trailer tidak sama dengan jumlah baris data |
| Kandidat kosong | sumber seberang belum ada, amount/currency beda, tanggal di luar toleransi, atau transaksi sudah terkunci |
| `400 Both transactions must be UNMATCHED` | transaksi sudah `PENDING_APPROVAL`/`RECONCILED` |
| `403` | `X-Role` salah atau user sama dengan peran sebelumnya |
| `409 Match must be SUBMITTED` | match sudah diproses |

Jangan mengubah `transactions`/`matches` manual lewat SQL. Gunakan API agar validasi, lock, status, dan audit tetap berjalan.

## Catatan desain

- Import divalidasi per baris: baris invalid ditolak, baris valid tetap masuk.
- Trailer hanya memeriksa jumlah baris; mismatch menggagalkan seluruh batch sebelum insert.
- Import membaca file per chunk, sehingga aman untuk file besar dari sisi memori.
- Kafka menangani antrean import; Redis hanya cache; Elasticsearch hanya indeks teks.
- Header `X-User`/`X-Role` hanya simulasi; produksi perlu autentikasi nyata.
- Ini demo modular monolith, bukan microservices terpisah.

## Teknologi

- Spring Boot, Spring Data JPA, Spring Cache, Spring Kafka
- PostgreSQL, Kafka, Redis, Elasticsearch
- SQL native untuk antrean, kandidat, bulk insert, dan report
- Docker Compose
