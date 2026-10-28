package com.ledgerflow.repository;

import com.ledgerflow.entity.LedgerTransaction;
import com.ledgerflow.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

    boolean existsByAccountIdAndReferenceId(UUID accountId, String referenceId);

    long countByAccountId(UUID accountId);

    Page<LedgerTransaction> findByAccountId(UUID accountId, Pageable pageable);

    Page<LedgerTransaction> findByAccountIdAndType(UUID accountId, TransactionType type, Pageable pageable);
}
