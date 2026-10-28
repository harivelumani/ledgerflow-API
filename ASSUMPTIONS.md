# Assumptions

1. The installed JDK is 25. The project compiles with `--release 21` and runs on JDK 25. The Docker image uses Eclipse Temurin 21.
2. Spring Boot 3.5.16 is the Spring Boot 3 line used here because it runs on the available JDK. Java 17 and Java 21 are not installed on this machine.
3. Description is required, must not be blank, and is limited to 255 characters. Reference IDs are required, trimmed, and limited to 64 characters. Account names are required, trimmed, and limited to 120 characters.
4. Amounts use `DECIMAL(19,2)` and `BigDecimal` with scale 2 and `RoundingMode.HALF_UP`. More than two decimal places are rejected. Zero and negative amounts are rejected. A debit equal to the current balance is allowed and leaves a zero balance.
5. Clients cannot send an initial balance, an id, or a timestamp. Unknown JSON properties, including `balance`, are rejected with HTTP 400.
6. Insufficient funds and duplicate references use HTTP 409. Validation and malformed JSON use HTTP 400. Missing accounts use HTTP 404.
7. Replaying a reference returns 409. It does not return the original success payload. A caller that times out after a successful write should treat 409 as "already recorded" and read the balance or history.
8. A ledger `referenceId` is unique per account. The same value may be used on a different account.
9. A transfer `referenceId` is unique across all transfers. That same value is also stored on the source debit and the destination credit, so it cannot later be reused as a normal transaction reference on either participating account. Both ledger rows store `transfer_id`.
10. There is no authentication or authorization. Do not expose this API on a public network without adding an auth layer.
11. The local JDBC URL disables TLS so a developer MySQL container can start without certificates. Production should set `DB_URL` to a TLS connection string.
12. No database trigger maintains balances. The only balance mutations are `Account.credit` and `Account.debit`, and the only callers are `LedgerService.post` and `TransferService.transfer`. Both methods are `@Transactional`. A `CHECK (balance >= 0)` constraint is the database backstop.
13. Account rows are locked with `PESSIMISTIC_WRITE` for the duration of the write transaction. Transfers lock the two accounts in ascending UUID order.
14. `LedgerWriteHook` is a no-op in production. Tests replace it to fail after the balance update and before the ledger insert, which proves the surrounding transaction rolls back.
15. The default suite uses H2 in MySQL compatibility mode. H2 does not prove every InnoDB lock or deadlock detail. `mvn test -Pmysql` starts MySQL 8.4 with Testcontainers and checks the unique constraints plus concurrent debits. It does not rerun the transfer or rollback tests. Flyway 11.7.2 warns that MySQL 8.4 is newer than the 8.1 line it has tested; the version 1 migration still applied.
16. Timestamps are `Instant` values in UTC, serialized as ISO-8601 with a `Z` suffix, and stored as `TIMESTAMP(6)`.
17. History uses zero-based pages. The default page size is 20 and the maximum is 100. Sorting is `createdAt DESC, id DESC`.
18. There is no endpoint to update or delete an account, transaction, or transfer. Ledger and transfer columns are `updatable = false`, and the ledger and transfer entities are Hibernate `@Immutable`.
