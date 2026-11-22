package com.example.reconciliation.imports;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportHeaderAliasServiceTest {
    @Mock
    private ImportHeaderMappingRepository repository;

    @Test
    void loadsAndNormalizesAliases() {
        when(repository.findAll()).thenReturn(List.of(
                new ImportHeaderMapping("SOURCE", " Sumber "),
                new ImportHeaderMapping("external_reference", "No Referensi")));

        assertThat(new ImportHeaderAliasService(repository).aliases())
                .containsEntry("sumber", "source")
                .containsEntry("no referensi", "external_reference");
    }

    @Test
    void rejectsUnknownCanonicalColumn() {
        when(repository.findAll()).thenReturn(List.of(
                new ImportHeaderMapping("branch", "Cabang")));

        assertThatThrownBy(() -> new ImportHeaderAliasService(repository).aliases())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown canonical column");
    }

    @Test
    void rejectsAliasThatConflictsWithACanonicalColumn() {
        when(repository.findAll()).thenReturn(List.of(
                new ImportHeaderMapping("transaction_date", "amount")));

        assertThatThrownBy(() -> new ImportHeaderAliasService(repository).aliases())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflicts with the canonical column");
    }

    @Test
    void rejectsAliasMappedToTwoDifferentColumns() {
        when(repository.findAll()).thenReturn(List.of(
                new ImportHeaderMapping("amount", "Nilai"),
                new ImportHeaderMapping("transaction_date", "Nilai")));

        assertThatThrownBy(() -> new ImportHeaderAliasService(repository).aliases())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maps to both");
    }

    @Test
    void rejectsBlankAlias() {
        when(repository.findAll()).thenReturn(List.of(
                new ImportHeaderMapping("source", " ")));

        assertThatThrownBy(() -> new ImportHeaderAliasService(repository).aliases())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }
}
