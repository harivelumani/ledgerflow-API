package com.ledgerflow.service;

import com.ledgerflow.dto.response.AccountResponse;
import com.ledgerflow.dto.response.BalanceResponse;
import com.ledgerflow.entity.Account;
import com.ledgerflow.exception.AccountNotFoundException;
import com.ledgerflow.mapper.LedgerMapper;
import com.ledgerflow.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerMapper ledgerMapper;
    private final Clock clock;

    public AccountService(AccountRepository accountRepository, LedgerMapper ledgerMapper, Clock clock) {
        this.accountRepository = accountRepository;
        this.ledgerMapper = ledgerMapper;
        this.clock = clock;
    }

    @Transactional
    public AccountResponse create(String rawName) {
        String name = InputRules.required(rawName, "name", InputRules.NAME_MAX);
        Account account = accountRepository.saveAndFlush(Account.open(name, clock.instant()));
        return ledgerMapper.toAccount(account);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return ledgerMapper.toBalance(account);
    }
}
