package com.example.reconciliation.imports;

import com.example.reconciliation.transaction.TransactionSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvFileParserTest {
    private static final String HEADER = "source,external_reference,transaction_date,amount,currency,description";
    private final CsvFileParser parser = new CsvFileParser();
    private final HeaderMapping header = parser.validateHeader(HEADER);

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
    void acceptsColumnsInAnyOrder() {
        HeaderMapping reordered = parser.validateHeader(
                "description,amount,currency,source,transaction_date,external_reference");

        ParsedRow row = parser.parseRow(
                "\"Transfer, PT Alpha\",150000.00,IDR,BANK,2025-03-01,INV-1", 3, reordered);

        assertThat(row.source()).isEqualTo(TransactionSource.BANK);
        assertThat(row.externalReference()).isEqualTo("INV-1");
        assertThat(row.transactionDate()).isEqualTo(LocalDate.of(2025, 3, 1));
        assertThat(row.amount()).isEqualByComparingTo(new BigDecimal("150000.00"));
        assertThat(row.currency()).isEqualTo("IDR");
        assertThat(row.description()).isEqualTo("Transfer, PT Alpha");
    }

    @Test
    void acceptsHeaderAliases() {
        HeaderMapping mapped = parser.validateHeader(
                "Sumber,No Referensi,Tanggal,Nominal,Mata Uang,Keterangan",
                Map.of(
                        "Sumber", "source",
                        "No Referensi", "external_reference",
                        "Tanggal", "transaction_date",
                        "Nominal", "amount",
                        "Mata Uang", "currency",
                        "Keterangan", "description"));

        ParsedRow row = parser.parseRow(
                "BANK,INV-7,2025-03-01,250000.00,IDR,Transfer masuk", 2, mapped);

        assertThat(row.source()).isEqualTo(TransactionSource.BANK);
        assertThat(row.externalReference()).isEqualTo("INV-7");
        assertThat(row.transactionDate()).isEqualTo(LocalDate.of(2025, 3, 1));
        assertThat(row.amount()).isEqualByComparingTo(new BigDecimal("250000.00"));
        assertThat(row.currency()).isEqualTo("IDR");
        assertThat(row.description()).isEqualTo("Transfer masuk");
    }

    @Test
    void acceptsMixOfCanonicalNamesAndAliases() {
        HeaderMapping mapped = parser.validateHeader(
                "amount,source,Nominal,Tanggal,currency,description",
                Map.of("Nominal", "transaction_date", "Tanggal", "external_reference"));

        ParsedRow row = parser.parseRow("10.00,BANK,2025-03-01,INV-8,IDR,row", 1, mapped);

        assertThat(row.amount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(row.transactionDate()).isEqualTo(LocalDate.of(2025, 3, 1));
        assertThat(row.externalReference()).isEqualTo("INV-8");
    }

    @Test
    void rejectsTwoHeaderColumnsMappedToTheSameField() {
        assertThatThrownBy(() -> parser.validateHeader(
                "source,external_reference,transaction_date,amount,currency,description,Nominal",
                Map.of("Nominal", "amount")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate column for amount");
    }

    @Test
    void ignoresExtraColumns() {
        HeaderMapping withBranch = parser.validateHeader(HEADER + ",branch");

        ParsedRow row = parser.parseRow("BANK,INV-1,2025-03-01,10.00,IDR,row,JKT", 1, withBranch);

        assertThat(row.externalReference()).isEqualTo("INV-1");
        assertThat(row.description()).isEqualTo("row");
    }

    @Test
    void rejectsHeaderMissingRequiredColumns() {
        assertThatThrownBy(() -> parser.validateHeader("source,amount"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing")
                .hasMessageContaining("external_reference");
    }

    @Test
    void rejectsDuplicateHeaderColumns() {
        assertThatThrownBy(() -> parser.validateHeader(HEADER + ",amount"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate header column: amount");
    }

    @Test
    void parsesValidRowIncludingQuotedComma() {
        ParsedRow row = parser.parseRow(
                "BANK,INV-1,2025-03-01,150000.00,IDR,\"Transfer, PT Alpha\"", 7, header);

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
        ParsedRow row = parser.parseRow("INTERNAL,INV-2,2025-03-02,10.00,IDR,", 1, header);
        assertThat(row.description()).isNull();
    }

    @Test
    void rejectsInvalidRows() {
        assertThatThrownBy(() -> parser.parseRow("BANK,INV-1,05/03/2025,10.00,IDR,x", 1, header))
                .hasMessageContaining("transaction_date");
        assertThatThrownBy(() -> parser.parseRow("BANK,INV-1,2025-03-01,abc,IDR,x", 1, header))
                .hasMessageContaining("amount");
        assertThatThrownBy(() -> parser.parseRow("BANK,INV-1,2025-03-01,-1.00,IDR,x", 1, header))
                .hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> parser.parseRow("BANK,INV-1,2025-03-01,10.00,US,x", 1, header))
                .hasMessageContaining("currency");
        assertThatThrownBy(() -> parser.parseRow("BANK,INV-1,2025-03-01,10.00,IDR", 1, header))
                .hasMessageContaining("expected 6 columns");
        assertThatThrownBy(() -> parser.parseRow("BANK,INV-1,2025-03-01,10.00,IDR,x,extra", 1, header))
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
