package com.ledgerflow.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

public class InsufficientFundsException extends ApiException {

    public InsufficientFundsException() {
        super(HttpStatus.CONFLICT,
                "INSUFFICIENT_FUNDS",
                "Insufficient funds",
                List.of("The account balance is lower than the requested debit"));
    }
}
