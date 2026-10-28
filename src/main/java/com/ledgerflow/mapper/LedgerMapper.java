package com.ledgerflow.mapper;

import com.ledgerflow.dto.response.AccountResponse;
import com.ledgerflow.dto.response.BalanceResponse;
import com.ledgerflow.dto.response.TransactionItemResponse;
import com.ledgerflow.dto.response.TransactionPageResponse;
import com.ledgerflow.dto.response.TransactionResponse;
import com.ledgerflow.dto.response.TransferResponse;
import com.ledgerflow.entity.Account;
import com.ledgerflow.entity.LedgerTransaction;
import com.ledgerflow.entity.Transfer;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class LedgerMapper {

    public AccountResponse toAccount(Account account) {
        return new AccountResponse(account.getId(), account.getName(), account.getBalance(), account.getCreatedAt());
    }

    public BalanceResponse toBalance(Account account) {
        return new BalanceResponse(account.getId(), account.getBalance());
    }

    public TransactionResponse toTransaction(LedgerTransaction transaction, BigDecimal balance) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getReferenceId(),
                transaction.getCreatedAt(),
                balance);
    }

    public TransactionItemResponse toItem(LedgerTransaction transaction) {
        return new TransactionItemResponse(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getReferenceId(),
                transaction.getCreatedAt());
    }

    public TransactionPageResponse toPage(Page<LedgerTransaction> page) {
        return new TransactionPageResponse(
                page.getContent().stream().map(this::toItem).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public TransferResponse toTransfer(
            Transfer transfer,
            UUID debitTransactionId,
            UUID creditTransactionId,
            BigDecimal sourceBalance,
            BigDecimal destinationBalance) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getSourceAccountId(),
                transfer.getDestinationAccountId(),
                transfer.getAmount(),
                transfer.getReferenceId(),
                transfer.getDescription(),
                debitTransactionId,
                creditTransactionId,
                sourceBalance,
                destinationBalance,
                transfer.getCreatedAt());
    }
}
