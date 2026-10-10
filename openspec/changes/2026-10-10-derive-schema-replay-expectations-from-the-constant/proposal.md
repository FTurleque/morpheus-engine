# Proposal

## Why

The 2026-10-09 audit (AUD-TST-11, sprint 1) found that the test which proves the SQLite migrations replay cleanly says
one thing in its name and another in its body, and restates the schema version as a literal where the repository's own
rule says a perishable number is read from its source. Re-read on `origin/develop` at `fe88c856` on 2026-10-10:

- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqliteSchemaMigrationTest.java:140` is named
  `migrationReplayIsIdempotentAndLedgerContainsEighteenImmutableEntries` and `:154` asserts
  `assertEquals(20, result.getInt("count"))`. The name says eighteen entries where the assertion requires twenty (the
  supported version read from `SqliteSchemaManager` on 2026-10-10); the name is the only documentation of a test.
- The same file restates the supported schema version as the literal `20` three times (`:39`, `:154`, `:194`), where
  `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION` (package-private, `SqliteSchemaManager.java:45`) is reachable from the
  same package. Two sibling tests already do it correctly: `SqliteMigrationChecksumGoldenTest.java:50` compares the
  number of canonical checksums with the constant, and `SqliteSyncStateRevisionMigrationTest.java:53` asserts the
  constant. Each migration therefore costs three edits in a test that has nothing to do with that migration.
- Checked and **left alone**: `SqliteCompositionResolutionMigrationTest.java:24` deletes `schema_migrations WHERE
  version = 20`, which names the migration under test, not the current version, and `R2UpgradeCompatibilityTest.java:74`
  asserts `12`, the baseline of a historical fixture. A literal that names a specific migration or a frozen baseline is
  correct; a literal that restates "the latest" is the defect.

The behaviour the test checks — a fully migrated database holds one ledger entry per supported version, each carrying a
SHA-256 checksum, and reopening it changes nothing — is not written in `openspec/specs/sqlite-schema-history`, which
specifies how checksums compare and what restore verifies, not the cardinality of the ledger.

## What Changes

- The replay test is renamed for the invariant it checks (`…LedgerContainsOneEntryPerSupportedMigration`) and its three
  literals become `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION`.
- The capability `sqlite-schema-history` gains the requirement that the ledger of a migrated database holds exactly one
  entry per supported version and that reopening it is a no-op.
- A guard refuses, in the store tests, an assertion whose expected value is the literal the constant has today, so the
  restatement cannot come back with the next migration.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `sqlite-schema-history`: adds the ledger-cardinality and idempotent-replay requirement and the rule that tests derive
  the expected version from the declaring constant.

## Impact

- `morpheus-store-sqlite` tests (`SqliteSchemaMigrationTest`); one textual rule in `morpheus-architecture-tests`.
- No production code, no migration, no checksum, no schema version moves; `contracts/public-surfaces.tsv` and
  `docs/openapi/` are untouched.
