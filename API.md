# API Reference

Base URL:

```text
http://localhost:8080
```

Semua request dan response memakai JSON, kecuali upload file (`multipart/form-data`).

Dokumentasi pemakaian aplikasi: [`README.md`](README.md).
Koleksi Postman: [`postman/Transaction-Reconciliation.postman_collection.json`](postman/Transaction-Reconciliation.postman_collection.json).
Swagger UI: `http://localhost:8080/swagger-ui.html` — OpenAPI JSON: `http://localhost:8080/v3/api-docs`.

## Ringkasan endpoint

| Method | Path | Fungsi |
|---|---|---|
| `GET` | `/actuator/health` | Health check |
| `POST` | `/api/imports` | Upload file CSV |
| `POST` | `/api/imports/scan` | Scan folder inbox secara manual |
| `GET` | `/api/imports` | 50 batch import terbaru |
| `GET` | `/api/imports/{id}` | Detail batch import |
| `GET` | `/api/transactions` | Antrean transaksi + filter |
| `GET` | `/api/transactions/{id}/candidates` | Kandidat pasangan |
| `POST` | `/api/matches` | Maker mengajukan match |
| `GET` | `/api/matches` | Daftar match + filter |
| `GET` | `/api/matches/{id}` | Detail match + audit |
| `POST` | `/api/matches/{id}/checker-decision` | Keputusan checker |
| `POST` | `/api/matches/{id}/signer-decision` | Keputusan signer |
| `GET` | `/api/reports/reconciliation` | Ringkasan rekonsiliasi |
| `POST` | `/api/search/transactions/reindex` | Mulai reindex Elasticsearch |
| `GET` | `/api/search/transactions/reindex` | Status reindex |
| `GET` | `/api/search/transactions` | Pencarian teks |

## Konvensi

- Tanggal: `yyyy-MM-dd`.
- Amount: angka desimal, misalnya `150000.00`.
- Pagination: `page` mulai dari `0`.
- Setiap response menyertakan header `X-Request-Id` untuk korelasi log.
- Header identitas (khusus matching):
  - `X-User`: nama pengguna, maksimal 100 karakter.
  - `X-Role`: `MAKER`, `CHECKER`, atau `SIGNER`.
- Kode status umum:
  - `200` sukses.
  - `201` data dibuat.
  - `202` diterima untuk diproses asynchronous.
  - `400` validasi gagal.
  - `403` role/aktor tidak sesuai.
  - `404` data tidak ditemukan.
  - `409` konflik status.
  - `500` error tak terduga.

---

## Health

### `GET /actuator/health`

```bash
curl.exe -sS http://localhost:8080/actuator/health
```

```json
{"status":"UP"}
```

---

## Import

### `POST /api/imports`

Upload file CSV. Proses berjalan asynchronous lewat Kafka.

- Content-Type: `multipart/form-data`
- Field: `file`
- Maksimal 100 MB
- Response: `202 Accepted`

```bash
curl.exe -sS -F "file=@samples/transactions-sample.csv" \
  http://localhost:8080/api/imports
```

Response awal:

```json
{
  "id": 10,
  "fileName": "transactions-sample.csv",
  "status": "RECEIVED",
  "totalRows": 0,
  "declaredRows": null,
  "processedRows": 0,
  "insertedRows": 0,
  "duplicateRows": 0,
  "rejectedRows": 0,
  "errorMessage": null,
  "createdAt": "2026-11-21T12:50:03Z",
  "finishedAt": null
}
```

Status diproses di background. Poll `GET /api/imports/{id}`.

### `POST /api/imports/scan`

Memindai `inbox/` tanpa menunggu scheduler.

- Response: `202 Accepted`
- Body: jumlah file yang didaftarkan (angka)

```bash
curl.exe -sS -X POST http://localhost:8080/api/imports/scan
```

```json
2
```

### `GET /api/imports`

Mengembalikan maksimal 50 batch terbaru.

```bash
curl.exe -sS http://localhost:8080/api/imports
```

