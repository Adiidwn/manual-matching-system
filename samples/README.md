# Contoh file import

Semua file memakai header dan urutan kolom yang sama:

```text
source,external_reference,transaction_date,amount,currency,description
```

- `source`: `BANK` atau `INTERNAL`
- `transaction_date`: `yyyy-MM-dd`
- `amount`: angka positif, pemisah desimal titik
- `currency`: 3 huruf, misalnya `IDR`
- `description`: boleh kosong, kutip jika mengandung koma

## Baris total (trailer)

Di bagian paling bawah file boleh ada satu baris total berisi jumlah baris data:

```text
source,external_reference,transaction_date,amount,currency,description
BANK,INV-1001,2025-03-01,150000.00,IDR,Transfer masuk
INTERNAL,INV-1001,2025-03-01,150000.00,IDR,Transfer keluar
2
```

Aturan trailer:

- hanya satu kolom dan berisi angka,
- harus menjadi baris tidak kosong terakhir,
- menghitung **semua baris data**, termasuk baris yang nanti ditolak,
- boleh memakai leading zero (`09` = 9),
- opsional; file tanpa trailer tetap diterima,
- jika angka trailer tidak sama dengan jumlah baris data, batch dibuat `FAILED` **sebelum** ada baris yang dimasukkan.

Angka trailer disimpan di field `declaredRows`, sedangkan jumlah baris data aktual ada di `totalRows`.

## Daftar file

| File | Isi | Perkiraan hasil |
|---|---|---|
| `transactions-sample.csv` | 3 pasang BANK + INTERNAL, trailer `6` | 6 baris valid, `declaredRows=6` |
| `transactions-with-total.csv` | 2 pasang baru, trailer `4` | 4 baris valid, `declaredRows=4` |
| `bank-statement-2025-03-01.csv` | hanya BANK, trailer `3` | 3 baris valid |
| `internal-ledger-2025-03-01.csv` | hanya INTERNAL, trailer `3` | 3 baris valid |
| `transactions-with-errors.csv` | campuran valid/invalid, trailer `09` | 9 baris: 4 valid, 5 ditolak, `declaredRows=9` |
| `transactions-bad-total.csv` | 1 baris data, trailer `5` | `FAILED`: jumlah baris tidak cocok |

## Cara memakai

Lewat upload:

```bash
curl.exe -sS -F "file=@samples/transactions-with-total.csv" http://localhost:8080/api/imports
```

Lewat scheduler, salin ke folder inbox lalu tunggu ±30 detik:

```powershell
New-Item -ItemType Directory -Force imports/inbox | Out-Null
Copy-Item samples/transactions-with-total.csv imports/inbox/
```

Untuk contoh bank + internal, import kedua file:

```bash
curl.exe -sS -F "file=@samples/bank-statement-2025-03-01.csv" http://localhost:8080/api/imports
curl.exe -sS -F "file=@samples/internal-ledger-2025-03-01.csv" http://localhost:8080/api/imports
```

Untuk melihat penolakan baris:

```bash
curl.exe -sS -F "file=@samples/transactions-with-errors.csv" http://localhost:8080/api/imports
curl.exe -sS http://localhost:8080/api/imports
```

Untuk melihat trailer yang tidak cocok:

```bash
curl.exe -sS -F "file=@samples/transactions-bad-total.csv" http://localhost:8080/api/imports
curl.exe -sS http://localhost:8080/api/imports
```

Batch terakhir akan berstatus `FAILED` dengan pesan seperti:

```text
Row count mismatch: file declares 5 rows but contains 1 data row(s)
```

Tidak ada baris dari file tersebut yang masuk ke database.
