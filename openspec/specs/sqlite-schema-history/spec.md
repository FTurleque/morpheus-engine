# sqlite-schema-history Specification

## Purpose
Define how the SQLite knowledge store identifies the schema migrations applied to a database, which recorded
migration histories it accepts when opening or restoring a database, and what it refuses.

## Requirements

### Requirement: Migration identity is independent of the build platform

The store SHALL compute the checksum of a migration from its text with line endings normalised, so that two builds
of the same MORPHEUS version produce the same checksum for the same migration whatever line endings their checkout
used.

#### Scenario: A database created by a Windows build opens with a Linux build

- **WHEN** a database whose migrations were applied by a Windows build of a version is opened by a Linux build of
  the same version
- **THEN** the store opens it without a migration history mismatch

#### Scenario: A database created by a Linux build opens with a Windows build

- **WHEN** a database whose migrations were applied by a Linux build of a version is opened by a Windows build of
  the same version
- **THEN** the store opens it without a migration history mismatch

#### Scenario: A changed migration is still refused

- **WHEN** the recorded checksum of an applied migration differs from the canonical checksum of that migration for
  a reason other than line endings
- **THEN** the store refuses to open the database with an explicit migration history mismatch naming the version

### Requirement: Recorded histories are never rewritten silently

The store SHALL accept a migration recorded with the checksum of the same text under either line-ending convention,
and MUST NOT rewrite the recorded checksum of an applied migration when it accepts it.

#### Scenario: A legacy Windows ledger is accepted as recorded

- **WHEN** a database records, for an applied migration, the checksum of its CRLF text
- **THEN** the store opens the database and the recorded checksum is unchanged afterwards

### Requirement: Released migrations are pinned

Every released migration SHALL have its canonical checksum pinned by the build, so that a change to the content of
a released migration fails the build.

#### Scenario: Editing a released migration fails the build

- **WHEN** one byte of a released migration other than a line ending is changed
- **THEN** the build fails and names the migration whose pinned checksum no longer matches

### Requirement: Offline restore verifies the ledger before replacing the database

An offline restore SHALL verify that every migration recorded in the backup is a migration the running version
knows, under the same acceptance rule as opening a database, before the live database is moved aside, and MUST
leave the live database in place when the verification fails.

#### Scenario: A backup with an incompatible ledger is refused

- **WHEN** an offline restore is confirmed for a backup whose ledger records a migration the running version does
  not accept
- **THEN** the restore is refused with an explicit reason and the live database is unchanged and still opens

#### Scenario: A backup from the other platform is restored

- **WHEN** an offline restore is confirmed for a backup created by a build of the same version on the other
  platform
- **THEN** the restore succeeds and the restored database opens

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
