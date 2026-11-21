package com.example.reconciliation.transaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

@ExtendWith(MockitoExtension.class)
class TransactionQueueServiceTest {
    @Mock
    private TransactionRepository repository;
    @InjectMocks
    private TransactionQueueService service;

    @Test
    void rejectsInvalidPagination() {
        assertBadRequest(() -> service.getQueue(null, null, null, null, null, null, null, null, -1, 50));
        assertBadRequest(() -> service.getQueue(null, null, null, null, null, null, null, null, 0, 0));
        assertBadRequest(() -> service.getQueue(null, null, null, null, null, null, null, null, 0, 101));
    }

    @Test
    void rejectsInvalidAmountRange() {
        assertBadRequest(() -> service.getQueue(null, null, null, null, new BigDecimal("-1"), null, null, null, 0, 50));
        assertBadRequest(() -> service.getQueue(null, null, null, null, null, new BigDecimal("-1"), null, null, 0, 50));
        assertBadRequest(() -> service.getQueue(null, null, null, null, new BigDecimal("200"), new BigDecimal("100"), null, null, 0, 50));
    }

    @Test
    void rejectsTooLongReference() {
        String reference = "x".repeat(101);
        assertBadRequest(() -> service.getQueue(null, null, null, reference, null, null, null, null, 0, 50));
    }

    @Test
    void normalizesAndDelegatesFilters() {
        when(repository.findQueue(any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getQueue("UNMATCHED", "BANK", 1L, " INV-1 ",
                new BigDecimal("100.00"), new BigDecimal("200.00"),
                "2025-01-01", "2025-01-31", 0, 50);

        verify(repository).findQueue(
                eq("UNMATCHED"), eq("BANK"), eq(1L), eq("INV-1"),
                eq(new BigDecimal("100.00")), eq(new BigDecimal("200.00")),
                eq("2025-01-01"), eq("2025-01-31"), any(Pageable.class));
    }

    private void assertBadRequest(ThrowingCallable call) {
        ResponseStatusException exception = catchThrowableOfType(call, ResponseStatusException.class);
        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
