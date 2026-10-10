# Spec Delta

## Purpose

Add to the SQLite schema-history capability the cardinality of the migration ledger and the idempotence of replaying
migrations, which the capability did not state, and the rule that the tests which check them derive the expected version
from the constant that declares it.

## ADDED Requirements

### Requirement: The ledger of a migrated database holds one entry per supported version

After the store has applied its migrations, `schema_migrations` SHALL hold exactly one row for each version from 1 to
`SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION`, each carrying a SHA-256 checksum of 64 hexadecimal digits, and opening the
database again MUST NOT add, remove or duplicate a row.

#### Scenario: A new database records every supported migration once

- **WHEN** a store is opened on a new database file and closed
- **THEN** `SELECT COUNT(*) FROM schema_migrations` equals `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION`, every
  `checksum` is 64 hexadecimal digits, and `currentVersion` equals the same constant

#### Scenario: Reopening is a no-op

- **WHEN** the same database file is opened and closed a second time
- **THEN** the row count and the set of versions in `schema_migrations`, captured after each opening, are identical

### Requirement: Store tests derive the expected schema version from the declaring constant

A test of `morpheus-store-sqlite` SHALL NOT compare the schema version read from a database, or the ledger count, with an
integer literal; it MUST read `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION`, except where a test is listed by name, with a
reason, as asserting a frozen historical baseline.

#### Scenario: The replay test names the invariant and reads the constant

- **WHEN** `SqliteSchemaMigrationTest` is read after the change
- **THEN** the replay method is named `…LedgerContainsOneEntryPerSupportedMigration`, and its three former literals
  (`currentVersion` twice and the ledger count once) are the constant

#### Scenario: A restated version is refused

- **WHEN** a test under `morpheus-store-sqlite/src/test/java` calls `assertEquals` with an integer literal as expected
  value and `currentVersion(` or the ledger count as actual value
- **THEN** the architecture suite fails and names the file and the line, whatever the literal is, and the rule contains no
  copy of the constant's value

#### Scenario: A frozen historical baseline is excluded by name

- **WHEN** `R2UpgradeCompatibilityTest` asserts the schema version of its frozen fixture against a literal
- **THEN** the guard does not report it, because the test is listed with the reason "frozen historical baseline", and an
  entry without a reason makes the suite fail

#### Scenario: An unrelated assertion is not looked at

- **WHEN** a store test asserts `assertEquals(<any integer>, list.size())`
- **THEN** the guard reports nothing, whatever the integer is

#### Scenario: Adding a migration needs no edit of the replay test

- **WHEN** a migration is added and `SUPPORTED_SCHEMA_VERSION` is raised by one
- **THEN** `SqliteSchemaMigrationTest` compiles and its replay and version assertions hold without being edited
