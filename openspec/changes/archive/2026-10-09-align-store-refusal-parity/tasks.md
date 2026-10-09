# Tasks

Every refusal test asserts the exception type **and** the message (`rules/testing.md`), and is seen failing before
its fix.

## 1. Reproduce the divergences

- [x] 1.1 In `m25/PolicyPersistenceParityTest`, remove a missing activation and a missing override directly from both stores with a well-formed audit; assert `EntityNotFoundException` and the same message in both; verify it fails today (memory `IllegalArgumentException` for the activation, SQLite `PolicyConflictException` for both)
- [x] 1.2 In the same test, remove a missing row with a mismatched audit target; assert both refuse it as `policy audit target mismatch for <action>`; verify memory fails today (it checks existence first)
- [x] 1.3 In the same test, replace a pack definition with a revision two steps ahead; assert both refuse it with `policy update must advance revision and version by exactly one` and leave definition, versions and audit unchanged; verify SQLite fails today (trigger `KnowledgeStoreException`)
- [x] 1.4 Closed-store tests for `SqliteSavedViewStore` and `SqliteCompositionStateStore` covering every public operation, asserting `KnowledgeStoreException`, the store's message and no cause; verify they fail today (`IllegalStateException`)

## 2. Policy stores

- [x] 2.1 `MemoryPolicyPackStore`: missing activation refused with `EntityNotFoundException("policy activation does not exist: <pack>")`; every write checks the audit target before existence and revision; verify 1.1 and 1.2 pass for memory
- [x] 2.2 `SqlitePolicyPackStore`: removals look the row up after the audit target check and refuse a missing one with the same `EntityNotFoundException`s; verify 1.1 passes for SQLite
- [x] 2.3 `SqlitePolicyPackStore`: check the revision step of `compareAndSetDefinition` explicitly, with the memory store's message, before the transaction; keep the V018 trigger; verify 1.3 passes
- [x] 2.4 Update `SqlitePolicyPackStoreAtomicityTest` where it pins the old refusals (missing activation, closed store); verify it passes with the new contract and say in the test why the expectation changed

## 3. Closed SQLite stores

- [x] 3.1 `SqlitePolicyPackStore`, `SqliteSavedViewStore`, `SqliteCompositionStateStore`: throw `KnowledgeStoreException("SQLite <name> store is closed")` with no cause; verify 1.4 and the policy store's closed-store test pass
- [x] 3.2 Add one test asserting that every SQLite store refuses an operation after `close()` with the same type and no cause, so a thirteenth store cannot diverge; verify it fails if one of the three is reverted

## 4. Verify and record

- [x] 4.1 Run `./mvnw clean verify` and the persistence parity tests on both platforms (CI for Windows); verify every ratchet holds
- [x] 4.2 Close issue #416 with a link to the merged change
