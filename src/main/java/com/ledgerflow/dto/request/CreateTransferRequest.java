package com.ledgerflow.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "CreateTransferRequest")
public record CreateTransferRequest(
        @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "sourceAccountId is required")
        UUID sourceAccountId,
        @Schema(example = "7c9e6679-7425-40de-944b-e07fc1f90ae7", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "destinationAccountId is required")
        UUID destinationAccountId,
        @Schema(example = "30.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount,
        @Schema(example = "TRANSFER-001", description = "Globally unique transfer idempotency key.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "referenceId is required")
        @Size(max = 64, message = "referenceId must be at most 64 characters")
        String referenceId,
        @Schema(example = "Transfer to savings", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "description is required")
        @Size(max = 255, message = "description must be at most 255 characters")
        String description
) {
}
