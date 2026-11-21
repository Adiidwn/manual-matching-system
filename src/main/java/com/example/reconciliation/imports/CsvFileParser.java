package com.example.reconciliation.imports;

import com.example.reconciliation.transaction.TransactionSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class CsvFileParser implements FileParser {
    private static final List<String> HEADER = List.of(
            "source", "external_reference", "transaction_date", "amount", "currency", "description");
    private static final Pattern TRAILER_PATTERN = Pattern.compile("\\d{1,9}");

    @Override
    public boolean supports(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    @Override
    public void validateHeader(String headerLine) {
        if (headerLine == null) throw new IllegalArgumentException("CSV file is empty");
        List<String> actual = split(stripBom(headerLine)).stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .toList();
        if (!actual.equals(HEADER)) {
            throw new IllegalArgumentException("Expected header " + HEADER + " but found " + actual);
        }
    }

    @Override
    public ParsedRow parseRow(String line, long rowNumber) {
        List<String> columns = split(line);
        if (columns.size() != HEADER.size()) {
            throw new IllegalArgumentException("expected " + HEADER.size() + " columns but found " + columns.size());
        }
        TransactionSource source = parseSource(columns.get(0));
        String reference = parseReference(columns.get(1));
        LocalDate date = parseDate(columns.get(2));
        BigDecimal amount = parseAmount(columns.get(3));
        String currency = parseCurrency(columns.get(4));
        String description = parseDescription(columns.get(5));
        return new ParsedRow(rowNumber, source, reference, date, amount, currency, description);
    }

    @Override
    public Long parseTrailer(String line) {
        List<String> columns = split(stripBom(line));
        if (columns.size() != 1) return null;
        String candidate = columns.get(0).trim();
        if (!TRAILER_PATTERN.matcher(candidate).matches()) return null;
        return Long.parseLong(candidate);
    }

    private static TransactionSource parseSource(String value) {
        try {
            return TransactionSource.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("source must be BANK or INTERNAL");
        }
    }

    private static String parseReference(String value) {
        String reference = value.trim();
        if (reference.isEmpty() || reference.length() > 100) {
            throw new IllegalArgumentException("external_reference must contain 1..100 characters");
        }
        return reference;
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("transaction_date must use yyyy-MM-dd: " + value.trim());
        }
    }

    private static BigDecimal parseAmount(String value) {
        try {
            BigDecimal amount = new BigDecimal(value.trim()).setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be greater than zero");
            if (amount.precision() - amount.scale() > 17) throw new IllegalArgumentException("amount is too large");
            return amount;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("amount must be a number: " + value.trim());
        }
    }

    private static String parseCurrency(String value) {
        String currency = value.trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency must be a 3-letter code");
        return currency;
    }

    private static String parseDescription(String value) {
        String description = value.trim();
        if (description.isEmpty()) return null;
        if (description.length() > 500) throw new IllegalArgumentException("description must be at most 500 characters");
        return description;
    }

    private static String stripBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private static List<String> split(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        columns.add(current.toString());
        return columns;
    }
}
