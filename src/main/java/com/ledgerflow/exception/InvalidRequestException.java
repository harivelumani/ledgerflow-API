package com.ledgerflow.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String detail) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", List.of(detail));
    }
}
