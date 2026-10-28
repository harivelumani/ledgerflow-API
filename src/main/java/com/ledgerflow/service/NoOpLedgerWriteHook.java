package com.ledgerflow.service;

import org.springframework.stereotype.Component;

@Component
public class NoOpLedgerWriteHook implements LedgerWriteHook {

    @Override
    public void beforeLedgerInsert() {
        // Intentionally empty. See LedgerWriteHook.
    }
}
