package com.example.reconciliation.transaction;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateMatchingServiceTest {
    @Mock
    private TransactionRepository repository;
    @InjectMocks
    private CandidateMatchingService service;

    @Test
    void rejectsToleranceOutsideRange() {
        assertStatus(() -> service.find(1L, -1, 10), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.find(1L, 31, 10), HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsLimitOutsideRange() {
        assertStatus(() -> service.find(1L, 3, 0), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.find(1L, 3, 51), HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsMissingTarget() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertStatus(() -> service.find(1L, 3, 10), HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsTargetThatIsNotUnmatched() {
        TransactionRecord target = mock(TransactionRecord.class);
        when(target.getStatus()).thenReturn(TransactionStatus.PENDING_APPROVAL);
        when(repository.findById(1L)).thenReturn(Optional.of(target));

        assertStatus(() -> service.find(1L, 3, 10), HttpStatus.CONFLICT);
    }

    @Test
    void mapsCandidatesFromOppositeSource() {
        TransactionRecord target = mock(TransactionRecord.class);
        when(target.getStatus()).thenReturn(TransactionStatus.UNMATCHED);
        when(target.getSource()).thenReturn(TransactionSource.BANK);
        when(target.getAmount()).thenReturn(new BigDecimal("150000.00"));
        when(target.getCurrency()).thenReturn("IDR");
        when(target.getTransactionDate()).thenReturn(LocalDate.of(2025, 3, 1));
        when(repository.findById(10L)).thenReturn(Optional.of(target));

        TransactionQueueRow candidate = mock(TransactionQueueRow.class);
        when(candidate.getId()).thenReturn(11L);
        when(candidate.getSource()).thenReturn("INTERNAL");
        when(candidate.getExternalReference()).thenReturn("INV-1");
        when(candidate.getTransactionDate()).thenReturn(LocalDate.of(2025, 3, 2));
        when(candidate.getAmount()).thenReturn(new BigDecimal("150000.00"));
        when(candidate.getCurrency()).thenReturn("IDR");
        when(candidate.getDescription()).thenReturn("Transfer to PT Alpha");
        when(candidate.getStatus()).thenReturn("UNMATCHED");

        when(repository.findCandidates(
                "BANK", new BigDecimal("150000.00"), "IDR",
                "2025-02-26", "2025-03-04", "2025-03-01", 10))
                .thenReturn(List.of(candidate));

        List<CandidateView> result = service.find(10L, 3, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(11L);
        assertThat(result.get(0).source()).isEqualTo("INTERNAL");
        assertThat(result.get(0).externalReference()).isEqualTo("INV-1");
        verify(repository).findCandidates(
                "BANK", new BigDecimal("150000.00"), "IDR",
                "2025-02-26", "2025-03-04", "2025-03-01", 10);
    }

    private void assertStatus(ThrowingCallable call, HttpStatus expected) {
        ResponseStatusException exception = catchThrowableOfType(call, ResponseStatusException.class);
        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(expected);
    }
}
