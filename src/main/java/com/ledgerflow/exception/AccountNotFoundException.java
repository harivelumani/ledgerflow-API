package com.ledgerflow.exception;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

public class AccountNotFoundException extends ApiException {

    public AccountNotFoundException(UUID accountId) {
        super(HttpStatus.NOT_FOUND,
                "ACCOUNT_NOT_FOUND",
                "Account not found",
                List.of("No account exists for id " + accountId));
    }

    public AccountNotFoundException(List<String> details) {
        super(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account not found", details);
    }
}
