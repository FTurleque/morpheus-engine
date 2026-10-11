# Design

## Context

`SqliteSchemaMigrationTest` is the only test that opens a database twice and reads the whole `schema_migrations` table.
Its three version literals and its name drifted at V019 and V020 because the file holds no reference to the constant that
moves. `SUPPORTED_SCHEMA_VERSION` is the number of the last migration and the ledger holds one row per version from 1 to
that number, so the expected count equals the constant while the migrations stay contiguous.

## Goals / Non-Goals

**Goals**: a test name that states the invariant; no restatement of the current version in the store tests; the
restatement cannot return unnoticed.

**Non-Goals**: touching the production schema code; changing any migration; "fixing" the literals that name a specific
migration or a frozen historical baseline (`version = 20` in `SqliteCompositionResolutionMigrationTest`, `12` in
`R2UpgradeCompatibilityTest`).

## Decisions

1. **Name the invariant, not a number.** `…LedgerContainsOneEntryPerSupportedMigration`, as the audit suggests. A name
   that carries a number is a second copy of it.
2. **Derive the expected count from `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION`**, the constant the golden-checksum test
   already compares with. The equality "count == constant" holds because versions are contiguous from 1; if a version were
   ever skipped, the golden test (`CANONICAL_CHECKSUMS.size()` against the constant) is the one that says so, and this
   test would fail for the same reason, which is correct.
3. **A textual guard stated on the operation, not on a value.** Review pointed out that a guard keyed on the constant's
   value becomes a time bomb: after the next migration the value is 21 and any unrelated `assertEquals(21, list.size())`
   would be refused, while the sibling change `make-gate-verdicts-follow-living-sources` states its rule "on the operation,
   not on a value". The rule therefore refuses, in `morpheus-store-sqlite/src/test/java`, an `assertEquals` whose first
   argument is an integer literal and whose second argument is the schema version read from a database
   (`...currentVersion(`) or the ledger count (`getInt("count")` of the replay query). An assertion about anything else is
   not looked at, whatever its number. Legitimate exceptions are listed by name with a reason, the pattern
   `public-surface-convergence` uses: `R2UpgradeCompatibilityTest` asserts `currentVersion` equal to the frozen baseline of
   its fixture (`:74`), which is the first entry. It is a text rule by nature (it reads sources; `enforcement-choice`).
   Its limits are written down: it does not see `assertThat`, a long literal (`20L`) or an argument split across lines; it
   is a net for the common shape, and the sibling tests that derive from the constant remain the model.
   Rejected: *no guard, rename and replace only* — the audit's action, and cheaper, but it repeats the situation that
   produced the defect: three edits per migration, each easy to forget, and a name that can go stale again.
   Rejected: *reflection on the constant from the architecture suite* — the constant is package-private in another
   module, the repository keeps reflection out of its runtime (`rules/architecture.md`), and reading the source gives the
   same information without it.
4. **The requirement is stated as store behaviour, not as test hygiene.** The testable fact is the ledger's cardinality
   and the no-op replay; the derivation rule is its verification, expressed as a second requirement because that is the
   part the audit found missing.

## Verification of the proposed guard (broken once)

On the working tree of 2026-10-10 the store tests were searched for `assertEquals(<integer literal>, ...currentVersion(` and
for the ledger count:

```text
before the change   SqliteSchemaMigrationTest.java lines 39, 154, 194 (literal 20)    -> the rule fails
                    R2UpgradeCompatibilityTest.java line 74 (literal 12)               -> excluded, frozen baseline
after the change    only the excluded baseline                                         -> the rule passes
```

(The first three lines are the ones the audit names; the search was run, not inferred. `SqliteCompositionResolutionMigrationTest`
does not match: its `20` is inside an SQL string, not an `assertEquals`.)

## Non-duplication check

Done by hand against the four requirements already in `sqlite-schema-history` and the seven other capabilities.
"Migration identity is independent of the build platform" compares checksums across line endings; "Recorded histories are
never rewritten silently" forbids rewriting a recorded checksum when accepting a variant; "Released migrations are pinned"
fails the build when a released migration changes; "Offline restore verifies the ledger…" concerns restore. The two ADDED
requirements — one ledger row per supported version with an idempotent replay, and tests deriving the expected version from
the constant — say none of that: the first is about the ledger's cardinality (no row added, removed or duplicated), the
second about how tests state the version.

## Supervision objections and their treatment

Two read-only reviewers read the four changes on 2026-10-10: the `architect` agent (layer rules, existing tests, feasibility) and the `contract-guardian` agent (public surface, ADR need). Neither found a public surface touched, a layer rule violated, or a duplicate of the eight existing capabilities. Their objections and what became of each:


| # | Objection (reviewer) | Treatment |
|---|---|---|
| 1 | A guard keyed on the constant's value is a time bomb and inconsistent with the sibling change (architect) | **Accepted.** Guard stated on the operation (decision 3), exclusion list with reasons |
| 2 | Scenario "historical baseline is not refused" is vacuous while the constant is 20 (architect) | **Accepted.** Replaced by "excluded by name with a reason" and "unrelated assertion not looked at" |
| 3 | The replay test does not compare the version set across openings and checks only the checksum length (architect) | **Accepted.** Task 2.1 strengthens the test |
| 4 | The second requirement is repository hygiene in a product spec (both) | **Kept, and said so.** It is the verification contract of the first requirement (decision 4); it blocks nothing and can be struck without touching the rest |
| 5 | The guard misses `assertThat`, `20L`, wrapped lines (convergence) | **Accepted as a stated limit** (decision 3) |


## Risks / Trade-offs

- [The guard flags a frozen baseline] -> listed by name with its reason; one entry today.
- [Count equals constant only while versions are contiguous] → covered by `SqliteMigrationChecksumGoldenTest`, which fails
  first if a version is skipped.

## ADR

None: a test-hygiene and specification change with no structural decision. The numbering was read from `docs/adr/`
(109 records, highest `0109`); nothing is attributed.

## Open Questions

(none)
