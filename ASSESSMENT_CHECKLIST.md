# Assessment checklist

| Requirement | Implementation | Test |
| --- | --- | --- |
| Java 21 bytecode, Spring Boot 3.5, Maven | `pom.xml` | `mvn clean verify` |
| Web, JPA, Validation, Actuator, springdoc | `pom.xml`, controllers, `OpenApiConfig` | `ApiContractIntegrationTest.openApiDocumentsTheLedgerEndpoints` |
| `BigDecimal` scale 2, half up, no float/double money | `Money`, entity columns | `MoneyTest`, ledger example test |
| UTC timestamps | `Clock.systemUTC()`, Jackson UTC, Hikari `SET time_zone` | Account create asserts `2026-10-27T10:00:00Z` |
| Layered packages, constructor injection | `com.ledgerflow.*` | Context startup in integration tests |
| Account and ledger entities, enums, constraints | entities and `V1__create_ledger_schema.sql` | Schema validate on startup; MySQL profile checks constraint names |
| Client cannot set id, balance, or timestamp | Request DTOs, unknown-property rejection, server factories | `AccountIntegrationTest.rejectsClientSuppliedBalance` |
| `POST /api/accounts` returns 201 and zero balance | `AccountController`, `AccountService` | `createsAccountWithZeroBalanceAndServerTimestamp` |
| Blank or missing name is 400 | Bean validation | `rejectsMissingOrBlankName` |
| `POST /api/accounts/{id}/transactions` | `TransactionController`, `LedgerService` | `creditsAndDebitsFollowTheLedgerExample` |
| Credit increases, debit decreases | `Account.credit` / `debit` under a lock | Same test, plus debit-to-zero |
| Insufficient debit is rejected and has no effect | `InsufficientFundsException`, balance check constraint | `insufficientDebitDoesNotChangeBalanceOrHistory` |
| Zero, negative, and over-precise amounts rejected | `@DecimalMin`, `@Digits`, `Money.requirePositive` | `rejectsInvalidAmountsTypesAndReferences`, `MoneyTest` |
| Missing or blank reference rejected | `@NotBlank` | Same test |
| Invalid type rejected | Jackson enum plus query parser | Same test and pagination test |
| Duplicate reference is 409 and does not change the balance | In-lock check plus `uk_txn_account_reference` | `duplicateReferenceDoesNotChangeBalanceAndIsAllowedOnAnotherAccount` |
| Same reference allowed on another account | Composite unique key | Same test |
| Missing account is 404 for balance, post, and history | `AccountNotFoundException` | Account and transaction tests |
| Balance read does not mutate | Read-only transaction, no recalculation | `readingBalanceDoesNotMutateIt` |
| History filter, page, size, latest-first, id tie-break | `PageRequests`, repository `Pageable` | `historyIsFilteredPagedAndSortedLatestFirst`, `equalTimestampsUseTransactionIdAsTieBreaker` |
| Invalid pagination is 400, max size 100 | `PageRequests` | `PageRequestsTest`, `rejectsInvalidPaginationAndTypeFilter` |
| Empty page metadata | Spring Data page mapped to the response record | Empty debit filter assertion |
| No edit or delete API | No mappings; immutable entities | `thereAreNoUpdateOrDeleteEndpoints` |
| Atomic balance update and ledger insert | `@Transactional`, flush, exception propagation | `balanceUpdateRollsBackWhenLedgerInsertFails`, `constraintFailureRollsBackTheBalanceUpdateAndTheEarlierInsert` |
| Concurrent debits cannot overspend | `PESSIMISTIC_WRITE` | H2: `concurrentDebitsCannotOverspend`. MySQL 8.4: `MySqlLedgerIntegrationTest` on 27 October 2026, 3 of 8 debits succeeded and the balance stayed 10.00. |
| Concurrent duplicate submissions apply once | Lock plus unique constraint | `concurrentDuplicateSubmissionsApplyOnce` |
| Central JSON errors, no SQL or stack traces in the body | `GlobalExceptionHandler` | Status and `code` assertions across the API tests |
| Env-based MySQL config, no committed secrets | `application.yml`, `.env.example` | Inspected; `.env` is gitignored |
| Flyway, `ddl-auto=validate` | `application.yml`, `V1__create_ledger_schema.sql` | Context startup validates the schema |
| Dockerfile and Compose without baked credentials | `Dockerfile`, `docker-compose.yml` | Compose started on 27 October 2026. MySQL 8.4.11 healthy, API healthy, Flyway version 1 applied. Host port 3307 was used because 3306 was already taken. |
| Swagger UI | springdoc, README URL | OpenAPI document test. Live `GET /swagger-ui.html` returned 302 and `/v3/api-docs` returned 200. |
| Transfer atomicity, distinct accounts, deadlock-safe locks | `TransferService` | `TransferIntegrationTest`, `ConcurrencyIntegrationTest` |
| Transfer rollback | Hook before ledger insert, same transaction | `transferRollsBackBothBalancesWhenLedgerInsertFails` |
| Duplicate transfer does not apply twice | `uk_transfers_reference` | Sequential and concurrent transfer tests |
| MySQL migration, unique keys, and concurrent debits | `V1__create_ledger_schema.sql`, `MySqlLedgerIntegrationTest` | `mvn test -Pmysql` on 27 October 2026: 1 test, 0 failures, MySQL 8.4.11. Flyway warned that 8.4 is newer than its tested 8.1 line and then applied version 1. |
| Transfer concurrency and deadlock avoidance on MySQL | `TransferService` lock order | NOT VERIFIED on MySQL. Proven on H2 by `ConcurrencyIntegrationTest`. |
| Rollback hook and full API suite on MySQL | Service transactions and H2 tests | NOT VERIFIED as a MySQL test run. The live Compose workflow covered the main account, ledger, history, duplicate, insufficient-funds, and transfer path. |

`mvn clean verify` on 27 October 2026: **33 tests, 0 failures, 0 errors, 0 skipped**, JDK 25.0.2, Java 21 bytecode, H2. The `mysql` tag is excluded from that command. `mvn test -Pmysql` the same day: **1 test, 0 failures**, Testcontainers MySQL 8.4.11.
