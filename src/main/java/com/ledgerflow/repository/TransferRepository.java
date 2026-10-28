package com.ledgerflow.repository;

import com.ledgerflow.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    boolean existsByReferenceId(String referenceId);
}
