package com.ledgerflow.controller;

import com.ledgerflow.dto.request.CreateTransferRequest;
import com.ledgerflow.dto.response.ErrorResponse;
import com.ledgerflow.dto.response.TransferResponse;
import com.ledgerflow.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
@Tag(name = "Transfers", description = "Move money between two accounts as one atomic pair of ledger entries")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @Operation(
            summary = "Transfer money",
            description = """
                    Debits the source account and credits the destination account. Both balance changes and both ledger
                    entries share one database transaction. Account row locks are taken in sorted UUID order.
                    referenceId is globally unique. Replaying it returns 409 and does not move money again.
                    The same reference is stored on both ledger entries and on the transfer, and both entries
                    store the transfer id.
                    """)
    @ApiResponse(responseCode = "201", description = "Transfer completed")
    @ApiResponse(responseCode = "400", description = "Invalid amount, same account, or missing fields",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Source or destination account does not exist",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Insufficient source funds or duplicate referenceId",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody CreateTransferRequest request) {
        TransferResponse body = transferService.transfer(
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount(),
                request.referenceId(),
                request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
