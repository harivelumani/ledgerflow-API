package com.ledgerflow.api;

import com.ledgerflow.entity.TransactionType;
import com.ledgerflow.exception.DuplicateReferenceException;
import com.ledgerflow.exception.InsufficientFundsException;
import com.ledgerflow.repository.LedgerTransactionRepository;
import com.ledgerflow.repository.TransferRepository;
import com.ledgerflow.service.LedgerService;
import com.ledgerflow.service.TransferService;
import com.ledgerflow.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Test
    void concurrentDebitsCannotOverspend() throws Exception {
        UUID accountId = createAccount("Race");
        postTransaction(accountId, "CREDIT", "100.00", "Seed", "SEED");

        int threads = 8;
        AtomicInteger wins = new AtomicInteger();
        List<Throwable> unexpected = runTogether(threads, index -> {
            try {
                ledgerService.post(accountId, TransactionType.DEBIT, new BigDecimal("30.00"), "Spend", "D-" + index);
                wins.incrementAndGet();
            } catch (InsufficientFundsException exception) {
                // The losers are the point of the test.
            }
        });

        assertThat(unexpected).isEmpty();
        assertThat(wins).hasValue(3);
        assertThat(balanceOf(accountId)).isEqualByComparingTo("10.00");
        assertThat(ledgerTransactionRepository.countByAccountId(accountId)).isEqualTo(4);
    }

    @Test
    void concurrentDuplicateSubmissionsApplyOnce() throws Exception {
        UUID accountId = createAccount("Idem");
        AtomicInteger wins = new AtomicInteger();
        List<Throwable> unexpected = runTogether(10, index -> {
            try {
                ledgerService.post(accountId, TransactionType.CREDIT, new BigDecimal("25.00"), "Once", "SAME-REF");
                wins.incrementAndGet();
            } catch (DuplicateReferenceException | DataIntegrityViolationException exception) {
                // A concurrent loser is rejected by the lock check or the unique constraint.
            }
        });

        assertThat(unexpected).isEmpty();
        assertThat(wins).hasValue(1);
        assertThat(balanceOf(accountId)).isEqualByComparingTo("25.00");
        assertThat(ledgerTransactionRepository.countByAccountId(accountId)).isEqualTo(1);
    }

    @Test
    void concurrentTransfersKeepBalancesNonNegativeAndConserveMoney() throws Exception {
        UUID source = createAccount("Source");
        UUID destination = createAccount("Destination");
        postTransaction(source, "CREDIT", "100.00", "Seed", "SEED-A");
        postTransaction(destination, "CREDIT", "100.00", "Seed", "SEED-B");

        AtomicInteger wins = new AtomicInteger();
        List<Throwable> unexpected = runTogether(8, index -> {
            try {
                transferService.transfer(source, destination, new BigDecimal("40.00"), "AB-" + index, "Move");
                wins.incrementAndGet();
            } catch (InsufficientFundsException exception) {
                // Expected once the source cannot fund another 40.
            }
        });

        assertThat(unexpected).isEmpty();
        assertThat(wins).hasValue(2);
        assertThat(balanceOf(source)).isEqualByComparingTo("20.00");
        assertThat(balanceOf(destination)).isEqualByComparingTo("180.00");
        assertThat(balanceOf(source).add(balanceOf(destination))).isEqualByComparingTo("200.00");
    }

    @Test
    void opposingTransfersDoNotDeadlockOrGoNegative() throws Exception {
        UUID first = createAccount("First");
        UUID second = createAccount("Second");
        postTransaction(first, "CREDIT", "100.00", "Seed", "SEED-A");
        postTransaction(second, "CREDIT", "100.00", "Seed", "SEED-B");

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) {
                int index = i;
                futures.add(executor.submit(attempt(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    try {
                        transferService.transfer(first, second, new BigDecimal("20.00"), "AB-" + index, "A to B");
                    } catch (InsufficientFundsException exception) {
                        // Allowed when one side is temporarily short.
                    }
                })));
                futures.add(executor.submit(attempt(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    try {
                        transferService.transfer(second, first, new BigDecimal("20.00"), "BA-" + index, "B to A");
                    } catch (InsufficientFundsException exception) {
                        // Allowed when one side is temporarily short.
                    }
                })));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        BigDecimal left = balanceOf(first);
        BigDecimal right = balanceOf(second);
        assertThat(left).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(right).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(left.add(right)).isEqualByComparingTo("200.00");
        assertThat(transferRepository.count()).isLessThanOrEqualTo(10);
    }

    @Test
    void concurrentDuplicateTransfersApplyOnce() throws Exception {
        UUID source = createAccount("Source");
        UUID destination = createAccount("Destination");
        postTransaction(source, "CREDIT", "50.00", "Seed", "SEED");
        AtomicInteger wins = new AtomicInteger();
        List<Throwable> unexpected = runTogether(8, index -> {
            try {
                transferService.transfer(source, destination, new BigDecimal("10.00"), "TRANSFER-ONCE", "Once");
                wins.incrementAndGet();
            } catch (DuplicateReferenceException | DataIntegrityViolationException exception) {
                // Replay or unique-constraint loser.
            }
        });

        assertThat(unexpected).isEmpty();
        assertThat(wins).hasValue(1);
        assertThat(balanceOf(source)).isEqualByComparingTo("40.00");
        assertThat(balanceOf(destination)).isEqualByComparingTo("10.00");
        assertThat(transferRepository.count()).isEqualTo(1);
    }

    private List<Throwable> runTogether(int threads, Attempt attempt) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> unexpected = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        if (!start.await(5, TimeUnit.SECONDS)) {
                            unexpected.add(new IllegalStateException("start latch timed out"));
                            return;
                        }
                        attempt.run(index);
                    } catch (RuntimeException exception) {
                        unexpected.add(exception);
                    } catch (Exception exception) {
                        unexpected.add(exception);
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        return unexpected;
    }

    private Runnable attempt(Interruptible interruptible) {
        return () -> {
            try {
                interruptible.run();
            } catch (Exception exception) {
                if (exception instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException(exception);
            }
        };
    }

    @FunctionalInterface
    private interface Attempt {
        void run(int index) throws Exception;
    }

    @FunctionalInterface
    private interface Interruptible {
        void run() throws Exception;
    }
}
