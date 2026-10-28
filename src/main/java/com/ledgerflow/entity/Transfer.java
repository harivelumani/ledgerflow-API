package com.ledgerflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "transfers",
        uniqueConstraints = @UniqueConstraint(name = "uk_transfers_reference", columnNames = "reference_id"))
public class Transfer {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 36)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "source_account_id", nullable = false, updatable = false, length = 36)
    private UUID sourceAccountId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "destination_account_id", nullable = false, updatable = false, length = 36)
    private UUID destinationAccountId;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "reference_id", nullable = false, updatable = false, length = 64)
    private String referenceId;

    @Column(nullable = false, updatable = false, length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transfer() {
    }

    public static Transfer create(
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String referenceId,
            String description,
            Instant createdAt) {
        Transfer transfer = new Transfer();
        transfer.id = UUID.randomUUID();
        transfer.sourceAccountId = sourceAccountId;
        transfer.destinationAccountId = destinationAccountId;
        transfer.amount = amount;
        transfer.referenceId = referenceId;
        transfer.description = description;
        transfer.createdAt = createdAt;
        return transfer;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Transfer transfer)) {
            return false;
        }
        return id != null && id.equals(transfer.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
