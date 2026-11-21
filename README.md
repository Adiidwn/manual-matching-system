# Transaction Reconciliation

Demo aplikasi rekonsiliasi transaksi dengan pencocokan manual dan alur **maker → checker → signer**. PostgreSQL menjadi sumber data utama. Transaksi dimuat melalui Kafka, antrean dan kandidat dicari dengan SQL, Redis menyimpan kandidat selama 60 detik, dan Elasticsearch menyediakan pencarian teks setelah proses reindex.

## Menjalankan

Butuh Docker dengan Compose dan Java 17/Maven bila menjalankan aplikasi di luar container.

```shell
docker compose up --build
```

API tersedia di `http://localhost:8080`; health check di `/actuator/health`.

## Memuat data sintetis

Kirim permintaan untuk membuat sampai satu juta transaksi. Kafka memproses pekerjaan secara asynchronous; status dapat dipantau lewat endpoint kedua.

```powershell
Invoke-RestMethod -Method Post 'http://localhost:8080/api/demo-data?count=1000000'
Invoke-RestMethod 'http://localhost:8080/api/demo-data'
```

Pasangan demo dibuat berurutan: baris `BANK` dan `INTERNAL` memiliki nominal yang sama dan tanggal berdekatan. Gunakan pencarian antrean ber-pagination; jangan memuat satu juta baris sekaligus.

```text
GET /api/transactions?status=UNMATCHED&source=BANK&page=0&size=50
GET /api/transactions/1/candidates?toleranceDays=3&limit=10
GET /api/reports/reconciliation
```

GET antrean menerima filter `status`, `source`, `fromDate`, dan `toDate`. Ukuran halaman maksimum 100. Kandidat dibatasi maksimal 50 hasil dan hanya memakai transaksi dari sumber seberang dengan nominal serta mata uang yang sama.

## Alur maker-checker-signer

Header `X-User` dan `X-Role` mensimulasikan identitas/peran untuk demo ini. Contoh berikut mengajukan pasangan transaksi ID 1 dan 2:

```powershell
$headers = @{ 'X-User' = 'maker-1'; 'X-Role' = 'MAKER' }
$body = @{ bankTransactionId = 1; internalTransactionId = 2 } | ConvertTo-Json
$match = Invoke-RestMethod -Method Post 'http://localhost:8080/api/matches' -Headers $headers -ContentType 'application/json' -Body $body
$matchId = $match.id
```

Checker menyetujui pengajuan:

```powershell
$headers = @{ 'X-User' = 'checker-1'; 'X-Role' = 'CHECKER' }
Invoke-RestMethod -Method Post "http://localhost:8080/api/matches/$matchId/checker-decision" -Headers $headers -ContentType 'application/json' -Body '{"approve":true}'
```

Signer menyelesaikan rekonsiliasi:

```powershell
$headers = @{ 'X-User' = 'signer-1'; 'X-Role' = 'SIGNER' }
Invoke-RestMethod -Method Post "http://localhost:8080/api/matches/$matchId/signer-decision" -Headers $headers -ContentType 'application/json' -Body '{"approve":true}'
Invoke-RestMethod "http://localhost:8080/api/matches/$matchId"
```

Maker tidak dapat menjadi checker atau signer pada pengajuan yang sama, checker dan signer harus berbeda, dan penolakan memerlukan alasan. Setiap tindakan disimpan di audit trail. Header role hanya untuk demo; deployment nyata perlu autentikasi dan otorisasi.

## Pencarian Elasticsearch

Setelah data dibuat, bangun indeks dari PostgreSQL (bisa memakan waktu untuk satu juta baris), lalu pantau statusnya:

```text
POST /api/search/transactions/reindex
GET  /api/search/transactions/reindex
GET  /api/search/transactions?q=INV-123&page=0&size=20
```

Status transaksi dan keputusan workflow tetap dibaca dari PostgreSQL; indeks Elasticsearch dipakai untuk pencarian teks dan dapat dibangun ulang. Data demo serta status pekerjaan disimpan untuk eksplorasi lokal, bukan sebagai mekanisme import produksi.

## Struktur teknologi

- **Spring IoC / Java Stream:** layanan modular, validasi workflow, dan pemetaan hasil query.
- **Advanced SQL:** pencarian antrean dan kandidat, keyset pagination saat membangun indeks, serta agregasi control report.
- **Kafka:** pekerjaan pemuatan demo dijalankan asynchronous melalui event.
- **Redis:** cache hasil kandidat dengan TTL 60 detik; cache dikosongkan saat status matching berubah.
- **Elasticsearch:** pencarian teks deskripsi dan referensi transaksi.
- **Containerization:** Docker Compose menjalankan aplikasi, PostgreSQL, Kafka, Redis, dan Elasticsearch.

Ini project demo lokal berskala kecil, bukan deployment bank siap produksi. Aplikasi masih satu Spring Boot modular, bukan microservices terpisah.
