package com.ledgerflow.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

public class InvalidTransactionTypeException extends ApiException {

    public InvalidTransactionTypeException() {
        super(HttpStatus.BAD_REQUEST,
                "INVALID_TRANSACTION_TYPE",
                "Invalid transaction type",
                List.of("type must be CREDIT or DEBIT"));
    }
}
