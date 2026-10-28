package com.ledgerflow.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String referenceId,
        String description,
        UUID debitTransactionId,
        UUID creditTransactionId,
        BigDecimal sourceBalance,
        BigDecimal destinationBalance,
        Instant createdAt
) {
}
