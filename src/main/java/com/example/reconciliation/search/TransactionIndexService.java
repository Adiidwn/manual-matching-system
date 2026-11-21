package com.example.reconciliation.search;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class TransactionIndexService {
    private static final int PAGE_SIZE = 2_000;
    private final JdbcTemplate jdbc;
    private final TransactionDocumentRepository documents;
    private final AtomicReference<IndexState> state = new AtomicReference<>(new IndexState("IDLE", 0, 0, null));

    public TransactionIndexService(JdbcTemplate jdbc, TransactionDocumentRepository documents) {
        this.jdbc = jdbc;
        this.documents = documents;
    }

    public synchronized IndexState startReindex() {
        String current = state.get().status();
        if ("QUEUED".equals(current) || "PROCESSING".equals(current))
            throw new IllegalStateException("A reindex job is already running");
        int total = jdbc.queryForObject("SELECT count(*) FROM transactions", Integer.class);
        state.set(new IndexState("QUEUED", 0, total, null));
        CompletableFuture.runAsync(this::reindex);
        return state.get();
    }

    public IndexState getState() { return state.get(); }

    private void reindex() {
        int total = state.get().total();
        state.set(new IndexState("PROCESSING", 0, total, null));
        long lastId = 0;
        int indexed = 0;
        try {
            while (true) {
                long afterId = lastId;
                List<TransactionDocument> batch = jdbc.query("""
                    SELECT id, source, external_reference, transaction_date, amount, currency, description
                    FROM transactions WHERE id > ? ORDER BY id LIMIT ?
                    """, (rs, row) -> new TransactionDocument(
                        Long.toString(rs.getLong("id")), rs.getString("source"), rs.getString("external_reference"),
                        rs.getDate("transaction_date").toLocalDate(), rs.getBigDecimal("amount"),
                        rs.getString("currency"), rs.getString("description")), afterId, PAGE_SIZE);
                if (batch.isEmpty()) break;
                documents.saveAll(batch);
                lastId = Long.parseLong(batch.get(batch.size() - 1).getId());
                indexed += batch.size();
                state.set(new IndexState("PROCESSING", indexed, total, null));
            }
            state.set(new IndexState("COMPLETED", indexed, total, null));
        } catch (Exception e) {
            state.set(new IndexState("FAILED", indexed, total, e.getMessage()));
        }
    }

    public record IndexState(String status, int indexed, int total, String error) {}
}
