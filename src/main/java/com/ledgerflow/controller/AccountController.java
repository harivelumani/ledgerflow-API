package com.ledgerflow.controller;

import com.ledgerflow.dto.request.CreateAccountRequest;
import com.ledgerflow.dto.response.AccountResponse;
import com.ledgerflow.dto.response.BalanceResponse;
import com.ledgerflow.dto.response.ErrorResponse;
import com.ledgerflow.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts", description = "Open accounts and read the persisted balance")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @Operation(
            summary = "Create an account",
            description = "Creates an account with a server-generated id, a zero balance, and a UTC timestamp. Initial balance is not accepted.")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Missing name, blank name, or unexpected fields such as balance",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse body = accountService.create(request.name());
        return ResponseEntity.created(URI.create("/api/accounts/" + body.id() + "/balance")).body(body);
    }

    @GetMapping("/{accountId}/balance")
    @Operation(summary = "Get the current balance", description = "Returns the persisted balance. This read does not recalculate or change it.")
    @ApiResponse(responseCode = "200", description = "Current balance")
    @ApiResponse(responseCode = "404", description = "Account does not exist",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public BalanceResponse balance(@PathVariable UUID accountId) {
        return accountService.getBalance(accountId);
    }
}
