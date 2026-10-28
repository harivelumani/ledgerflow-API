package com.ledgerflow.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

public class DuplicateReferenceException extends ApiException {

    public DuplicateReferenceException(String message, String detail) {
        super(HttpStatus.CONFLICT, "DUPLICATE_REFERENCE", message, List.of(detail));
    }
}
