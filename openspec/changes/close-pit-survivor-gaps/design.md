# Design

## Context

Each of the 169 mutations was read against the source PIT mutated (`origin/develop` at `3ec3ea46`), with its exact
description from the lot report, and given one verdict. The tables are in `qualification.md`; the counts:

| Class | Mutations | Missing test | Equivalent | Redundant | Unreachable | Defect |
|---|---|---|---|---|---|---|
| `MultiProviderCompositionService` | 32 | 28 | 0 | 4 | 0 | 0 |
| `QueryExecutionService` | 18 | 18 | 0 | 0 | 0 | 0 |
| `NormalizedProjectContent` | 25 | 25 | 0 | 0 | 0 | 0 |
| `SyntheticJsonParser` | 43 | 34 | 4 | 2 | 3 | 0 |
| `SqlitePolicyPackStore` | 31 | 29 | 2 | 0 | 0 | 0 |
| `SqlitePortfolioStore` | 20 | 12 | 7 | 0 | 0 | 1 |

What the missing tests have in common is the reason the audit saw survivors at all: a weak assertion lets many
mutations through at once.

- `MultiProviderCompositionDuplicationTest#everyPublishedEntityTypeIsObserved` checks that each type has *a* conflict;
  two providers sharing a key produce one conflict per field, so removing any single field observation is unseen,
  and its type list leaves out `CHANGE`. One field-set assertion per entity type kills 20 of the 28.
- The `rejects*` tests of `NormalizedProjectContentTest` assert the exception type only, against `rules/testing.md`;
  several reference checks share one message, so each needs its own case with its message (25 mutations).
- `SyntheticJsonParser`: a truncated document is refused by an `IllegalArgumentException`, but the mutants throw a
  `StringIndexOutOfBoundsException`, which both callers do not catch (they catch `IOException | IllegalArgumentException`),
  so a read would crash instead of answering `INVALID_SOURCE`; nothing asserts the refusal type (9 mutations).
- No test closes a policy store, re-activates a pack on a new version (the whole UPDATE branch of
  `compareAndSetActivation`), or calls the stores directly with a mismatched audit record.

## Defects

1. **Provider identifiers and the portfolio store** (`SqlitePortfolioStore.encodeProviders` / `decodeProviders`,
   `ProviderId`). Reachable through HTTP and MCP, whose `providers` argument is a comma-separated string. Revealed by
   the surviving mutant of the blank-item filter at line 477, which only matters because of this ambiguity.
2. **Policy audit order** (`SqlitePolicyPackStore.listAudit`, `ORDER BY at, id` over `Instant.toString()` text). Found
   beside the mutations; not revealed by one. Measured by task 1.3: the store port is out of order in SQLite only;
   the public audit is chronological in both stores because `PolicyPackService.audit` re-sorts by
   `AuditRecord.compareTo`, and loses write order only among records sharing one instant (random UUIDv7 bits). With
   the system clock, two audit writes sharing an instant to the nanosecond are not expected — each write is its own
   transaction — so the public effect needs a coarse or fixed clock.

## Open decisions

1. **Where a provider identifier is constrained.** (a) `ProviderId` refuses control characters — one rule for every
   provider identifier, at the domain boundary; (b) the portfolio entry points refuse them; (c) the store encodes the
   set as a JSON array — no new refusal, a migration of the column.
   **Decided on 2026-10-09: (a)**, recorded as an amendment of ADR-0023. (b) leaves a direct store call ambiguous; (c)
   migrates a column to accept identifiers no shipped provider produces. The blank-segment filter of
   `decodeProviders` goes with it: a stored empty segment is refused by name instead of being recomposed.
2. **How the audit order is restored.** (a) store `at` as fixed-width text (nanosecond precision, always nine
   digits) and order by it, with a migration of existing rows; (b) add an insertion sequence and order by it; (c)
   order by the parsed instant in Java after reading. Since task 1.3 a second question goes with it: whether records
   sharing one instant must keep write order (the spec says so) — which needs a write sequence in both stores and a
   change of `AuditRecord.compareTo` or of the service's sort — or only a stable order, which `(at, id)` already
   gives once the port sorts by instant.
   **Decided on 2026-10-09: (c), ties stable rather than in write order.** Both stores sort `listAudit` by
   `AuditRecord`'s natural order (instant, then identity) and the port says so. No migration: the stored text is
   unchanged and is parsed before sorting, so an existing database reads back in chronological order. A write
   sequence was set aside: it costs a migration and a change of `AuditRecord.compareTo` for a tie the system clock
   does not produce. `PolicyPackService.audit` keeps its own sort; it no longer changes the order the store returns.
3. **`MultiProviderCompositionResult.diagnostics()`**, filled by `distinctDiagnostics` and read by nothing in
   production or tests (4 redundant mutations): remove it, or expose and test it.
   **Decided on 2026-10-09: remove** the component and `distinctDiagnostics`. `CompositionSnapshotState` copies only
   the primary provider, the contributions and the conflicts, so no public surface serves it, and the diagnostics of
   an unavailable provider stay reachable through `contributions()`.
4. **The double whitespace skip of `SyntheticJsonParser`** (lines 65 and 335 against 76, 100, 102): remove one side,
   never both.
   **Decided on 2026-10-09: remove the skip inside `expect()` (line 335) and the one of `parseDocument` (line 65).**
   Each grammar position keeps one skip, written where the grammar allows whitespace, and `expect` matches exactly
   one character; the mutants of lines 100 and 102 become killable by the existing whitespace test.
5. **Composition of requirement deltas.** The composition publishes the deltas and evidence of every provider but
   observes neither for duplicates, while its comment says every published type is observed. Evidence is distinct per
   provider by construction; deltas are not. Either observe deltas, or correct the comment.
   **Decided on 2026-10-09: correct the comment.** `NormalizedProjectContent` refuses a delta whose change is not in
   the same content, so two providers contributing deltas to one change both publish that change, which is already
   observed (`CHANGE` and `IDENTITY` conflicts). Observing deltas would add `REQUIREMENT_DELTA` to
   `CompositionEntityType`, a public enumeration of `docs/openapi/morpheus-v1.yaml`, for a duplication already
   reported one level up. The comment states both exceptions and why.
6. **Store parity found beside the mutations** (not defects by themselves, but the persistence parity rule asks for
   one behaviour): removing a missing activation throws `IllegalArgumentException("policy activation does not exist")`
   in memory and removing a missing override `EntityNotFoundException`, where SQLite throws `PolicyConflictException`
   for both (pinned by `SqlitePolicyPackStoreAtomicityTest`); memory checks the revision before the audit and SQLite
   the audit first; a closed SQLite store throws `IllegalStateException` in three stores (`SqlitePolicyPackStore`,
   `SqliteSavedViewStore`, `SqliteCompositionStateStore`) and `KnowledgeStoreException` in nine.
   **Decided on 2026-10-09: spun off** to https://github.com/FTurleque/morpheus-engine/issues/416.

## Non-Goals

Changing the PIT configuration; writing tests for the equivalent and unreachable mutations (their reasons are in
`qualification.md`).
