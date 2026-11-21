package com.example.reconciliation.imports;

import com.example.reconciliation.transaction.TransactionSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvFileParserTest {
    private static final String HEADER = "source,external_reference,transaction_date,amount,currency,description";
    private final CsvFileParser parser = new CsvFileParser();

    @Test
    void supportsCsvExtensionCaseInsensitively() {
        assertThat(parser.supports("transactions.CSV")).isTrue();
        assertThat(parser.supports("transactions.txt")).isFalse();
    }

    @Test
    void validatesHeaderIgnoringCaseAndBom() {
        assertThatCode(() -> parser.validateHeader("\uFEFF" + HEADER.toUpperCase()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWrongHeader() {
        assertThatThrownBy(() -> parser.validateHeader("source,amount"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected header");
    }

    @Test
    void parsesValidRowIncludingQuotedComma() {
        ParsedRow row = parser.parseRow(
                "BANK,INV-1,2025-03-01,150000.00,IDR,\"Transfer, PT Alpha\"", 7);

        assertThat(row.rowNumber()).isEqualTo(7);
        assertThat(row.source()).isEqualTo(TransactionSource.BANK);
        assertThat(row.externalReference()).isEqualTo("INV-1");
        assertThat(row.transactionDate()).isEqualTo(LocalDate.of(2025, 3, 1));
        assertThat(row.amount()).isEqualByComparingTo(new BigDecimal("150000.00"));
        assertThat(row.currency()).isEqualTo("IDR");
        assertThat(row.description()).isEqualTo("Transfer, PT Alpha");
    }

    @Test
    void acceptsEmptyDescription() {
        ParsedRow row = parser.parseRow("INTERNAL,INV-2,2025-03-02,10.00,IDR,", 1);
        assertThat(row.description()).isNull();
    }

    @Test
    void rejectsInvalidRows() {
        assertThatThrownBy(() -> parser.parseRow("BANK,INV-1,05/03/2025,10.00,IDR,x", 1))
                .hasMessageContaining("transaction_date");
        assertThatThrownBy(() -> parser.parseRow("BANK,INV-1,2025-03-01,abc,IDR,x", 1))
                .hasMessageContaining("amount");
        assertThatThrownBy(() -> parser.parseRow("BANK,INV-1,2025-03-01,-1.00,IDR,x", 1))
                .hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> parser.parseRow("BANK,INV-1,2025-03-01,10.00,US,x", 1))
                .hasMessageContaining("currency");
        assertThatThrownBy(() -> parser.parseRow("BANK,INV-1,2025-03-01,10.00,IDR", 1))
                .hasMessageContaining("expected 6 columns");
    }

    @Test
    void parsesTrailerWithLeadingZero() {
        assertThat(parser.parseTrailer("09")).isEqualTo(9L);
    }

    @Test
    void ignoresLinesThatAreNotTrailers() {
        assertThat(parser.parseTrailer("BANK,INV-1,2025-03-01,10.00,IDR,x")).isNull();
        assertThat(parser.parseTrailer("09,extra")).isNull();
        assertThat(parser.parseTrailer("not-a-number")).isNull();
    }
}
