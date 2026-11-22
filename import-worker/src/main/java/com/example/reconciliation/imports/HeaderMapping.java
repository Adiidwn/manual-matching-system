package com.example.reconciliation.imports;

import java.util.Map;

/**
 * Resolved header positions for a delimited file. Column order in the file does
 * not matter: fields are looked up by header name.
 */
public record HeaderMapping(Map<String, Integer> indexes, int columnCount) {
    public HeaderMapping {
        indexes = Map.copyOf(indexes);
    }

    public int indexOf(String column) {
        Integer index = indexes.get(column);
        if (index == null) throw new IllegalArgumentException("Missing column: " + column);
        return index;
    }
}
