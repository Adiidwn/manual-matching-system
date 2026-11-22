package com.example.reconciliation.imports;

import com.example.reconciliation.transaction.TransactionSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class CsvFileParser implements FileParser {
    public static final List<String> CANONICAL_COLUMNS = List.of(
            "source", "external_reference", "transaction_date", "amount", "currency", "description");
    private static final Pattern TRAILER_PATTERN = Pattern.compile("\\d{1,9}");

    @Override
    public boolean supports(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    @Override
    public HeaderMapping validateHeader(String headerLine, Map<String, String> aliases) {
        if (headerLine == null) throw new IllegalArgumentException("CSV file is empty");
        List<String> actual = split(stripBom(headerLine)).stream()
                .map(CsvFileParser::normalizeName)
                .toList();
        Map<String, String> normalizedAliases = normalizeAliases(aliases);
        Map<String, Integer> indexes = new HashMap<>();
        Map<String, Integer> seenHeaders = new HashMap<>();
        for (int i = 0; i < actual.size(); i++) {
            String name = actual.get(i);
            if (seenHeaders.putIfAbsent(name, i) != null) {
                throw new IllegalArgumentException("Duplicate header column: " + name);
            }
            String canonical = CANONICAL_COLUMNS.contains(name) ? name : normalizedAliases.get(name);
            if (canonical == null) continue;
            Integer previous = indexes.putIfAbsent(canonical, i);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate column for " + canonical
                        + " at positions " + previous + " and " + i);
            }
        }
        List<String> missing = CANONICAL_COLUMNS.stream().filter(column -> !indexes.containsKey(column)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Expected header " + CANONICAL_COLUMNS
                    + " but missing " + missing + " in " + actual);
        }
        return new HeaderMapping(indexes, actual.size());
    }

    private static Map<String, String> normalizeAliases(Map<String, String> aliases) {
        Map<String, String> normalized = new HashMap<>();
        if (aliases == null) return normalized;
        aliases.forEach((alias, canonical) -> {
            String key = normalizeName(alias);
            String value = normalizeName(canonical);
            if (key.isEmpty() || value.isEmpty()) return;
            String previous = normalized.putIfAbsent(key, value);
            if (previous != null && !previous.equals(value)) {
                throw new IllegalArgumentException("Header alias '" + key + "' maps to both '"
                        + previous + "' and '" + value + "'");
            }
        });
        return normalized;
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public ParsedRow parseRow(String line, long rowNumber, HeaderMapping header) {
        List<String> columns = split(line);
        if (columns.size() != header.columnCount()) {
            throw new IllegalArgumentException("expected " + header.columnCount()
                    + " columns but found " + columns.size());
        }
        TransactionSource source = parseSource(column(columns, header, "source"));
        String reference = parseReference(column(columns, header, "external_reference"));
        LocalDate date = parseDate(column(columns, header, "transaction_date"));
        BigDecimal amount = parseAmount(column(columns, header, "amount"));
        String currency = parseCurrency(column(columns, header, "currency"));
        String description = parseDescription(column(columns, header, "description"));
        return new ParsedRow(rowNumber, source, reference, date, amount, currency, description);
    }

    private static String column(List<String> columns, HeaderMapping header, String name) {
        return columns.get(header.indexOf(name));
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
