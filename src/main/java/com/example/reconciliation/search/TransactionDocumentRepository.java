package com.example.reconciliation.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface TransactionDocumentRepository extends ElasticsearchRepository<TransactionDocument, String> {
    @Query("{\"multi_match\":{\"query\":\"?0\",\"fields\":[\"description\",\"externalReference\"]}}")
    Page<TransactionDocument> searchText(String query, Pageable pageable);
}
