package com.ledgerflow.dto.response;

import com.ledgerflow.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionItemResponse(
        UUID id,
        UUID accountId,
        TransactionType type,
        BigDecimal amount,
        String description,
        String referenceId,
        Instant createdAt
) {
}
