# Interview notes

Questions this project tends to raise, with short answers and where to look in the code. Say the answer in your own words and open the file if you are asked to go deeper.

---

## Money and the ledger

**Why `BigDecimal` and not `double`?**
Binary floating point cannot represent most decimal fractions (0.1 + 0.2 = 0.30000000000000004), so rounding errors accumulate and ledgers stop adding up. Amounts are `BigDecimal` with scale 2 in Java and `DECIMAL(19,2)` in MySQL. Input amounts are normalised with `RoundingMode.UNNECESSARY`, so an amount with three decimals is rejected, not silently rounded. Computed amounts (interest) use `HALF_EVEN`, banker's rounding, so rounding errors don't drift in one direction.
→ `util/Money.java`, `dto/transaction/InternalTransferRequest.java` (`@Digits(integer=13, fraction=2)`)

**How is the ledger modelled?**
One `transactions` row per account movement, with `balance_after` stored on the row. A transfer writes two rows (debit and credit) sharing a reference number. Rows are `@Immutable`; corrections would be compensating entries, never edits. The invariant "balance = credits − debits" is asserted in `TransferConcurrencyIT.assertLedgerMatchesBalance`.
→ `entity/Transaction.java`, `service/ledger/LedgerService.post()`

**What if a bug tries to overdraw an account anyway?**
Three layers: service rules (minimum balance, limits) → `Account.debit()` throws on a negative result → `CHECK (balance >= 0)` in the schema.

---

## Concurrency (the core of the project)

**How do you prevent double spending?**
Every balance change locks the account row first (`SELECT … FOR UPDATE`) inside the transaction, then re-checks balance, status, KYC and limits *against the locked row*, then writes. A second transfer on the same account blocks on the lock and then sees the updated balance.
→ `service/ledger/LedgerService.lock()`, `service/TransferService`

**Why pessimistic rather than optimistic locking for transfers?**
Transfers on a hot account contend often. With optimistic locking, the losers fail and must retry, which gets worse under load and pushes retry logic onto clients. Pessimistic locks make them queue instead. Loan approval uses optimistic locking (`@Version` on `Loan`) because two officers clicking at once is rare, and a clean 409 is the right answer there. `Account` has a `@Version` too, as a safety net: any code path that forgot to lock would fail loudly instead of losing an update.

**How do you avoid deadlocks?**
Always lock in a global order: ascending account id. A→B and B→A both lock the lower id first, so one waits for the other instead of each holding one lock and waiting for the other. `lockInOrder()` sorts, whatever order the caller passes.
→ `LedgerService.lockInOrder()`, test `TransferConcurrencyIT.opposingTransfersNeitherDeadlockNorLoseMoney`

**Why does `lock()` use `entityManager.refresh(entity, PESSIMISTIC_WRITE)` instead of a `@Lock` query?**
If the account was already loaded into the persistence context earlier in the transaction (e.g. `loan.getAccount()`), a locking JPQL query locks the row but Hibernate hands back the *cached* instance with the stale balance. `refresh` re-reads the row under the lock. It's an easy bug to miss, because single-threaded tests never see it.

**Why READ COMMITTED isolation?**
MySQL defaults to REPEATABLE READ, where a transaction's read snapshot is fixed at its first plain SELECT. The transfer does plain reads (ownership check, idempotency lookup) *before* it waits for the row lock. So after acquiring the lock, the daily-limit `SUM` would still read the old snapshot and miss a transfer that committed while we waited. Two parallel transfers could then both fit the limit. Under READ COMMITTED every read sees the latest committed data. It's set on the pool and declared explicitly on `TransferService`.
→ test `TransferConcurrencyIT.dailyLimitHoldsUnderConcurrency`

---

## Idempotency

**What problem does `Idempotency-Key` solve?**
A client times out and retries, a user double-clicks, a mobile network drops the response. Without a key, each retry is a new transfer.

**How does it work?**
1. Fast path: if the key exists and the request hash matches, return the stored response (header `Idempotent-Replayed: true`). If the hash differs → 422 `IDEMPOTENCY_KEY_REUSED`.
2. Otherwise run the transfer. Its *first* write is inserting the key under `UNIQUE(user_id, idempotency_key)`, in the same DB transaction as the money movement, and the response JSON is stored before commit. Money moved and key recorded commit or roll back together.
3. Two identical requests racing past step 1: the second `INSERT` blocks on the unique index until the first commits, then fails with a duplicate key. We catch that and replay the winner's stored response.
→ `service/idempotency/IdempotencyService.java`, test `duplicateRequestsWithOneIdempotencyKeyMoveMoneyOnce`

