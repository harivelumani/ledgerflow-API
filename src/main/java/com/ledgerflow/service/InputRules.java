package com.ledgerflow.service;

import com.ledgerflow.exception.InvalidRequestException;

public final class InputRules {

    public static final int NAME_MAX = 120;
    public static final int DESCRIPTION_MAX = 255;
    public static final int REFERENCE_MAX = 64;

    private InputRules() {
    }

    public static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new InvalidRequestException(field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }
}
