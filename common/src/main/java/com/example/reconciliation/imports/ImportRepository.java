package com.example.reconciliation.imports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImportRepository extends JpaRepository<ImportBatch, Long> {
    Optional<ImportBatch> findByFileHash(String fileHash);
    List<ImportBatch> findTop50ByOrderByIdDesc();
}
