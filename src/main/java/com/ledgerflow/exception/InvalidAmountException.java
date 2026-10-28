package com.ledgerflow.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

public class InvalidAmountException extends ApiException {

    public InvalidAmountException(String message, String detail) {
        super(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", message, List.of(detail));
    }
}
