package com.ledgerflow.dto.request;

import com.ledgerflow.entity.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "CreateTransactionRequest")
public record CreateTransactionRequest(
        @Schema(example = "CREDIT", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "type is required")
        TransactionType type,
        @Schema(example = "100.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount,
        @Schema(example = "Salary", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "description is required")
        @Size(max = 255, message = "description must be at most 255 characters")
        String description,
        @Schema(example = "SALARY-001", description = "Idempotency key. Unique per account.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "referenceId is required")
        @Size(max = 64, message = "referenceId must be at most 64 characters")
        String referenceId
) {
}
