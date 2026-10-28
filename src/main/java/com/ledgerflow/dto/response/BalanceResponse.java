package com.ledgerflow.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "BalanceResponse")
public record BalanceResponse(
        UUID accountId,
        @Schema(example = "150.00") BigDecimal balance
) {
}
