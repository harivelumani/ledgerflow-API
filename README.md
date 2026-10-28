# LedgerFlow

LedgerFlow is a Transaction Ledger REST API. It opens accounts, posts immutable credit and debit entries, reads the persisted balance, lists history with filtering and pagination, and transfers money between two accounts in one database transaction.

Monetary values are `BigDecimal` with scale 2. Timestamps are UTC. Clients cannot set balances, identifiers, or creation timestamps, and they cannot edit or delete a posted transaction.

## Features

- Create an account with a server-generated id, a zero balance, and a UTC timestamp.
- Post a `CREDIT` or `DEBIT`. A debit that would make the balance negative is rejected and leaves no ledger row.
- Keep the balance update and the ledger insert in one transaction, with a pessimistic lock on the account row.
- Reject a repeated `referenceId` for the same account with HTTP 409, including concurrent retries.
- Read the current balance without recalculating it.
- List history latest-first, with optional `CREDIT` or `DEBIT` filtering and zero-based pagination.
- Transfer money between two accounts atomically, locking accounts in UUID order.
- Consistent JSON errors, Flyway migrations, and OpenAPI.

## Technology stack

- Java 21 bytecode (compiled and tested here on JDK 25; Docker image uses Temurin 21)
- Spring Boot 3.5.16
- Maven
- Spring Web, Spring Data JPA, Hibernate, Jakarta Bean Validation
- MySQL 8 for the application runtime
- Flyway
- springdoc OpenAPI
- JUnit 5, AssertJ, Mockito, Spring Boot Test
- H2 for the default test profile
- Testcontainers MySQL 8 via the `mysql` Maven profile

## Architecture

Layered packages under `com.ledgerflow`:

| Package | Responsibility |
| --- | --- |
| `controller` | HTTP mapping only |
| `service` | Business rules, locks, and transaction boundaries |
| `repository` | Spring Data JPA |
| `entity` | `Account`, `LedgerTransaction`, `Transfer` |
| `dto.request` / `dto.response` | API models. Entities are not returned |
| `exception` | Domain errors and `GlobalExceptionHandler` |
| `mapper` | Entity to response mapping |
| `config` | Clock and OpenAPI |
| `money` | Scale, rounding, and amount checks |

Balance changes happen only in `LedgerService.post` and `TransferService.transfer`. There is no trigger. Both methods are `@Transactional(isolation = READ_COMMITTED)`. They lock the account with `PESSIMISTIC_WRITE`, change the balance, flush it, then insert the ledger row. If the insert fails, the exception leaves the transaction method and Spring rolls the balance change back. `Account.debit` refuses a negative result, and MySQL enforces `CHECK (balance >= 0)`.

Duplicate protection does not rely only on an application check. `ledger_transactions` has a unique constraint on `(account_id, reference_id)`. The service also checks for an existing reference while holding the account lock. A constraint failure is translated to HTTP 409 without returning SQL text.

Transfers lock the two accounts in ascending UUID order before changing either balance, so two overlapping transfers cannot deadlock. The transfer reference is globally unique. Both ledger rows store that reference and the transfer id.

## Prerequisites

- JDK 21 or newer (21 is the bytecode target; JDK 25 was used in this workspace)
- Maven 3.9+
- MySQL 8, or Docker with Docker Compose, for a real server
- Docker is optional for the default test run

## Database setup

Create a database and user, then let Flyway create the tables on startup. Do not point Hibernate at `ddl-auto=create` or `create-drop`. The main profile uses `ddl-auto=validate`.

```sql
CREATE DATABASE ledgerflow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'ledger'@'%' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON ledgerflow.* TO 'ledger'@'%';
```

The migration is `src/main/resources/db/migration/V1__create_ledger_schema.sql`. It creates:

- `accounts` with a non-negative `DECIMAL(19,2)` balance
- `ledger_transactions` with a positive amount, a `CREDIT`/`DEBIT` check, a foreign key to `accounts`, and unique `(account_id, reference_id)`
- `transfers` with a globally unique `reference_id`, distinct source and destination accounts, and foreign keys to `accounts`
- `ledger_transactions.transfer_id` referencing `transfers`
- Indexes for account history, type filters, and transfer lookup

Timestamps are `TIMESTAMP(6)` and the JDBC session is set to UTC.

## Environment variables

