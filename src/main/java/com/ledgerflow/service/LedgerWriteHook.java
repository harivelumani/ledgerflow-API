package com.ledgerflow.service;

/**
 * Called after a balance change has been flushed and before ledger rows are inserted,
 * still inside the same database transaction. The production implementation does nothing.
 * Tests replace it to prove that a failed ledger insert rolls the balance change back.
 */
public interface LedgerWriteHook {

    void beforeLedgerInsert();
}
