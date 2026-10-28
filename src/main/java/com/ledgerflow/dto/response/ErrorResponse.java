package com.ledgerflow.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(name = "ErrorResponse")
public record ErrorResponse(
        @Schema(example = "2026-10-27T10:00:00Z") Instant timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String error,
        @Schema(example = "VALIDATION_ERROR") String code,
        @Schema(example = "Request validation failed") String message,
        List<String> details,
        @Schema(example = "/api/accounts") String path
) {
}
