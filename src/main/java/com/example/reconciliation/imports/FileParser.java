package com.example.reconciliation.imports;

public interface FileParser {
    boolean supports(String fileName);

    void validateHeader(String headerLine);

    ParsedRow parseRow(String line, long rowNumber);

    /**
     * Returns the declared row count when the line is a trailer row, otherwise null.
     */
    Long parseTrailer(String line);
}