### `GET /api/imports/{id}`

Detail satu batch.

```bash
curl.exe -sS http://localhost:8080/api/imports/10
```

Field penting:

| Field | Arti |
|---|---|
| `status` | `RECEIVED`, `PROCESSING`, `COMPLETED`, `FAILED` |
| `totalRows` | baris data aktual |
| `declaredRows` | angka trailer, `null` jika tidak ada |
| `insertedRows` | baris baru |
| `duplicateRows` | baris yang sudah ada |
| `rejectedRows` | baris invalid |
| `errorMessage` | penyebab gagal |

Status `404` jika batch tidak ada.

---

## Transaksi

### `GET /api/transactions`

Antrean transaksi dengan filter dan pagination.

| Parameter | Tipe | Arti |
|---|---|---|
| `status` | enum | `UNMATCHED`, `PENDING_APPROVAL`, `RECONCILED` |
| `source` | enum | `BANK`, `INTERNAL` |
| `batchId` | long | filter batch import |
| `externalReference` | string | pencarian sebagian, case-insensitive |
| `minAmount` | decimal | amount minimum, inklusif |
| `maxAmount` | decimal | amount maksimum, inklusif |
| `fromDate` | date | tanggal mulai, inklusif |
| `toDate` | date | tanggal akhir, inklusif |
| `page` | int | default `0` |
| `size` | int | default `50`, maksimal `100` |

Contoh:

```bash
curl.exe -sS "http://localhost:8080/api/transactions?status=UNMATCHED&source=BANK&page=0&size=50"
curl.exe -sS "http://localhost:8080/api/transactions?externalReference=INV-1001"
curl.exe -sS "http://localhost:8080/api/transactions?minAmount=100000&maxAmount=500000"
curl.exe -sS "http://localhost:8080/api/transactions?fromDate=2025-03-01&toDate=2025-03-31"
```

Response (disederhanakan):

```json
{
  "content": [
    {
      "id": 26,
      "source": "BANK",
      "externalReference": "TRL-1002",
      "transactionDate": "2025-05-02",
      "amount": 125000.00,
      "currency": "IDR",
      "description": "Second pair",
      "status": "UNMATCHED",
      "importBatchId": 6
    }
  ],
  "totalElements": 10,
  "totalPages": 1,
  "number": 0,
  "size": 50
}
```

Validasi:

- `size` di luar `1..100` → `400`
- `minAmount`/`maxAmount` negatif → `400`
- `minAmount > maxAmount` → `400`
- `externalReference` lebih dari 100 karakter → `400`

### `GET /api/transactions/{id}/candidates`

Mencari kandidat pasangan dari sumber seberang.

| Parameter | Default | Batas |
|---|---|---|
| `toleranceDays` | `3` | `0..30` |
| `limit` | `10` | `1..50` |

```bash
curl.exe -sS "http://localhost:8080/api/transactions/26/candidates?toleranceDays=3&limit=10"
```

```json
[
  {
    "id": 27,
    "source": "INTERNAL",
    "externalReference": "TRL-1002",
    "transactionDate": "2025-05-02",
    "amount": 125000.00,
    "currency": "IDR",
    "description": "Second pair",
    "status": "UNMATCHED"
  }
]
```

Aturan kandidat:

- sumber seberang,
- status `UNMATCHED`,
- amount dan currency sama persis,
- tanggal dalam `transactionDate ± toleranceDays`,
- maksimal 50 hasil.

Kemungkinan error:

- `404` transaksi target tidak ada.
- `409` transaksi target bukan `UNMATCHED`.
- `400` parameter di luar batas.

---

## Matching

### `POST /api/matches` (maker)

Header:

```text
X-User: maker-1
X-Role: MAKER
Content-Type: application/json
```

Body:

```json
{
  "bankTransactionId": 26,
  "internalTransactionId": 27
}
```

```bash
curl.exe -sS -X POST http://localhost:8080/api/matches \
  -H "Content-Type: application/json" \
  -H "X-User: maker-1" \
  -H "X-Role: MAKER" \
  -d '{"bankTransactionId":26,"internalTransactionId":27}'
```

