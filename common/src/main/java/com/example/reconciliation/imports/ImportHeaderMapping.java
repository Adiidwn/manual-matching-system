package com.example.reconciliation.imports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A manually maintained header alias that maps a file header name to one of the
 * canonical import columns.
 */
@Entity
@Table(name = "import_header_mappings")
public class ImportHeaderMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "canonical_column", nullable = false, length = 64)
    private String canonicalColumn;

    @Column(name = "header_name", nullable = false, length = 128)
    private String headerName;

    protected ImportHeaderMapping() {}

    public ImportHeaderMapping(String canonicalColumn, String headerName) {
        this.canonicalColumn = canonicalColumn;
        this.headerName = headerName;
    }

    public Long getId() { return id; }
    public String getCanonicalColumn() { return canonicalColumn; }
    public String getHeaderName() { return headerName; }
}
