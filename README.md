# Transaction Reconciliation

[![CI](https://github.com/Adiidwn/manual-matching-system/actions/workflows/ci.yml/badge.svg)](https://github.com/Adiidwn/manual-matching-system/actions/workflows/ci.yml)

Aplikasi rekonsiliasi transaksi: **file CSV → parse → PostgreSQL → manual matching maker → checker → signer**.

Monorepo dengan **dua deployable**:

| Deployable | Tanggung jawab | Port |
|---|---|---|
| `api-service` | REST API, matching workflow, report, search, upload/scan intake | `8080` |
| `import-worker` | konsumer Kafka, parsing CSV, insert transaksi, arsip file | — |

- **PostgreSQL** = sumber kebenaran (dipakai kedua service).
- **Kafka** = memproses import secara asynchronous (`file-import-requests`).
- **Redis** = cache kandidat (60 detik).
- **Elasticsearch** = pencarian teks (hanya API).

> Tidak ada UI. Semua operasi lewat REST API.

Dokumentasi endpoint lengkap: [`API.md`](API.md).
Arsitektur dan alur sistem: [`docs/architecture.md`](docs/architecture.md).
Koleksi Postman: [`postman/`](postman/README.md).
Swagger UI: `http://localhost:8080/swagger-ui.html`.
OpenAPI JSON: `http://localhost:8080/v3/api-docs`.

Alur singkat:

```text
file → parse → transaksi UNMATCHED → pilih kandidat → maker → checker → signer
```

## Daftar isi

- [API reference](API.md)
- [Arsitektur](docs/architecture.md)
- [Postman collection](postman/README.md)
- [Struktur monorepo](#struktur-monorepo)
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
- [Structured logging](#structured-logging)
- [Middleware](#middleware)
- [Testing dan CI](#testing-dan-ci)

## Struktur monorepo

```text
reconciliation/
├─ pom.xml                  parent (packaging pom)
├─ common/                  kontrak + model bersama
│  ├─ imports/              FileImportRequested, ImportBatch, ImportRepository, ImportStatus
│  └─ transaction/          TransactionSource
├─ api-service/             deployable #1
│  ├─ config/               CorrelationIdFilter, CacheConfiguration, OpenApiConfiguration
│  ├─ middleware/           ActorMiddleware, RequireRole, RoleAuthorizationInterceptor
│  ├─ imports/              ImportController, ImportService (intake), ImportView, scheduler
│  ├─ transaction/ workflow/ search/ report/
│  ├─ resources/db/migration  pemilik migrasi Flyway
│  └─ Dockerfile
├─ import-worker/           deployable #2
│  ├─ imports/              ImportConsumer, ImportProcessor, CsvFileParser
│  ├─ config/               WorkerCacheConfiguration
│  ├─ resources/application.yml
│  └─ Dockerfile
├─ compose.yaml             api + import-worker + PostgreSQL/Kafka/Redis/Elasticsearch
└─ .github/workflows/ci.yml test semua modul, build 2 image, publish ke GHCR
```

Alur import setelah dipisah:

```text
upload/scan → api-service: hash + register batch + pindah file ke processing
            → Kafka: file-import-requests
            → import-worker: parse + insert + arsipkan file
```

`api-service` pemilik skema (Flyway). `import-worker` memakai `spring.flyway.enabled=false` dan menunggu API sehat sebelum start.

## Menjalankan

Butuh Docker + Compose.

```shell
docker compose up --build -d
docker compose ps
```

`compose.yaml` menjalankan dua container aplikasi: `api` (port 8080) dan `import-worker` (tanpa port). Worker menunggu API `healthy` agar skema Flyway sudah siap.

Tunggu sampai semua service `healthy`, lalu cek:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

```json
{"status":"UP"}
```

API: `http://localhost:8080`

Swagger UI: `http://localhost:8080/swagger-ui.html`  
OpenAPI JSON: `http://localhost:8080/v3/api-docs`

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

Scheduler berjalan di `api-service` dan memindai folder setiap 30 detik. File harus `.csv` dan sudah stabil minimal 3 detik. Setelah registrasi, file diproses oleh `import-worker` lewat Kafka.

### 2. Upload

```powershell
curl.exe -F "file=@samples/transactions-sample.csv" http://localhost:8080/api/imports
```

Upload langsung didaftarkan oleh API dan dikirim ke `import-worker`; tidak perlu menunggu scheduler. Maksimal 100 MB.

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

# Ringkasan terfilter (tanggal + referensi)
Invoke-RestMethod "http://localhost:8080/api/reports/reconciliation?fromDate=2025-03-01&toDate=2025-03-31&externalReference=INV"

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

| Variable | Service | Default | Fungsi |
|---|---|---|---|
| `APP_IMPORT_INBOX` | api | `./inbox` | folder scheduler |
| `APP_IMPORT_PROCESSING` | api + worker | `./import-processing` | file yang sedang diproses |
| `APP_IMPORT_ARCHIVE` | worker | `./import-archive` | file sukses |
| `APP_IMPORT_ERROR` | worker | `./import-error` | file gagal |
| `APP_IMPORT_POLL_INTERVAL_MS` | api | `30000` | interval scan |
| `APP_IMPORT_CHUNK_SIZE` | worker | `1000` | baris per batch insert |
| `SPRING_DATASOURCE_URL` | api + worker | `jdbc:postgresql://localhost:5432/reconciliation` | PostgreSQL |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | api + worker | `localhost:9092` | Kafka |
| `SPRING_DATA_REDIS_HOST` | api + worker | `localhost` | Redis |
| `SPRING_ELASTICSEARCH_URIS` | api | `http://localhost:9200` | Elasticsearch |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | api + worker | — | `ecs` untuk log JSON ECS |

## Troubleshooting

| Gejala | Solusi |
|---|---|
| Aplikasi tidak sehat | tunggu Elasticsearch healthy; cek `docker compose ps`, `docker compose logs api`, `docker compose logs import-worker` |
| Batch tetap `RECEIVED` | worker belum mengonsumsi Kafka; cek `docker compose logs import-worker` |
| Batch `FAILED` | lihat `errorMessage`; perbaiki file lalu upload ulang |
| `rejectedRows > 0` | cek tanggal, amount, currency, jumlah kolom |
| `Row count mismatch` | trailer tidak sama dengan jumlah baris data |
| Kandidat kosong | sumber seberang belum ada, amount/currency beda, tanggal di luar toleransi, atau transaksi sudah terkunci |
| `400 Both transactions must be UNMATCHED` | transaksi sudah `PENDING_APPROVAL`/`RECONCILED` |
| `403` | `X-Role` salah atau user sama dengan peran sebelumnya |
| `409 Match must be SUBMITTED` | match sudah diproses |

Jangan mengubah `transactions`/`matches` manual lewat SQL. Gunakan API agar validasi, lock, status, dan audit tetap berjalan.

## Structured logging

Secara default log tampil plain text. Di Docker, `compose.yaml` mengaktifkan JSON ECS:

```yaml
LOGGING_STRUCTURED_FORMAT_CONSOLE: ecs
```

Setiap request mendapat correlation id:

- dari header `X-Request-Id`, atau UUID jika tidak dikirim,
- dikembalikan di response header `X-Request-Id`,
- masuk ke MDC sebagai `requestId`.

Identitas demo dibaca sekali oleh middleware:

- `X-User` → MDC `user` dan atribut request `Actor`,
- `X-Role` → MDC `role`, divalidasi `MAKER`/`CHECKER`/`SIGNER`,
- role yang tidak dikenal atau `X-Role` tanpa `X-User` ditolak `400`.

Log import menyertakan `batchId` dan `fileName`. Log matching menyertakan `matchId` dan `actor`.

```powershell
curl.exe -i -H "X-Request-Id: demo-123" http://localhost:8080/actuator/health
docker compose logs -f app
```

## Middleware

Logika lintas endpoint tidak ditulis di controller. Urutan eksekusinya:

| Middleware | File | Fungsi |
|---|---|---|
| `CorrelationIdFilter` | `config/CorrelationIdFilter.java` | membuat/meneruskan `X-Request-Id` ke MDC |
| `ActorMiddleware` | `middleware/ActorMiddleware.java` | memvalidasi `X-User`/`X-Role` sekali, menyimpan `Actor` + MDC |
| `RoleAuthorizationInterceptor` | `middleware/RoleAuthorizationInterceptor.java` | menegakkan `@RequireRole` sebelum controller dipanggil |
| `MiddlewareConfiguration` | `middleware/MiddlewareConfiguration.java` | mendaftarkan interceptor untuk `/api/**` |

Controller matching kini hanya mendeklarasikan kebutuhan role:

```java
@RequireRole("MAKER")
public MatchView create(@RequestAttribute(Actor.REQUEST_ATTRIBUTE) Actor actor,
                        @Valid @RequestBody CreateRequest request) {
    return service.create(request, actor.user());
}
```

`MatchingService` tidak lagi mengurus MDC `actor`; nilai itu sudah dipasang middleware. Penolakan role terjadi sebagai `403` sebelum body/argumen controller diproses.


## Testing dan CI

```shell
mvn test
mvn verify
```

Test mencakup parser CSV/trailer, lifecycle batch import, validasi antrean, pencarian kandidat, middleware, dan alur maker-checker-signer.

Per modul:

```shell
mvn -pl api-service -am test
mvn -pl import-worker -am test
```

Pipeline GitHub Actions ada di `.github/workflows/ci.yml`:

- `mvn verify` untuk test + build semua modul,
- build dua Docker image (`api-service/Dockerfile`, `import-worker/Dockerfile`),
- publish dua image ke GHCR jika push tag `v*`:
  - `ghcr.io/<owner>/<repo>-api:<tag>`
  - `ghcr.io/<owner>/<repo>-worker:<tag>`.

## Catatan desain

- Import divalidasi per baris: baris invalid ditolak, baris valid tetap masuk.
- Trailer hanya memeriksa jumlah baris; mismatch menggagalkan seluruh batch sebelum insert.
- Import membaca file per chunk, sehingga aman untuk file besar dari sisi memori.
- Kafka menangani antrean import; Redis hanya cache; Elasticsearch hanya indeks teks.
- Header `X-User`/`X-Role` hanya simulasi; produksi perlu autentikasi nyata.
- API dan worker adalah dua deployable dengan database yang sama (shared database). Pembagian berikutnya: worker memiliki tabel import/transaksi tulis, API memiliki workflow match, lalu event untuk sinkronisasi.
- Migrasi Flyway hanya dijalankan `api-service`; worker menunggu API sehat.

## Teknologi

- Spring Boot, Spring Data JPA, Spring Cache, Spring Kafka
- PostgreSQL, Kafka, Redis, Elasticsearch
- SQL native untuk antrean, kandidat, bulk insert, dan report
- Monorepo Maven multi-module, Docker multi-stage, GitHub Actions + GHCR