Response: `201 Created`

```json
{
  "id": 3,
  "bankTransactionId": 26,
  "internalTransactionId": 27,
  "status": "SUBMITTED",
  "maker": "maker-1",
  "checker": null,
  "signer": null,
  "rejectionReason": null,
  "createdAt": "2025-05-01T10:00:00Z",
  "updatedAt": "2025-05-01T10:00:00Z"
}
```

Validasi:

- satu transaksi `BANK`, satu `INTERNAL`,
- keduanya `UNMATCHED`,
- amount dan currency sama,
- `403` jika role bukan `MAKER`,
- `409` jika salah satu transaksi sudah punya match aktif.

### `GET /api/matches`

Daftar match dengan filter dan pagination.

| Parameter | Arti |
|---|---|
| `status` | `SUBMITTED`, `CHECKED`, `SIGNED`, `REJECTED` |
| `maker` | nama maker, exact case-insensitive |
| `checker` | nama checker, exact case-insensitive |
| `signer` | nama signer, exact case-insensitive |
| `page` | default `0` |
| `size` | default `20`, maksimal `100` |

```bash
curl.exe -sS "http://localhost:8080/api/matches?status=SUBMITTED&page=0&size=20"
curl.exe -sS "http://localhost:8080/api/matches?maker=maker-1"
curl.exe -sS "http://localhost:8080/api/matches?checker=checker-1"
curl.exe -sS "http://localhost:8080/api/matches?signer=signer-1"
```

Response:

```json
{
  "content": [
    {
      "id": 3,
      "bankTransactionId": 26,
      "internalTransactionId": 27,
      "status": "SUBMITTED",
      "maker": "maker-1",
      "checker": null,
      "signer": null,
      "rejectionReason": null,
      "createdAt": "2025-05-01T10:00:00Z",
      "updatedAt": "2025-05-01T10:00:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

Daftar tidak memuat audit trail. Audit lengkap ada di `GET /api/matches/{id}`.

### `GET /api/matches/{id}`

Detail match + audit trail.

```bash
curl.exe -sS http://localhost:8080/api/matches/3
```

```json
{
  "match": {
    "id": 3,
    "bankTransactionId": 26,
    "internalTransactionId": 27,
    "status": "SIGNED",
    "maker": "maker-1",
    "checker": "checker-1",
    "signer": "signer-1",
    "rejectionReason": null,
    "createdAt": "2025-05-01T10:00:00Z",
    "updatedAt": "2025-05-01T10:05:00Z"
  },
  "audit": [
    {
      "id": 1,
      "actor": "maker-1",
      "role": "MAKER",
      "action": "SUBMIT",
      "note": null,
      "occurredAt": "2025-05-01T10:00:00Z"
    },
    {
      "id": 2,
      "actor": "checker-1",
      "role": "CHECKER",
      "action": "APPROVE",
      "note": null,
      "occurredAt": "2025-05-01T10:03:00Z"
    },
    {
      "id": 3,
      "actor": "signer-1",
      "role": "SIGNER",
      "action": "APPROVE",
      "note": null,
      "occurredAt": "2025-05-01T10:05:00Z"
    }
  ]
}
```

Status `404` jika match tidak ada.

### `POST /api/matches/{id}/checker-decision`

Header:

```text
X-User: checker-1
X-Role: CHECKER
```

Approve:

```bash
curl.exe -sS -X POST http://localhost:8080/api/matches/3/checker-decision \
  -H "Content-Type: application/json" \
  -H "X-User: checker-1" \
  -H "X-Role: CHECKER" \
  -d '{"approve":true}'
```

Reject:

```bash
curl.exe -sS -X POST http://localhost:8080/api/matches/3/checker-decision \
  -H "Content-Type: application/json" \
  -H "X-User: checker-1" \
  -H "X-Role: CHECKER" \
  -d '{"approve":false,"reason":"Nominal tidak sesuai mutasi bank"}'
