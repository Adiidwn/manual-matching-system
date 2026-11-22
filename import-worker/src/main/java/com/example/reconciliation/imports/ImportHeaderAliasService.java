package com.example.reconciliation.imports;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads the manually maintained header aliases from the database.
 */
@Service
public class ImportHeaderAliasService {
    private final ImportHeaderMappingRepository repository;

    public ImportHeaderAliasService(ImportHeaderMappingRepository repository) {
        this.repository = repository;
    }

    public Map<String, String> aliases() {
        Map<String, String> aliases = new HashMap<>();
        for (ImportHeaderMapping mapping : repository.findAll()) {
            String canonical = normalize(mapping.getCanonicalColumn());
            String alias = normalize(mapping.getHeaderName());
            if (!CsvFileParser.CANONICAL_COLUMNS.contains(canonical)) {
                throw new IllegalArgumentException("Unknown canonical column '" + canonical
                        + "' for header alias '" + alias + "'");
            }
            if (alias.isEmpty()) {
                throw new IllegalArgumentException("Header alias for " + canonical + " must not be blank");
            }
            if (CsvFileParser.CANONICAL_COLUMNS.contains(alias) && !alias.equals(canonical)) {
                throw new IllegalArgumentException("Header alias '" + alias
                        + "' conflicts with the canonical column of the same name");
            }
            String previous = aliases.putIfAbsent(alias, canonical);
            if (previous != null && !previous.equals(canonical)) {
                throw new IllegalArgumentException("Header alias '" + alias + "' maps to both '"
                        + previous + "' and '" + canonical + "'");
            }
        }
        return Map.copyOf(aliases);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
