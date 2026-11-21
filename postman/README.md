# Postman Collection

Koleksi Postman untuk semua endpoint **Transaction Reconciliation API**.

## File

| File | Fungsi |
|---|---|
| `Transaction-Reconciliation.postman_collection.json` | Semua request + test script |
| `Reconciliation-Local.postman_environment.json` | Environment `baseUrl` |

## Import

1. Jalankan aplikasi:

   ```shell
   docker compose up -d
   ```

2. Buka Postman → **Import** → pilih kedua file JSON di folder ini.
3. Pilih environment **Reconciliation Local**.
4. Pastikan `baseUrl` = `http://localhost:8080`.

## Variabel

Variabel ID diisi otomatis oleh test script:

| Variabel | Diisi oleh |
|---|---|
| `bankTransactionId` | request **Queue BANK UNMATCHED** |
| `internalTransactionId` | request **Candidates for BANK** |
| `matchId` | request **Create Match (MAKER)** |
| `importId` | request **Upload CSV** |

`searchQuery` default `INV-1001`.

## Urutan menjalankan

### 1. Health

Jalankan **00 - Health → Health check**. Response harus `UP`.

### 2. Import (opsional)

Jika database masih kosong:

1. **01 - Import → Upload CSV**.
2. Tunggu sampai **Get Import Detail** menunjukkan `COMPLETED`.
3. Baru lanjut ke folder Transactions.

### 3. Manual matching

Jalankan berurutan:

```text
02 - Transactions → Queue BANK UNMATCHED
02 - Transactions → Candidates for BANK
03 - Matching     → Create Match (MAKER)
03 - Matching     → Checker Approve
03 - Matching     → Signer Approve
03 - Matching     → Get Match Detail + Audit
```

Script akan mengisi `bankTransactionId`, `internalTransactionId`, dan `matchId` secara otomatis.

### 4. Report dan Search

- **04 - Reports → Reconciliation Summary**
- **05 - Search → Reindex Start**
- **05 - Search → Reindex Status**
- **05 - Search → Search Transactions**

Search membutuhkan Elasticsearch aktif.

## Reject

Request **Checker Reject (example)** dan **Signer Reject (example)** memakai variabel `matchId` yang sama.

Gunakan pada match baru yang masih di tahap yang sesuai:

- checker reject → match `SUBMITTED`,
- signer reject → match `CHECKED`.

Jika dijalankan setelah approve, response `409`.

## Catatan

- Import asynchronous. `RECEIVED` bukan error; poll detail batch.
- Endpoint matching membutuhkan `X-User` dan `X-Role`; header sudah diset di collection.
- `maker`, `checker`, dan `signer` harus berbeda.
- Jika `bankTransactionId` kosong, jalankan **Queue BANK UNMATCHED** terlebih dahulu.
- Jika file upload tidak terbaca, pilih ulang `samples/transactions-sample.csv` secara manual di tab Body → form-data.
- Jangan mengisi variabel ID di environment dengan nilai kosong; environment mengalahkan collection variable.