```

Aturan:

- match harus `SUBMITTED`,
- checker tidak boleh sama dengan maker,
- reject wajib `reason` (maksimal 500 karakter),
- approve → `CHECKED`,
- reject → `REJECTED`, transaksi kembali `UNMATCHED`.

### `POST /api/matches/{id}/signer-decision`

Header:

```text
X-User: signer-1
X-Role: SIGNER
```

Body sama: `approve` dan `reason`.

```bash
curl.exe -sS -X POST http://localhost:8080/api/matches/3/signer-decision \
  -H "Content-Type: application/json" \
  -H "X-User: signer-1" \
  -H "X-Role: SIGNER" \
  -d '{"approve":true}'
```

Aturan:

- match harus `CHECKED`,
- signer tidak boleh sama dengan maker atau checker,
- approve → `SIGNED`, transaksi menjadi `RECONCILED`,
- reject → `REJECTED`, transaksi kembali `UNMATCHED`.

---

## Report

### `GET /api/reports/reconciliation`

```bash
curl.exe -sS http://localhost:8080/api/reports/reconciliation
```

```json
[
  {"source": "BANK", "status": "RECONCILED", "count": 2},
  {"source": "BANK", "status": "UNMATCHED", "count": 10},
  {"source": "INTERNAL", "status": "RECONCILED", "count": 2},
  {"source": "INTERNAL", "status": "UNMATCHED", "count": 13}
]
```

---

## Search

Endpoint ini hanya tersedia jika Elasticsearch aktif.

### `POST /api/search/transactions/reindex`

```bash
curl.exe -sS -X POST http://localhost:8080/api/search/transactions/reindex
```

```json
{"status":"QUEUED","indexed":0,"total":23,"error":null}
```

`409` jika reindex lain masih berjalan.

### `GET /api/search/transactions/reindex`

```bash
curl.exe -sS http://localhost:8080/api/search/transactions/reindex
```

```json
{"status":"COMPLETED","indexed":23,"total":23,"error":null}
```

Status: `IDLE`, `QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED`.

### `GET /api/search/transactions`

| Parameter | Aturan |
|---|---|
| `q` | wajib, 1–100 karakter |
| `page` | default `0` |
| `size` | default `20`, maksimal `50` |

```bash
curl.exe -sS "http://localhost:8080/api/search/transactions?q=INV-1001&page=0&size=20"
```

Response berisi dokumen transaksi dari Elasticsearch (tanpa status):

```json
{
  "content": [
    {
      "id": "5",
      "source": "BANK",
      "externalReference": "INV-1001",
      "transactionDate": "2025-03-01",
      "amount": 150000.00,
      "currency": "IDR",
      "description": "Transfer from PT Alpha"
    }
  ],
  "totalElements": 1
}
```

---

## Status workflow

### Transaksi

```text
UNMATCHED → PENDING_APPROVAL → RECONCILED
     ↑                              │
     └──────── reject ──────────────┘
```

### Match

```text
SUBMITTED → CHECKED → SIGNED
     │          │
     └─ reject ─┴─ reject → REJECTED
```

## Error umum

| Status | Penyebab |
|---|---|
| `400` | parameter/body tidak valid, amount/currency tidak sama |
| `403` | `X-Role` salah, checker = maker, signer = maker/checker |
| `404` | transaksi/batch/match tidak ditemukan |
| `409` | transaksi bukan `UNMATCHED`, match bukan `SUBMITTED`/`CHECKED`, reindex masih berjalan |
| `500` | error tak terduga, misalnya infrastruktur pendukung bermasalah |

Jika trailer tidak cocok, upload tetap `202`; batch berubah menjadi `FAILED` dan `errorMessage` berisi `Row count mismatch`. Tidak ada baris yang dimasukkan dari file tersebut.

Jangan mengubah tabel `transactions` atau `matches` manual lewat SQL. Gunakan endpoint matching agar validasi, lock, status, dan audit tetap berjalan.
