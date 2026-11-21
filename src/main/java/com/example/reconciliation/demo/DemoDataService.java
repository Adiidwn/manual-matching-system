package com.example.reconciliation.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DemoDataService {
    private static final int BATCH_SIZE = 2_000;
    private final JdbcTemplate jdbc;
    private final AtomicReference<ImportState> state = new AtomicReference<>(new ImportState("IDLE", 0, 0, null));

    public DemoDataService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public synchronized ImportState start(int count) {
        if (count < 1 || count > 1_000_000) throw new IllegalArgumentException("count must be between 1 and 1000000");
        String current = state.get().status();
        if ("QUEUED".equals(current) || "PROCESSING".equals(current))
            throw new IllegalStateException("A demo import is already running");
        state.set(new ImportState("QUEUED", 0, count, null));
        CompletableFuture.runAsync(() -> generate(count));
        return state.get();
    }

    public ImportState getState() { return state.get(); }

    private void generate(int count) {
        state.set(new ImportState("PROCESSING", 0, count, null));
        try {
            for (int start = 0; start < count; start += BATCH_SIZE) {
                int batchStart = start;
                int end = Math.min(batchStart + BATCH_SIZE, count);
                jdbc.batchUpdate("INSERT INTO transactions (source, external_reference, transaction_date, amount, currency, description, status) VALUES (?, ?, ?, ?, 'IDR', ?, 'UNMATCHED')",
                    new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                        public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                            int row = batchStart + i;
                            ps.setString(1, row % 2 == 0 ? "BANK" : "INTERNAL");
                            ps.setString(2, "DEMO-" + (row / 2));
                            ps.setDate(3, Date.valueOf(LocalDate.of(2025, 1, 1).plusDays(row % 365)));
                            ps.setBigDecimal(4, BigDecimal.valueOf(10_000L + (row / 2) % 50_000, 2));
                            ps.setString(5, "Synthetic transaction " + (row / 2) + " " + UUID.nameUUIDFromBytes(("demo-" + row).getBytes()).toString().substring(0, 8));
                        }
                        public int getBatchSize() { return end - start; }
                    });
                state.set(new ImportState("PROCESSING", end, count, null));
            }
            state.set(new ImportState("COMPLETED", count, count, null));
        } catch (Exception e) {
            state.set(new ImportState("FAILED", state.get().processed(), count, e.getMessage()));
        }
    }

    public record ImportState(String status, int processed, int total, String error) {}
}
