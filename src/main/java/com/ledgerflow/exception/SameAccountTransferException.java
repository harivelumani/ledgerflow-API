package com.ledgerflow.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

public class SameAccountTransferException extends ApiException {

    public SameAccountTransferException() {
        super(HttpStatus.BAD_REQUEST,
                "SAME_ACCOUNT_TRANSFER",
                "Source and destination accounts must be different",
                List.of("sourceAccountId and destinationAccountId must be different"));
    }
}
