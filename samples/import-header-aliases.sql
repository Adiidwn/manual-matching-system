-- Contoh parameter alias header. Jalankan manual ke database reconciliation.
-- Ubah canonical_column/header_name sesuai file yang akan diimpor.

INSERT INTO import_header_mappings (canonical_column, header_name) VALUES
    ('source', 'Sumber'),
    ('external_reference', 'No Referensi'),
    ('transaction_date', 'Tanggal'),
    ('amount', 'Nominal'),
    ('currency', 'Mata Uang'),
    ('description', 'Keterangan')
ON CONFLICT DO NOTHING;
