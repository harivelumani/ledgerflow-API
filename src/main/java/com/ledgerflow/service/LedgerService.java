package com.ledgerflow.service;

import com.ledgerflow.dto.response.TransactionPageResponse;
import com.ledgerflow.dto.response.TransactionResponse;
import com.ledgerflow.entity.Account;
import com.ledgerflow.entity.LedgerTransaction;
import com.ledgerflow.entity.TransactionType;
import com.ledgerflow.exception.AccountNotFoundException;
import com.ledgerflow.exception.DuplicateReferenceException;
import com.ledgerflow.exception.InvalidTransactionTypeException;
import com.ledgerflow.mapper.LedgerMapper;
import com.ledgerflow.money.Money;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.LedgerTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

@Service
public class LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerMapper ledgerMapper;
    private final LedgerWriteHook ledgerWriteHook;
    private final Clock clock;

    public LedgerService(
            AccountRepository accountRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            LedgerMapper ledgerMapper,
            LedgerWriteHook ledgerWriteHook,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerMapper = ledgerMapper;
        this.ledgerWriteHook = ledgerWriteHook;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse post(
            UUID accountId,
            TransactionType type,
            BigDecimal rawAmount,
            String rawDescription,
            String rawReferenceId) {
        if (type == null) {
            throw new InvalidTransactionTypeException();
        }
        BigDecimal amount = Money.requirePositive(rawAmount);
        String description = InputRules.required(rawDescription, "description", InputRules.DESCRIPTION_MAX);
        String referenceId = InputRules.required(rawReferenceId, "referenceId", InputRules.REFERENCE_MAX);

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        if (ledgerTransactionRepository.existsByAccountIdAndReferenceId(accountId, referenceId)) {
            throw new DuplicateReferenceException(
                    "Transaction reference already exists",
                    "A transaction with this referenceId already exists for the account");
        }

        if (type == TransactionType.CREDIT) {
            account.credit(amount);
        } else {
            account.debit(amount);
        }
        accountRepository.saveAndFlush(account);
        ledgerWriteHook.beforeLedgerInsert();

        LedgerTransaction transaction = ledgerTransactionRepository.saveAndFlush(LedgerTransaction.record(
                account.getId(),
                type,
                amount,
                description,
                referenceId,
                clock.instant(),
                null));
        return ledgerMapper.toTransaction(transaction, account.getBalance());
    }

    @Transactional(readOnly = true)
    public TransactionPageResponse history(UUID accountId, int page, int size, String rawType) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        TransactionType type = parseType(rawType);
        Pageable pageable = PageRequests.of(page, size);
        Page<LedgerTransaction> result = type == null
                ? ledgerTransactionRepository.findByAccountId(accountId, pageable)
                : ledgerTransactionRepository.findByAccountIdAndType(accountId, type, pageable);
        return ledgerMapper.toPage(result);
    }

    private TransactionType parseType(String rawType) {
        if (rawType == null) {
            return null;
        }
        if (rawType.isBlank()) {
            throw new InvalidTransactionTypeException();
        }
        try {
            return TransactionType.valueOf(rawType.trim());
        } catch (IllegalArgumentException exception) {
            throw new InvalidTransactionTypeException();
        }
    }
}
