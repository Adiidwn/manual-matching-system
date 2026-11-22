package com.example.reconciliation.imports;

import java.util.Map;

public interface FileParser {
    boolean supports(String fileName);

    /**
     * Validates the header and returns the column mapping used by {@link #parseRow}.
     * Aliases map a file header name to one of the canonical columns.
     */
    HeaderMapping validateHeader(String headerLine, Map<String, String> aliases);

    default HeaderMapping validateHeader(String headerLine) {
        return validateHeader(headerLine, Map.of());
    }

    ParsedRow parseRow(String line, long rowNumber, HeaderMapping header);

    /**
     * Returns the declared row count when the line is a trailer row, otherwise null.
     */
    Long parseTrailer(String line);
}