**What happens to the key if the transfer is declined?**
It rolls back with the transaction, so the client can retry with the same key after, say, topping up the account. Only successful outcomes are cached.

**How do you record a FAILED transaction if the transaction rolls back?**
You can't write it inside the declining transaction, because the rollback removes it. `TransactionService` is deliberately *not* transactional: it calls the transactional `TransferService`, catches `TransactionDeclinedException` after the rollback (locks released), then writes the FAILED row in a new transaction. Doing it with `REQUIRES_NEW` *inside* the outer transaction would deadlock: the child row's FK check needs a shared lock on the account row the suspended outer transaction still holds.
→ `service/TransactionService.java`, `service/ledger/DeclinedTransactionRecorder.java`

---

## Security

**Why are refresh tokens not JWTs?**
They must be revocable and single-use, which requires server-side state anyway. They are 32 random bytes, stored as SHA-256 (a DB leak doesn't leak usable tokens), rotated on every refresh, and grouped into a *family* per login. If an already-rotated token shows up again, someone copied it: the whole family is revoked and the user is emailed. A 10-second grace window avoids false alarms when two tabs refresh at once.
→ `service/auth/AuthService.refresh()`, `service/auth/RefreshTokenService.java`

**Where do the tokens live in the browser?**
The access token lives only in memory (a module variable), never in localStorage, so an XSS payload can't lift a long-lived credential. The refresh token is in an `HttpOnly; SameSite=Strict; Path=/api/v1/auth` cookie. On reload, the SPA calls `/auth/refresh` to get a new access token. Axios refreshes on `TOKEN_EXPIRED` with a single-flight promise, so ten parallel 401s cause one refresh.
→ `frontend/src/lib/session.ts`, `frontend/src/lib/api.ts`, `security/RefreshTokenCookies.java`

**CSRF is disabled, isn't that dangerous?**
The API authenticates with a bearer header, which browsers never attach automatically, so there's nothing to forge. The only cookie is the refresh token: SameSite=Strict (not sent cross-site) and path-scoped to the auth endpoints, and the response (a token) can't be read cross-origin because of CORS.

**How does lockout work, and why `noRollbackFor`?**
Wrong password → increment `failed_login_attempts`; at 5, set `locked_until` = now + 15 min and email the user. The exception thrown for the failed login would normally roll back the increment, so `login()` is `@Transactional(noRollbackFor = BankException.class)`. The user row is read `FOR UPDATE`, so parallel guesses can't race on the counter. A locked account doesn't check the password at all.
→ `service/auth/AuthService.login()`, `entity/User.registerFailedLogin()`

**Rate limiting vs lockout: why both?**
Lockout stops guessing one account's password. Bucket4j (a token bucket per endpoint and client IP) stops one client spraying many accounts. Buckets are in a size-bounded Caffeine cache so rotating IPs can't exhaust memory. The IP comes from `getRemoteAddr()`, which the container rewrites from `X-Forwarded-For` only for trusted proxies; the raw header is never trusted.
→ `security/AuthRateLimitFilter.java`

**How do you stop a customer reading someone else's account (IDOR)?**
Two patterns. `/me/...` endpoints never take a customer id; it comes from the token. Endpoints that take an account id use `@PreAuthorize("@accountSecurity.canView(#accountId)")`: staff yes, customers only if they own it. Transfers check ownership *before* locking, so probing someone else's id locks nothing.
→ `security/AccountSecurity.java`, `controller/AccountController.java`, test `customersSeeOnlyTheirOwnAccountsWhileStaffSeeAll`

**Password reset without account enumeration?**
`/password/forgot` always returns 202 with the same message, and hashes an OTP even for unknown emails so timing matches. The OTP is 6 digits, BCrypt-hashed, valid 10 minutes, 5 attempts, single use. A new request supersedes old codes. A successful reset unlocks the account and revokes every refresh token.

**Other hardening:** PAN and Aadhaar masked in every response; BCrypt cost 12; login takes the same time for unknown emails; typed config fails startup on a weak JWT key; CSV statements neutralise formula injection (`=HYPERLINK(...)` in a transfer remark); nginx sends CSP and frame-deny headers; the containers run as non-root.

---

## Cross-cutting design

**How does the audit log work?**
`@Audited(action = TRANSFER, entityId = "#request.fromAccountId", details = "...")` on a service method. `AuditAspect` is `@Order(0)`, so it wraps *outside* `@Transactional` (whose advisor has the lowest precedence). By the time it writes, the business transaction has committed or rolled back. It records SUCCESS or FAILURE (with the error code) in `REQUIRES_NEW`. The audit table has no FKs, so rows outlive what they describe and can never wait on a lock the caller holds.
→ `audit/AuditAspect.java`, `audit/Audited.java`

**Why send email after commit?**
If the email went out inside the transaction and the transaction then rolled back, the customer would be told about money that never moved. `@TransactionalEventListener(AFTER_COMMIT)` plus `@Async` fixes that, and a slow SMTP server never holds row locks. In-app notifications *are* written in the transaction, so they appear if and only if the change commits. A transactional outbox would add delivery guarantees.
→ `service/notification/EmailDispatcher.java`

**How is time handled?**
A single `Clock` bean zoned to Asia/Kolkata. Timestamps are `Instant` stored in UTC; business dates (`value_date`, due dates, maturity) are `LocalDate` in the bank's zone. Daily limits, statements and interest all use the value date. Tests can pin the clock.
→ `config/CoreConfig.java`, unit test `businessDateComesFromTheBankTimeZone`

**Error handling?**
Every failure, including security filter failures that never reach a controller, returns `{timestamp, status, errorCode, message, path, fieldErrors?}`. Clients branch on the stable `errorCode` (e.g. `TOKEN_EXPIRED` → refresh, `INVALID_TOKEN` → sign in). Messages are written for end users. 5xx responses never leak internals.
→ `exception/GlobalExceptionHandler.java`, `exception/ErrorCode.java`

---

## Banking maths

**EMI formula?** `EMI = P·r·(1+r)^n / ((1+r)^n − 1)`, with `r` = annual rate / 12 / 100, computed at 34 significant digits and rounded to paise at the end. The schedule charges interest on the outstanding principal each month; the last instalment absorbs the rounding residue, so the loan closes at exactly zero. Due dates are disbursal date + k months (Jan 31 → Feb 28 → Mar 31, no drift).
→ `service/calc/EmiCalculator.java` (₹5,00,000 at 10% for 60 months = ₹10,623.52)

**FD maturity?** Quarterly compounding, `A = P(1 + R/400)^q`, plus simple interest for leftover months. The rate is locked at booking. A daily job pays principal plus interest into the payout account and closes the FD account.
→ `service/calc/FixedDepositCalculator.java`, `service/FixedDepositService.processMaturities()`

**Savings interest?** Daily product: the sum of each day's closing balance × rate / 365. Closing balances come straight from the ledger's `balance_after` values (the last entry of each day). A deposit on the 21st earns 10 days of interest, not a month.
→ `service/calc/InterestCalculator.java`, `service/InterestService.java`

**What if the interest job runs twice, or on two servers?**
`UNIQUE(account_id, period)` in `interest_postings`, written in the same transaction as the credit. The second run hits the constraint and skips. The FD and EMI jobs lock the FD or instalment row first and re-check its status. Each item is its own transaction, so one bad account doesn't stop the batch. With several instances I'd add ShedLock to avoid the duplicate work, but correctness doesn't depend on it.

---

## Testing

**What's tested where?**
Unit tests (Mockito, no Spring) cover the maths, ledger rules, lock ordering, idempotency, JWT and rate limiting. Integration tests (`*IT`, Spring context + real MySQL 8.4 via Testcontainers, one container shared by all IT classes) cover real concurrency with a latch releasing up to 40 threads at once, the auth flows (lockout, rotation, reuse detection, OTP), authorisation, and the scheduled jobs end to end. `mvn test` runs only the fast suite; `mvn verify` adds the ITs (Maven failsafe).

**Why Testcontainers instead of H2?**
The bugs worth catching here are MySQL-specific: InnoDB row locks, isolation levels, unique-index blocking, CHECK constraints and Flyway SQL. H2 would pass tests that fail in production.

---

## Trade-offs I'd mention unprompted

- PAN and Aadhaar are masked but not encrypted at rest.
- Rate-limit buckets are per instance (use Redis for a cluster).
- Access tokens remain valid for up to 15 minutes after an account is disabled; the trade-off is no DB hit per request.
- External transfers settle instantly; real NEFT is batched with returns.
