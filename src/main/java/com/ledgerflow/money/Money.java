package com.ledgerflow.money;

import com.ledgerflow.exception.InvalidAmountException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
    public static final BigDecimal MAX = new BigDecimal("99999999999999999.99");

    private Money() {
    }

    public static BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidAmountException("amount is required", "amount is required");
        }
        if (amount.scale() > SCALE) {
            throw new InvalidAmountException(
                    "amount must have at most 2 decimal places",
                    "amount must have at most 2 decimal places");
        }
        BigDecimal normalized = amount.setScale(SCALE, ROUNDING);
        if (normalized.compareTo(ZERO) <= 0) {
            throw new InvalidAmountException(
                    "amount must be greater than zero",
                    "amount must be greater than zero");
        }
        if (normalized.compareTo(MAX) > 0) {
            throw new InvalidAmountException(
                    "amount exceeds the supported range",
                    "amount must fit in DECIMAL(19,2)");
        }
        return normalized;
    }

    public static BigDecimal scale(BigDecimal amount) {
        return amount.setScale(SCALE, ROUNDING);
    }
}
