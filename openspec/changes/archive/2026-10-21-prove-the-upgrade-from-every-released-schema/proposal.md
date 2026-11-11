# Proposal

## Why

AUD-TST-10 (sprint 8) reports that no test upgrades a real database from the schema levels 13 to 19, and that the only
true upgrade test starts at level 12. Re-read on `develop` at `8b26d3d7` on 2026-10-13, the finding **holds, and its
description of what is at risk is wrong on two points**:

- `R2UpgradeCompatibilityTest.java:24-36` builds a database with migrations 1 to 12 only, inserts one project and one
  snapshot, and opens it with the current store. It is still the only test that starts from a database built to an
  older level. `SqliteMigrationAtomicityTest.java:45` also stops at 12 (`applyThroughVersion12`).
- The other migration tests start from the **current** schema and remove one ledger row:
  `SqliteSchemaMigrationTest.java:193` (16), `SqliteSpecificationVersionSequenceMigrationTest.java:29` (17),
  `SqliteSyncStateRevisionMigrationTest.java:34` (19), `SqliteCompositionResolutionMigrationTest.java:24` (20). The
  tables already carry their final form when the migration is replayed.
- **The levels the audit names are not the levels that were released.** `git ls-tree` on the three tags gives the last
  migration each release shipped: `v1.0.0` stops at V012, `v1.1.0` and `v1.2.0` both stop at V015. No published program
  ever wrote a database at level 13, 14, 16, 17, 18 or 19. The audit's "1.1.x (levels 13 to 18)" does not exist: the
  databases in operators' hands are at 12 or at 15, and the untested path is exactly one — **15 to current**, which is
  the upgrade every 1.1.0 and 1.2.0 installation will take.
- **The audit says V010 is the only migration that alters a table.** `V019__sync_state_revision.sql:3` is
  `ALTER TABLE sync_state ADD COLUMN revision`, and the migrations after V015 are not additive: V016 rewrites
  `specification_versions.sequence` on existing rows before adding a unique index, V017 seeds a new table from existing
  rows, V018 puts triggers on `policy_packs`, V020 rewrites a value of `composition_conflict`. Each of them acts on rows
  a 1.2.0 database can hold, and none is exercised on a database that was at level 15.

`state-the-schema-upgrade-and-its-way-back` tells the operator how to upgrade and names this finding as the test it
does not write; `back-up-before-a-schema-migration` copies the database before migrating. Neither proves that the
migration of a released database keeps what it held.

## What Changes

- The upgrade test is run once per released schema level, not once for 1.0.0: a database built with exactly the
  migrations a release shipped, holding rows in every table a later migration reads or rewrites, is opened by the
  current store and its content is read back through the store.
- The list of released levels is declared once, and the build fails when a released version has no declared level.
- The single-migration tests that replay a migration on a current schema are kept: they cover what a baseline cannot
  (a duplicate sequence, a legacy value), and this change does not rewrite them.

## Capabilities

### New Capabilities

- `released-schema-upgrade`: the proof that a database written by each released version is migrated by the current
  store without losing identity, history or the ability to be written.

### Modified Capabilities

(none — `sqlite-schema-history` says which ledgers the store accepts; nothing in it changes here)

## Impact

- `morpheus-store-sqlite` tests only: `R2UpgradeCompatibilityTest` generalised or replaced; one declaration of the
  released levels. No product code, no migration, no public surface.
- Presence ratchets may rise with the tests added; they are raised from a measurement on both platforms, never from a
  count of methods (`live-numbers`, `coverage-ratchet`).
- No ADR: no decision of structure; ADR-0018 (SQLite) and the capability `sqlite-schema-history` stand as they are.
- `docs/developer/BUILD_AND_TEST.md` or the store's developer page gains one sentence naming the test as the place where
  a release adds its level; the release checklist (`docs/project/validation/RELEASE_QUALIFICATION.md`) names the step.
- Follows `state-the-schema-upgrade-and-its-way-back` (AUD-DEP-01) and `back-up-before-a-schema-migration`
  (AUD-DEP-16); independent of the other changes of sprint 8.
