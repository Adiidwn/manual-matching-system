package com.example.reconciliation.report;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReconciliationReportControllerTest {
    @Test
    void mapsSummaryColumnsToStatusShare() throws Exception {
        ResultSet rs = summaryRow();
        AtomicReference<SqlParameterSource> captured = new AtomicReference<>();

        NamedParameterJdbcTemplate jdbc = jdbc(captured, rs);

        List<ReconciliationReportController.StatusShare> result = new ReconciliationReportController(jdbc)
                .reconciliationSummary(null, null, null);

        assertThat(result).containsExactly(new ReconciliationReportController.StatusShare(
                "BANK", "UNMATCHED", 18L, new BigDecimal("1971000.00"),
                30L, new BigDecimal("9471042.00"),
                new BigDecimal("60.00"), new BigDecimal("37.50")));
        assertThat(captured.get().getValue("fromDate")).isNull();
        assertThat(captured.get().getValue("toDate")).isNull();
        assertThat(captured.get().getValue("reference")).isNull();
    }

    @Test
    void passesDateAndReferenceFiltersAsParameters() throws Exception {
        AtomicReference<SqlParameterSource> captured = new AtomicReference<>();

        new ReconciliationReportController(jdbc(captured, summaryRow()))
                .reconciliationSummary(LocalDate.parse("2025-03-01"), LocalDate.parse("2025-03-31"), " INV-1001 ");

        assertThat(captured.get().getValue("fromDate")).isEqualTo("2025-03-01");
        assertThat(captured.get().getValue("toDate")).isEqualTo("2025-03-31");
        assertThat(captured.get().getValue("reference")).isEqualTo("INV-1001");
    }

    @Test
    void treatsBlankReferenceAsNoFilter() throws Exception {
        AtomicReference<SqlParameterSource> captured = new AtomicReference<>();

        new ReconciliationReportController(jdbc(captured, summaryRow()))
                .reconciliationSummary(null, null, "   ");

        assertThat(captured.get().getValue("reference")).isNull();
    }

    @Test
    void rejectsInvertedDateRange() {
        ReconciliationReportController controller = new ReconciliationReportController(jdbc(new AtomicReference<>(), null));

        assertThatThrownBy(() -> controller.reconciliationSummary(
                LocalDate.parse("2025-03-31"), LocalDate.parse("2025-03-01"), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsOversizedReference() {
        ReconciliationReportController controller = new ReconciliationReportController(jdbc(new AtomicReference<>(), null));

        assertThatThrownBy(() -> controller.reconciliationSummary(null, null, "x".repeat(101)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static ResultSet summaryRow() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("source")).thenReturn("BANK");
        when(rs.getString("status")).thenReturn("UNMATCHED");
        when(rs.getLong("row_count")).thenReturn(18L);
        when(rs.getBigDecimal("amount")).thenReturn(new BigDecimal("1971000.00"));
        when(rs.getLong("total_rows")).thenReturn(30L);
        when(rs.getBigDecimal("total_amount")).thenReturn(new BigDecimal("9471042.00"));
        when(rs.getBigDecimal("pct_of_source")).thenReturn(new BigDecimal("60.00"));
        when(rs.getBigDecimal("pct_of_all")).thenReturn(new BigDecimal("37.50"));
        return rs;
    }

    private static NamedParameterJdbcTemplate jdbc(AtomicReference<SqlParameterSource> captured, ResultSet rs) {
        return new NamedParameterJdbcTemplate(new JdbcTemplate()) {
            @Override
            public <T> List<T> query(String sql, SqlParameterSource params, RowMapper<T> rowMapper) {
                captured.set(params);
                if (rs == null) {
                    return List.of();
                }
                try {
                    return List.of(rowMapper.mapRow(rs, 0));
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }
}
