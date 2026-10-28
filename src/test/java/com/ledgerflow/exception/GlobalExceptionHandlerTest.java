package com.ledgerflow.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
            Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void knownConstraintNamesMapToClientErrorsWithoutSqlText() {
        var request = new MockHttpServletRequest("POST", "/api/accounts/x/transactions");
        String sql = "insert into ledger_transactions values ('secret-row') constraint uk_txn_account_reference";

        var duplicate = handler.handleIntegrity(violation(sql), request);

        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
        assertThat(duplicate.getBody().code()).isEqualTo("DUPLICATE_REFERENCE");
        assertThat(duplicate.getBody().message()).isEqualTo("Transaction reference already exists");
        assertThat(duplicate.getBody().message()).doesNotContain("secret-row");
        assertThat(duplicate.getBody().details()).noneMatch(detail -> detail.contains("secret-row") || detail.contains("insert into"));

        var balance = handler.handleIntegrity(
                violation("Check constraint violation: ck_accounts_balance_non_negative"), request);
        assertThat(balance.getBody().code()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(balance.getBody().message()).doesNotContain("ck_accounts_balance_non_negative");

        var transfer = handler.handleIntegrity(violation("Duplicate key uk_transfers_reference"), request);
        assertThat(transfer.getBody().message()).isEqualTo("Transfer reference already exists");
    }

    @Test
    void unrelatedConstraintIsNotRelabeledAsADuplicateOrInsufficientFunds() {
        var request = new MockHttpServletRequest("POST", "/api/transfers");
        var response = handler.handleIntegrity(
                violation("Unique index uk_other_table failed while updating balance_audit"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo("DATA_CONSTRAINT");
        assertThat(response.getBody().message()).isEqualTo("Request could not be stored");
        assertThat(response.getBody().details()).containsExactly("A data constraint was violated");
        assertThat(response.getBody().message()).doesNotContain("uk_other_table");
    }

    private DataIntegrityViolationException violation(String databaseMessage) {
        return new DataIntegrityViolationException("could not execute statement", new IllegalStateException(databaseMessage));
    }
}
