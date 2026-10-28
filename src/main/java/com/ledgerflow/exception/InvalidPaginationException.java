package com.ledgerflow.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

public class InvalidPaginationException extends ApiException {

    public InvalidPaginationException(String detail) {
        super(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Invalid pagination parameters", List.of(detail));
    }
}
