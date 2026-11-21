package com.example.reconciliation.report;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Reports", description = "Reconciliation summaries")
@RestController
@RequestMapping("/api/reports")
public class ReconciliationReportController {
    private static final int MAX_REFERENCE_LENGTH = 100;

    /**
     * Composition per source: one row per (source, status) with its share of the
     * source and its share of the whole filtered set. Filters are applied before
     * aggregation, so totalRows and pctOfAll describe the filtered data.
     */
    private static final String SUMMARY_SQL = """
            WITH params AS (
                SELECT CAST(:fromDate AS date) AS from_date,
                       CAST(:toDate AS date)   AS to_date,
                       CAST(:reference AS text) AS reference
            ),
            filtered AS (
                SELECT t.source, t.status, t.amount
                FROM transactions t
                CROSS JOIN params p
                WHERE (p.from_date IS NULL OR t.transaction_date >= p.from_date)
                  AND (p.to_date IS NULL OR t.transaction_date <= p.to_date)
                  AND (p.reference IS NULL
                       OR POSITION(LOWER(p.reference) IN LOWER(t.external_reference)) > 0)
            ),
            per_source_status AS (
                SELECT source,
                       status,
                       count(*)    AS row_count,
                       sum(amount) AS amount
                FROM filtered
                GROUP BY source, status
            ),
            per_source AS (
                SELECT source,
                       sum(row_count) AS total_rows,
                       sum(amount)    AS total_amount
                FROM per_source_status
                GROUP BY source
            ),
            overall AS (
                SELECT sum(total_rows) AS total_rows
                FROM per_source
            )
            SELECT s.source,
                   s.status,
                   s.row_count,
                   s.amount,
                   p.total_rows,
                   p.total_amount,
                   coalesce(round(100.0 * s.row_count / nullif(p.total_rows, 0), 2), 0) AS pct_of_source,
                   coalesce(round(100.0 * s.row_count / nullif(o.total_rows, 0), 2), 0) AS pct_of_all
            FROM per_source_status s
            JOIN per_source p ON p.source = s.source
            CROSS JOIN overall o
            ORDER BY s.source, s.status
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ReconciliationReportController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/reconciliation")
    public List<StatusShare> reconciliationSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String externalReference) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate must be before or equal to toDate");
        }
        String reference = normalizeReference(externalReference);

        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromDate", fromDate == null ? null : fromDate.toString())
                .addValue("toDate", toDate == null ? null : toDate.toString())
                .addValue("reference", reference);

        return jdbc.query(SUMMARY_SQL, params, (rs, row) -> new StatusShare(
                rs.getString("source"),
                rs.getString("status"),
                rs.getLong("row_count"),
                rs.getBigDecimal("amount"),
                rs.getLong("total_rows"),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("pct_of_source"),
                rs.getBigDecimal("pct_of_all")));
    }

    private static String normalizeReference(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String reference = value.trim();
        if (reference.length() > MAX_REFERENCE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "externalReference must be at most " + MAX_REFERENCE_LENGTH + " characters");
        }
        return reference;
    }

    public record StatusShare(String source,
                              String status,
                              long rowCount,
                              BigDecimal amount,
                              long totalRows,
                              BigDecimal totalAmount,
                              BigDecimal pctOfSource,
                              BigDecimal pctOfAll) {
    }
}