Copy the example file and replace the placeholders. Do not commit `.env`.

```powershell
Copy-Item .env.example .env
```

| Variable | Purpose |
| --- | --- |
| `DB_URL` | JDBC URL |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password. No real password is committed |
| `SERVER_PORT` | API port, default 8080 |
| `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` | Used by Docker Compose |

`src/main/resources/application.yml` reads those variables. The password default is empty.

## Local startup

From the project root, with MySQL already running:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/ledgerflow?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8"
$env:DB_USERNAME = "ledger"
$env:DB_PASSWORD = "change-me"
mvn spring-boot:run
```

The API listens on `http://localhost:8080`.

## Docker Compose

Compose starts MySQL 8.4, waits until `mysqladmin ping` succeeds, then starts the API. The API connects to `mysql:3306` on the Compose network. Credentials come from `.env`, not from the image. The API health check calls `/actuator/health`.

```powershell
Copy-Item .env.example .env
docker compose up --build -d
```

Stop the containers without deleting the database volume:

```powershell
docker compose stop
```

Start them again later with `docker compose up -d`. Do not use `docker compose down -v`. That command deletes the MySQL volume.

If host port 3306 is already taken, set `MYSQL_PORT` in `.env` to a free port, such as `3307`, and run `docker compose up -d` again. That changes only the published host port. On 27 October 2026 this machine already had a local `mysqld` on 3306, so Compose was published on 3307. The API stayed on port 8080.

## API endpoints

| Method | Path | Success | Error cases |
| --- | --- | --- | --- |
| `POST` | `/api/accounts` | 201 | 400 validation or unknown fields |
| `GET` | `/api/accounts/{accountId}/balance` | 200 | 404 missing account, 400 invalid UUID |
| `POST` | `/api/accounts/{accountId}/transactions` | 201 | 400 invalid body, 404 missing account, 409 insufficient funds or duplicate reference |
| `GET` | `/api/accounts/{accountId}/transactions` | 200 | 400 invalid page, size, or type, 404 missing account |
| `POST` | `/api/transfers` | 201 | 400 invalid body or same account, 404 missing account, 409 insufficient funds or duplicate reference |

There is no update or delete endpoint.

## Example requests

Create an account:

```powershell
curl -s -X POST http://localhost:8080/api/accounts -H "Content-Type: application/json" -d "{\"name\":\"Alice\"}"
```

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "name": "Alice",
  "balance": 0.00,
  "createdAt": "2026-10-27T10:00:00Z"
}
```

Post a credit:

```powershell
curl -s -X POST http://localhost:8080/api/accounts/{accountId}/transactions -H "Content-Type: application/json" -d "{\"type\":\"CREDIT\",\"amount\":100.00,\"description\":\"Salary\",\"referenceId\":\"SALARY-001\"}"
```

The response includes the transaction and the resulting `balance`. A debit uses `"type":"DEBIT"`.

Read the balance:

```powershell
curl -s http://localhost:8080/api/accounts/{accountId}/balance
```

```json
{
  "accountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "balance": 150.00
}
```

Transfer:

```powershell
curl -s -X POST http://localhost:8080/api/transfers -H "Content-Type: application/json" -d "{\"sourceAccountId\":\"ACCOUNT_A\",\"destinationAccountId\":\"ACCOUNT_B\",\"amount\":30.00,\"referenceId\":\"TRANSFER-001\",\"description\":\"Transfer to savings\"}"
```

The response includes the transfer id, both account ids, the amount, both ledger transaction ids, and both resulting balances.

## Swagger UI

With the API running:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI document: `http://localhost:8080/v3/api-docs`

## Pagination

`page` starts at 0. `size` defaults to 20 and must be from 1 to 100. Results are ordered by `createdAt` descending, then by transaction id descending.

