package com.ledgerflow.dto.response;

import com.ledgerflow.entity.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "TransactionResponse")
public record TransactionResponse(
        UUID id,
        UUID accountId,
        TransactionType type,
        BigDecimal amount,
        String description,
        String referenceId,
        Instant createdAt,
        @Schema(description = "Account balance after this transaction was applied") BigDecimal balance
) {
}
