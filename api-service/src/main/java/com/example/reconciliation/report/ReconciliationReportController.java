package com.example.reconciliation.report;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Reports", description = "Reconciliation summaries")
@RestController
@RequestMapping("/api/reports")
public class ReconciliationReportController {
    private final JdbcTemplate jdbc;
    public ReconciliationReportController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/reconciliation")
    public List<StatusCount> reconciliationSummary() {
        return jdbc.query("""
            SELECT source, status, count(*) AS transaction_count
            FROM transactions
            GROUP BY source, status
            ORDER BY source, status
            """, (rs, row) -> new StatusCount(rs.getString("source"), rs.getString("status"), rs.getLong("transaction_count")));
    }

    public record StatusCount(String source, String status, long count) {}
}
