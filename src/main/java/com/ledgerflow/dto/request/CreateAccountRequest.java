package com.ledgerflow.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateAccountRequest")
public record CreateAccountRequest(
        @Schema(example = "Alice", description = "Account holder name. Balance, id, and createdAt are server-generated.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must be at most 120 characters")
        String name
) {
}
