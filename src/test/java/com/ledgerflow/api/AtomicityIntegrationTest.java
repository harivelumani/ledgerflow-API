package com.ledgerflow.api;

import com.ledgerflow.entity.Account;
import com.ledgerflow.entity.LedgerTransaction;
import com.ledgerflow.entity.TransactionType;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.LedgerTransactionRepository;
import com.ledgerflow.repository.TransferRepository;
import com.ledgerflow.service.LedgerService;
import com.ledgerflow.service.LedgerWriteHook;
import com.ledgerflow.service.TransferService;
import com.ledgerflow.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

class AtomicityIntegrationTest extends IntegrationTestSupport {

    @MockitoBean
    private LedgerWriteHook ledgerWriteHook;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void allowLedgerInserts() {
        doNothing().when(ledgerWriteHook).beforeLedgerInsert();
    }

    @Test
    void balanceUpdateRollsBackWhenLedgerInsertFails() throws Exception {
        UUID accountId = createAccount("Rollback");
        postTransaction(accountId, "CREDIT", "50.00", "Seed", "SEED");
        doThrow(new IllegalStateException("simulated ledger insert failure"))
                .when(ledgerWriteHook).beforeLedgerInsert();

        assertThatThrownBy(() -> ledgerService.post(
                accountId, TransactionType.DEBIT, new BigDecimal("20.00"), "Should vanish", "FAIL-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated ledger insert failure");

        assertThat(balanceOf(accountId)).isEqualByComparingTo("50.00");
        assertThat(ledgerTransactionRepository.countByAccountId(accountId)).isEqualTo(1);
        assertThat(ledgerTransactionRepository.findAll())
                .noneMatch(transaction -> "FAIL-1".equals(transaction.getReferenceId()));
    }

    @Test
    void constraintFailureRollsBackTheBalanceUpdateAndTheEarlierInsert() throws Exception {
        UUID accountId = createAccount("Constraint");
        postTransaction(accountId, "CREDIT", "50.00", "Seed", "SEED");
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> template.executeWithoutResult(status -> {
            Account account = accountRepository.findByIdForUpdate(accountId).orElseThrow();
            account.credit(new BigDecimal("25.00"));
            accountRepository.saveAndFlush(account);
            ledgerTransactionRepository.saveAndFlush(LedgerTransaction.record(
                    accountId, TransactionType.CREDIT, new BigDecimal("10.00"), "First", "DUP", clock.instant(), null));
            ledgerTransactionRepository.saveAndFlush(LedgerTransaction.record(
                    accountId, TransactionType.CREDIT, new BigDecimal("5.00"), "Second", "DUP", clock.instant(), null));
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(balanceOf(accountId)).isEqualByComparingTo("50.00");
        assertThat(ledgerTransactionRepository.countByAccountId(accountId)).isEqualTo(1);
    }

    @Test
    void transferRollsBackBothBalancesWhenLedgerInsertFails() throws Exception {
        UUID source = createAccount("Source");
        UUID destination = createAccount("Destination");
        postTransaction(source, "CREDIT", "100.00", "Seed", "SEED");
        doThrow(new IllegalStateException("simulated transfer ledger failure"))
                .when(ledgerWriteHook).beforeLedgerInsert();

        assertThatThrownBy(() -> transferService.transfer(
                source, destination, new BigDecimal("30.00"), "TRANSFER-FAIL", "Should vanish"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(balanceOf(source)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(destination)).isEqualByComparingTo("0.00");
        assertThat(transferRepository.count()).isZero();
        assertThat(ledgerTransactionRepository.count()).isEqualTo(1);
    }
}
