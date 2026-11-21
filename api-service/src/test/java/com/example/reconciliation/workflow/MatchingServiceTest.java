package com.example.reconciliation.workflow;

import com.example.reconciliation.transaction.TransactionRecord;
import com.example.reconciliation.transaction.TransactionRepository;
import com.example.reconciliation.transaction.TransactionSource;
import com.example.reconciliation.transaction.TransactionStatus;
import com.example.reconciliation.workflow.MatchDtos.MatchView;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static com.example.reconciliation.workflow.MatchDtos.CreateRequest;
import static com.example.reconciliation.workflow.MatchDtos.DecisionRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchingServiceTest {
    @Mock
    private MatchRepository matches;
    @Mock
    private AuditRepository audits;
    @Mock
    private TransactionRepository transactions;
    @InjectMocks
    private MatchingService service;

    @BeforeEach
    void setUp() {
        when(matches.save(any(MatchRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void makerCreatesSubmittedMatch() {
        TransactionRecord bank = transaction(10L, TransactionSource.BANK, "100.00", TransactionStatus.UNMATCHED);
        TransactionRecord internal = transaction(11L, TransactionSource.INTERNAL, "100.00", TransactionStatus.UNMATCHED);
        when(transactions.findLockedById(10L)).thenReturn(Optional.of(bank));
        when(transactions.findLockedById(11L)).thenReturn(Optional.of(internal));

        MatchView view = service.create(new CreateRequest(10L, 11L), "maker-1");

        assertThat(view.status()).isEqualTo(MatchStatus.SUBMITTED);
        assertThat(view.maker()).isEqualTo("maker-1");
        verify(transactions).updateStatus(10L, "PENDING_APPROVAL");
        verify(transactions).updateStatus(11L, "PENDING_APPROVAL");
        verify(audits).save(any(AuditEvent.class));
    }

    @Test
    void createRequiresOneBankAndOneInternal() {
        TransactionRecord bank = transaction(10L, TransactionSource.INTERNAL, "100.00", TransactionStatus.UNMATCHED);
        TransactionRecord internal = transaction(11L, TransactionSource.INTERNAL, "100.00", TransactionStatus.UNMATCHED);
        when(transactions.findLockedById(10L)).thenReturn(Optional.of(bank));
        when(transactions.findLockedById(11L)).thenReturn(Optional.of(internal));

        assertStatus(() -> service.create(new CreateRequest(10L, 11L), "maker-1"), HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRequiresBothTransactionsUnmatched() {
        TransactionRecord bank = transaction(10L, TransactionSource.BANK, "100.00", TransactionStatus.PENDING_APPROVAL);
        TransactionRecord internal = transaction(11L, TransactionSource.INTERNAL, "100.00", TransactionStatus.UNMATCHED);
        when(transactions.findLockedById(10L)).thenReturn(Optional.of(bank));
        when(transactions.findLockedById(11L)).thenReturn(Optional.of(internal));

        assertStatus(() -> service.create(new CreateRequest(10L, 11L), "maker-1"), HttpStatus.CONFLICT);
    }

    @Test
    void createRequiresSameAmountAndCurrency() {
        TransactionRecord bank = transaction(10L, TransactionSource.BANK, "100.00", TransactionStatus.UNMATCHED);
        TransactionRecord internal = transaction(11L, TransactionSource.INTERNAL, "101.00", TransactionStatus.UNMATCHED);
        when(transactions.findLockedById(10L)).thenReturn(Optional.of(bank));
        when(transactions.findLockedById(11L)).thenReturn(Optional.of(internal));

        assertStatus(() -> service.create(new CreateRequest(10L, 11L), "maker-1"), HttpStatus.BAD_REQUEST);
    }

    @Test
    void checkerApprovesSubmittedMatch() {
        MatchRecord match = new MatchRecord(10L, 11L, "maker-1");
        when(matches.findLockedById(5L)).thenReturn(Optional.of(match));

        MatchView view = service.checkerDecision(5L, "checker-1", new DecisionRequest(true, null));

        assertThat(view.status()).isEqualTo(MatchStatus.CHECKED);
        assertThat(view.checker()).isEqualTo("checker-1");
        verify(audits).save(any(AuditEvent.class));
    }

    @Test
    void checkerCannotBeMaker() {
        MatchRecord match = new MatchRecord(10L, 11L, "maker-1");
        when(matches.findLockedById(5L)).thenReturn(Optional.of(match));

        assertStatus(() -> service.checkerDecision(5L, "maker-1", new DecisionRequest(true, null)),
                HttpStatus.FORBIDDEN);
    }

    @Test
    void checkerCannotDecideTwice() {
        MatchRecord match = new MatchRecord(10L, 11L, "maker-1");
        match.checkedBy("checker-1");
        when(matches.findLockedById(5L)).thenReturn(Optional.of(match));

        assertStatus(() -> service.checkerDecision(5L, "checker-2", new DecisionRequest(true, null)),
                HttpStatus.CONFLICT);
    }

    @Test
    void signerApprovesCheckedMatchAndReconcilesTransactions() {
        MatchRecord match = new MatchRecord(10L, 11L, "maker-1");
        match.checkedBy("checker-1");
        when(matches.findLockedById(5L)).thenReturn(Optional.of(match));

        MatchView view = service.signerDecision(5L, "signer-1", new DecisionRequest(true, null));

        assertThat(view.status()).isEqualTo(MatchStatus.SIGNED);
        assertThat(view.signer()).isEqualTo("signer-1");
        verify(transactions).updateStatus(10L, "RECONCILED");
        verify(transactions).updateStatus(11L, "RECONCILED");
    }

    @Test
    void signerMustDifferFromMakerAndChecker() {
        MatchRecord match = new MatchRecord(10L, 11L, "maker-1");
        match.checkedBy("checker-1");
        when(matches.findLockedById(5L)).thenReturn(Optional.of(match));

        assertStatus(() -> service.signerDecision(5L, "maker-1", new DecisionRequest(true, null)),
                HttpStatus.FORBIDDEN);
        assertStatus(() -> service.signerDecision(5L, "checker-1", new DecisionRequest(true, null)),
                HttpStatus.FORBIDDEN);
    }

    @Test
    void signerRequiresCheckedStatus() {
        MatchRecord match = new MatchRecord(10L, 11L, "maker-1");
        when(matches.findLockedById(5L)).thenReturn(Optional.of(match));

        assertStatus(() -> service.signerDecision(5L, "signer-1", new DecisionRequest(true, null)),
                HttpStatus.CONFLICT);
    }

    @Test
    void rejectionRequiresReason() {
        MatchRecord match = new MatchRecord(10L, 11L, "maker-1");
        when(matches.findLockedById(5L)).thenReturn(Optional.of(match));

        assertStatus(() -> service.checkerDecision(5L, "checker-1", new DecisionRequest(false, " ")),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void checkerRejectionReleasesTransactions() {
        MatchRecord match = new MatchRecord(10L, 11L, "maker-1");
        when(matches.findLockedById(5L)).thenReturn(Optional.of(match));

        MatchView view = service.checkerDecision(5L, "checker-1",
                new DecisionRequest(false, "Nominal tidak sesuai"));

        assertThat(view.status()).isEqualTo(MatchStatus.REJECTED);
        assertThat(view.rejectionReason()).isEqualTo("Nominal tidak sesuai");
        verify(transactions).updateStatus(10L, "UNMATCHED");
        verify(transactions).updateStatus(11L, "UNMATCHED");
    }

    @Test
    void signerRejectionReleasesTransactions() {
        MatchRecord match = new MatchRecord(10L, 11L, "maker-1");
        match.checkedBy("checker-1");
        when(matches.findLockedById(5L)).thenReturn(Optional.of(match));

        MatchView view = service.signerDecision(5L, "signer-1",
                new DecisionRequest(false, "Dokumen belum lengkap"));

        assertThat(view.status()).isEqualTo(MatchStatus.REJECTED);
        verify(transactions).updateStatus(10L, "UNMATCHED");
        verify(transactions).updateStatus(11L, "UNMATCHED");
    }

    private TransactionRecord transaction(Long id, TransactionSource source, String amount,
                                          TransactionStatus status) {
        TransactionRecord record = mock(TransactionRecord.class);
        lenient().when(record.getId()).thenReturn(id);
        lenient().when(record.getSource()).thenReturn(source);
        lenient().when(record.getAmount()).thenReturn(new BigDecimal(amount));
        lenient().when(record.getCurrency()).thenReturn("IDR");
        lenient().when(record.getStatus()).thenReturn(status);
        return record;
    }

    private void assertStatus(ThrowingCallable call, HttpStatus expected) {
        ResponseStatusException exception = catchThrowableOfType(call, ResponseStatusException.class);
        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(expected);
    }
}
