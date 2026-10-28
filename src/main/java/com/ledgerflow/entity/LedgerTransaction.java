package com.ledgerflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(
        name = "ledger_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_txn_account_reference",
                columnNames = {"account_id", "reference_id"}))
public class LedgerTransaction {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 36)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "account_id", nullable = false, updatable = false, length = 36)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private TransactionType type;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 255)
    private String description;

    @Column(name = "reference_id", nullable = false, updatable = false, length = 64)
    private String referenceId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "transfer_id", updatable = false, length = 36)
    private UUID transferId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerTransaction() {
    }

    public static LedgerTransaction record(
            UUID accountId,
            TransactionType type,
            BigDecimal amount,
            String description,
            String referenceId,
            Instant createdAt,
            UUID transferId) {
        LedgerTransaction transaction = new LedgerTransaction();
        transaction.id = UUID.randomUUID();
        transaction.accountId = accountId;
        transaction.type = type;
        transaction.amount = amount;
        transaction.description = description;
        transaction.referenceId = referenceId;
        transaction.createdAt = createdAt;
        transaction.transferId = transferId;
        return transaction;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LedgerTransaction transaction)) {
            return false;
        }
        return id != null && id.equals(transaction.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