```text
GET /api/accounts/{id}/transactions
GET /api/accounts/{id}/transactions?page=0&size=10
GET /api/accounts/{id}/transactions?type=CREDIT
GET /api/accounts/{id}/transactions?type=DEBIT&page=1&size=5
```

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalRecords": 0,
  "totalPages": 0
}
```

An empty account still returns that metadata. A page past the end returns an empty `content` array and the real totals.

## Error response

```json
{
  "timestamp": "2026-10-27T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": ["amount must be greater than zero"],
  "path": "/api/accounts/3fa85f64-5717-4562-b3fc-2c963f66afa6/transactions"
}
```

| Situation | Status | Code |
| --- | --- | --- |
| Validation failure | 400 | `VALIDATION_ERROR` |
| Malformed JSON or unknown property | 400 | `MALFORMED_REQUEST` |
| Invalid transaction type | 400 | `INVALID_TRANSACTION_TYPE` |
| Invalid pagination | 400 | `INVALID_PAGINATION` |
| Invalid account id | 400 | `INVALID_IDENTIFIER` |
| Same source and destination | 400 | `SAME_ACCOUNT_TRANSFER` |
| Account not found | 404 | `ACCOUNT_NOT_FOUND` |
| Unknown path | 404 | `NOT_FOUND` |
| Insufficient funds | 409 | `INSUFFICIENT_FUNDS` |
| Duplicate reference | 409 | `DUPLICATE_REFERENCE` |
| Method not allowed | 405 | `METHOD_NOT_ALLOWED` |
| Unexpected failure | 500 | `INTERNAL_ERROR` |

Unexpected errors return a generic message. SQL, stack traces, and credentials are not included in the body. They are logged on the server.

## Tests

Default suite. It uses H2 and excludes the `mysql` tag:

```powershell
mvn clean verify
```

MySQL 8 suite. It runs only `MySqlLedgerIntegrationTest` and needs Docker:

```powershell
mvn test -Pmysql
```

Executed on 27 October 2026 on Windows 11, JDK 25.0.2, Maven 3.9.13, compiling to Java 21:

| Command | Result |
| --- | --- |
| `mvn clean verify` | BUILD SUCCESS. 33 tests, 0 failures, 0 errors, 0 skipped. Database: H2 in MySQL mode. |
| `mvn test -Pmysql` | BUILD SUCCESS. 1 test, 0 failures. Testcontainers MySQL 8.4.11. Flyway applied version 1, both unique constraints were present, and 8 concurrent debits of 30.00 against a 100.00 balance produced 3 successes and a final balance of 10.00. |
| Docker Compose API | MySQL 8.4.11 healthy and the API healthy. Flyway applied version 1. A live workflow created two accounts, credited 100.00, debited 30.00, transferred 20.00, rejected a repeated reference with 409, and rejected an excessive debit with 409. |

H2 still covers rollback, pagination, transfers, and the broader concurrency cases. Those behaviors were not all repeated by the single MySQL test. The MySQL test does prove the migration, the unique keys, and concurrent debit locking on InnoDB.

## Design decisions

- Ledger rows and transfers are insert-only. Hibernate `@Immutable` and `updatable = false` back that up, and the public API has no `PUT`, `PATCH`, or `DELETE`.
- Money never uses `float` or `double`.
- A failed, rejected, or duplicate transaction does not change the balance. The worked example is credit 100, credit 50, debit 30, debit 20, ending at 100.
- Transfer replay is HTTP 409, the same policy as transaction replay.
- Actuator exposes only `health` and `info`, with health details hidden.

## Known limitations

- There is no authentication. This is an assessment API, not a production service.
- Flyway 11.7.2 warns that MySQL 8.4 is newer than the MySQL 8.1 line it has tested. The migration still applied on MySQL 8.4.11 in both Compose and Testcontainers.
- Local and Compose JDBC URLs disable TLS. A real deployment should set `DB_URL` to a TLS connection string.
- A transfer reference is stored on both ledger rows, so that reference cannot later be posted as an ordinary transaction on either participating account.
- Replaying a reference returns 409. It does not return the original success body.
- The default Maven suite uses H2. Transfer concurrency and the rollback hook were proven there. The MySQL profile proves migration, unique keys, and concurrent debits.
- See `ASSUMPTIONS.md` for the full list.

## Evaluator setup

1. Install JDK 21 or newer and Maven 3.9 or newer. Docker Desktop is required for Compose and for `mvn test -Pmysql`.
2. From this directory, copy `.env.example` to `.env` and keep the MySQL user password and `DB_PASSWORD` identical. Do not commit `.env`.
3. Run `docker compose up --build -d`. If port 3306 is busy, change `MYSQL_PORT` and start again.
4. Open http://localhost:8080/swagger-ui.html.
5. Run `mvn clean verify` for the default suite, then `mvn test -Pmysql` when Docker is available.
6. Stop with `docker compose stop`.
