# Proposal

## Why

The two policy pack stores refuse the same invalid write in different ways, and closed SQLite stores throw two
different exception types (issue #416, found while qualifying PIT-AUD-5). The persistence parity rule asks memory and
SQLite to behave identically, and a caller cannot handle "store closed" one way: `MorpheusCompositionMcpTools` catches
`IllegalArgumentException | KnowledgeStoreException` only, so an `IllegalStateException` from a closed
`SqliteCompositionStateStore` would escape its mapping. No production path reaches either divergence today —
`PolicyPackService` checks existence before calling the store, and every transport opens a store per request — so the
change fixes a contract callers cannot rely on, before one does.

## What Changes

- Removing a missing activation or override is refused by both policy stores with an `EntityNotFoundException` that
  names the row. Today memory throws `IllegalArgumentException` (activation) or `EntityNotFoundException` (override),
  and SQLite throws `PolicyConflictException` ("stale … current is 0") for both.
- Both policy stores check a write in one order: the audit's target (action, pack, rule, scope) first, then the stored
  state (existence, then revision), then the audit's version, which needs the stored row. Today memory checks the
  revision before the audit, SQLite the audit first.
- A compare-and-set write whose replacement skips a revision step is refused by both stores with the same
  `IllegalArgumentException` before anything is written. Today memory refuses it explicitly and SQLite only through the
  V018 trigger, as a `KnowledgeStoreException`; the trigger stays, as an integrity backstop.
- Every SQLite store refuses an operation after `close()` with a `KnowledgeStoreException` naming the store, with no
  cause. `SqlitePolicyPackStore`, `SqliteSavedViewStore` and `SqliteCompositionStateStore` throw
  `IllegalStateException` today; the nine others already throw `KnowledgeStoreException`.

## Capabilities

### New Capabilities

- `policy-store-refusals`: how a policy pack store refuses an invalid write — a missing row, a stale or skipped
  revision, a mismatched audit — identically in memory and in SQLite.
- `store-closed-refusal`: what a SQLite store does when used after it was closed.

### Modified Capabilities

(none — `policy-audit-order` is about the order of the audit a store returns, which this change does not touch)

## Impact

- `morpheus-store-memory`: `MemoryPolicyPackStore` (refusal types and check order).
- `morpheus-store-sqlite`: `SqlitePolicyPackStore` (refusals, order, explicit revision-step checks),
  `SqliteSavedViewStore`, `SqliteCompositionStateStore` (closed-store type).
- Tests: `SqlitePolicyPackStoreAtomicityTest` pins today's SQLite refusals and the `IllegalStateException` of a closed
  policy store, and changes deliberately; `m25/PolicyPersistenceParityTest` gains the refusal cases; closed-store tests
  for the two other stores.
- No public surface changes: `PolicyPackService` already refuses these cases before reaching a store, and the HTTP and
  MCP handlers of these stores already catch `KnowledgeStoreException`.
