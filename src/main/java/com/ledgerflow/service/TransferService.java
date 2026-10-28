package com.ledgerflow.service;

import com.ledgerflow.dto.response.TransferResponse;
import com.ledgerflow.entity.Account;
import com.ledgerflow.entity.LedgerTransaction;
import com.ledgerflow.entity.TransactionType;
import com.ledgerflow.entity.Transfer;
import com.ledgerflow.exception.AccountNotFoundException;
import com.ledgerflow.exception.DuplicateReferenceException;
import com.ledgerflow.exception.InvalidRequestException;
import com.ledgerflow.exception.SameAccountTransferException;
import com.ledgerflow.mapper.LedgerMapper;
import com.ledgerflow.money.Money;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.LedgerTransactionRepository;
import com.ledgerflow.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final TransferRepository transferRepository;
    private final LedgerMapper ledgerMapper;
    private final LedgerWriteHook ledgerWriteHook;
    private final Clock clock;

    public TransferService(
            AccountRepository accountRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            TransferRepository transferRepository,
            LedgerMapper ledgerMapper,
            LedgerWriteHook ledgerWriteHook,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.transferRepository = transferRepository;
        this.ledgerMapper = ledgerMapper;
        this.ledgerWriteHook = ledgerWriteHook;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferResponse transfer(
            UUID sourceId,
            UUID destinationId,
            BigDecimal rawAmount,
            String rawReferenceId,
            String rawDescription) {
        if (sourceId == null || destinationId == null) {
            throw new InvalidRequestException("sourceAccountId and destinationAccountId are required");
        }
        if (sourceId.equals(destinationId)) {
            throw new SameAccountTransferException();
        }
        BigDecimal amount = Money.requirePositive(rawAmount);
        String referenceId = InputRules.required(rawReferenceId, "referenceId", InputRules.REFERENCE_MAX);
        String description = InputRules.required(rawDescription, "description", InputRules.DESCRIPTION_MAX);
        requireBothAccounts(sourceId, destinationId);

        // Lock in UUID order so two transfers that touch the same pair cannot deadlock.
        UUID firstId = sourceId.compareTo(destinationId) < 0 ? sourceId : destinationId;
        UUID secondId = firstId.equals(sourceId) ? destinationId : sourceId;
        Account first = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new AccountNotFoundException(firstId));
        Account second = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new AccountNotFoundException(secondId));
        Account source = first.getId().equals(sourceId) ? first : second;
        Account destination = source == first ? second : first;

        if (transferRepository.existsByReferenceId(referenceId)
                || ledgerTransactionRepository.existsByAccountIdAndReferenceId(sourceId, referenceId)
                || ledgerTransactionRepository.existsByAccountIdAndReferenceId(destinationId, referenceId)) {
            throw new DuplicateReferenceException(
                    "Transfer reference already exists",
                    "A transfer with this referenceId already exists");
        }

        source.debit(amount);
        destination.credit(amount);
        accountRepository.saveAndFlush(source);
        accountRepository.saveAndFlush(destination);
        ledgerWriteHook.beforeLedgerInsert();

        Instant createdAt = clock.instant();
        Transfer transfer = transferRepository.saveAndFlush(Transfer.create(
                sourceId, destinationId, amount, referenceId, description, createdAt));
        LedgerTransaction debit = ledgerTransactionRepository.saveAndFlush(LedgerTransaction.record(
                sourceId, TransactionType.DEBIT, amount, description, referenceId, createdAt, transfer.getId()));
        LedgerTransaction credit = ledgerTransactionRepository.saveAndFlush(LedgerTransaction.record(
                destinationId, TransactionType.CREDIT, amount, description, referenceId, createdAt, transfer.getId()));
        return ledgerMapper.toTransfer(
                transfer, debit.getId(), credit.getId(), source.getBalance(), destination.getBalance());
    }

    private void requireBothAccounts(UUID sourceId, UUID destinationId) {
        List<String> missing = new ArrayList<>();
        if (!accountRepository.existsById(sourceId)) {
            missing.add("Source account does not exist: " + sourceId);
        }
        if (!accountRepository.existsById(destinationId)) {
            missing.add("Destination account does not exist: " + destinationId);
        }
        if (!missing.isEmpty()) {
            throw new AccountNotFoundException(missing);
        }
    }
}
