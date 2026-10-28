package com.ledgerflow.api;

import com.ledgerflow.entity.TransactionType;
import com.ledgerflow.exception.InsufficientFundsException;
import com.ledgerflow.service.LedgerService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises Flyway, InnoDB constraints, and row locks on MySQL 8.
 * Excluded from the default build. Run with {@code mvn test -Pmysql} when Docker is available.
 */
@Tag("mysql")
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MySqlLedgerIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void mysqlSchemaConstraintsAndConcurrentDebitsHold() throws Exception {
        Integer uniqueKeys = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND CONSTRAINT_NAME IN ('uk_txn_account_reference', 'uk_transfers_reference')
                """, Integer.class);
        assertThat(uniqueKeys).isEqualTo(2);

        String body = mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"MySQL\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID accountId = UUID.fromString(body.replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1"));
        ledgerService.post(accountId, TransactionType.CREDIT, new BigDecimal("100.00"), "Seed", "SEED");

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                int index = i;
                executor.submit(() -> {
                    try {
                        start.await(5, TimeUnit.SECONDS);
                        ledgerService.post(accountId, TransactionType.DEBIT, new BigDecimal("30.00"), "Spend", "D-" + index);
                        wins.incrementAndGet();
                    } catch (InsufficientFundsException exception) {
                        // Expected for the requests that arrive after the balance is exhausted.
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                });
            }
            start.countDown();
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, accountId.toString());
        Integer debits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_transactions WHERE account_id = ? AND type = 'DEBIT'",
                Integer.class, accountId.toString());
        assertThat(wins).hasValue(3);
        assertThat(balance).isEqualByComparingTo("10.00");
        assertThat(debits).isEqualTo(3);
    }
}
