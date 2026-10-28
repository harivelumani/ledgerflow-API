package com.ledgerflow.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "AccountResponse")
public record AccountResponse(
        UUID id,
        @Schema(example = "Alice") String name,
        @Schema(example = "0.00") BigDecimal balance,
        Instant createdAt
) {
}
