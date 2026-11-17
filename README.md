# payment-demo-tdd

A money transfer service between accounts, written test-first with JUnit 5. It is concurrent, idempotent and deadlock-free.

Plain Java, no framework: the goal is the domain rules and the concurrency, not the plumbing.

## Run

```bash
mvn test
```

## Rules (one test each)

1. **Normal transfer.** A=100, B=50, A→B 30 → A=70, B=80, `SUCCESS`.
2. **Insufficient balance.** The transfer is `REJECTED` and neither account changes. Transferring the exact balance is accepted (boundary test).
3. **Idempotency.** A retry with the same `transferId` does not move money again; it returns the stored `SUCCESS`.
4. **Same account (A→A).** Throws `IllegalArgumentException`, checked before idempotency and locking.
5. **Concurrency.** 100 threads transfer A↔B and C↔D at the same time. The test finishes in time and the total amount of money stays the same.
6. **Concurrent idempotency.** 100 threads send the same `transferId`. Money moves exactly once.

## Design decisions

| Topic | Decision | Why |
|---|---|---|
| Money type | `BigDecimal` built from strings, compared with `compareTo` | `double` cannot represent money exactly; `equals` also compares scale (`10.0` ≠ `10.00`) |
| Domain model | `Money` and `Balance` are immutable value objects; "balance can't go negative" lives in `Balance` | One place for the rule; a caller that skips the service can't break it |
| Exceptions | Bugs → JDK exceptions (`IllegalArgumentException`); business rejection → `InsufficientBalanceException` → `REJECTED` | The type says whether it is a bug or an expected outcome |
| Idempotency | The client sends `transferId`; the check happens before any side effect | A retry after a timeout must not move money twice |
| Deadlock | One lock per account, taken in a fixed order (smaller ID first) | Breaks circular wait. Removing the ordering made the test deadlock every time |
| Concurrent idempotency | `get → transfer → save` runs inside the account locks | Otherwise two threads both see "not processed" and both withdraw |
| Shared map | `ConcurrentHashMap` | `HashMap` broke under concurrent writes in the test (`HashMap$Node cannot be cast to HashMap$TreeNode`) |

## Known limits / next steps

- If the same `transferId` arrives with a different account pair, different locks are taken. A real system needs an atomic reservation of the ID (a database unique constraint, Redis `SETNX`, or `putIfAbsent` with a `PENDING` state).
- `synchronized` can't time out. A `ReentrantLock.tryLock(timeout)` version is the next step.
- Same `transferId` with a different payload should be an error.
- Currency checks are out of scope.

My detailed learning notes (in Turkish) are in [NOTES.md](NOTES.md).
