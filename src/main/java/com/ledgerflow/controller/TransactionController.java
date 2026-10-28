package com.ledgerflow.controller;

import com.ledgerflow.dto.request.CreateTransactionRequest;
import com.ledgerflow.dto.response.ErrorResponse;
import com.ledgerflow.dto.response.TransactionPageResponse;
import com.ledgerflow.dto.response.TransactionResponse;
import com.ledgerflow.service.LedgerService;
import com.ledgerflow.service.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/accounts/{accountId}/transactions")
@Tag(name = "Transactions", description = "Append-only credits and debits. Existing transactions cannot be edited or deleted.")
public class TransactionController {

    private final LedgerService ledgerService;

    public TransactionController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping
    @Operation(
            summary = "Post a credit or debit",
            description = """
                    Credits increase the balance and debits decrease it. A debit that would make the balance negative is rejected.
                    referenceId is unique per account. The balance update and the ledger insert commit or roll back together.
                    """)
    @ApiResponse(responseCode = "201", description = "Transaction posted")
    @ApiResponse(responseCode = "400", description = "Invalid type, amount, description, or reference",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Account does not exist",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Insufficient funds or duplicate referenceId",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<TransactionResponse> post(
            @PathVariable UUID accountId,
            @Valid @RequestBody CreateTransactionRequest request) {
        TransactionResponse body = ledgerService.post(
                accountId, request.type(), request.amount(), request.description(), request.referenceId());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    @Operation(
            summary = "List transaction history",
            description = """
                    Zero-based pagination. page defaults to 0 and size defaults to 20. size must be from 1 to 100.
                    Results are sorted by createdAt descending, then by transaction id descending.
                    Omit type to return both CREDIT and DEBIT.
                    """)
    @ApiResponse(responseCode = "200", description = "A page of transactions for this account")
    @ApiResponse(responseCode = "400", description = "Invalid page, size, or type",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Account does not exist",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TransactionPageResponse history(
            @PathVariable UUID accountId,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size from 1 to 100", example = "10")
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_SIZE) int size,
            @Parameter(description = "Optional filter: CREDIT or DEBIT")
            @RequestParam(required = false) String type) {
        return ledgerService.history(accountId, page, size, type);
    }
}
