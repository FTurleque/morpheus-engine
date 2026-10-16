# Spec Delta

## Purpose

Add to the schema history capability what the SQLite store does before it advances the recorded schema of an existing
database, and what it refuses when it cannot keep a copy of that database as it was.

## ADDED Requirements

### Requirement: The store SHALL keep a verified copy of an existing database before advancing its schema

When the store opens a database whose ledger records at least one migration and whose highest recorded version is lower
than `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION`, it MUST write a copy of that database before applying any
migration, the copy MUST pass an integrity check and record the original version as its highest version, and its name
MUST carry that original version.

#### Scenario: An outdated database is copied, then migrated

- **WHEN** the store opens a database whose ledger stops at version 12 and the supported version is higher
- **THEN** a copy named for version 12 exists afterwards, `PRAGMA integrity_check` on it returns `ok`, its
  `MAX(version)` in `schema_migrations` is 12, and the opened database is at the supported version

#### Scenario: The copy holds the data as it was

- **WHEN** the copy written before the migration is opened read-only
- **THEN** its tables and row counts are those of the database before the migration, and none of the migrations applied
  afterwards is recorded in it

#### Scenario: A new database is not copied

- **WHEN** the store opens a path where no database exists, or a database without a ledger
- **THEN** it creates and migrates it as before and no copy is written

#### Scenario: An up-to-date database is not copied

- **WHEN** the store opens a database already at the supported version, a first and a second time
- **THEN** no copy is written and the directory listing is unchanged

#### Scenario: A database ahead of the program is still refused first

- **WHEN** the store opens a database whose ledger is ahead of the supported version
- **THEN** it refuses with the existing "is newer than supported" message and writes no copy

#### Scenario: A divergent history is still refused as a history mismatch

- **WHEN** the store opens an outdated database one of whose recorded migrations it does not accept
- **THEN** it refuses with the existing migration history mismatch naming the version, not with a backup failure, and
  the database is not migrated

### Requirement: The store SHALL refuse to advance the schema when the copy cannot be kept

If the copy cannot be written or fails its integrity check, the store MUST refuse to open the database with a failure
that names `PRE_MIGRATION_BACKUP_FAILED`, the original version and the supported version, MUST NOT put a file-system
location in that message, and MUST leave the ledger at the original version.

#### Scenario: A copy that cannot be written stops the migration

- **WHEN** the store opens an outdated database and the copy cannot be created
- **THEN** opening fails with `PRE_MIGRATION_BACKUP_FAILED`, the message names both versions and no path, and the
  database's `MAX(version)` is still the original version

#### Scenario: The refusal is repeatable

- **WHEN** the same open is attempted again without the cause being removed
- **THEN** it fails the same way and the database is still unchanged

#### Scenario: Once the cause is removed the upgrade completes

- **WHEN** the cause is removed and the store opens the database again
- **THEN** the copy is written, the database is migrated, and it opens

#### Scenario: A refusal raised inside a remote request discloses nothing

- **WHEN** the first open of an outdated database happens while serving a remote request and the copy cannot be written
- **THEN** the response carries no server path, and the database is not migrated

### Requirement: A pre-migration copy MUST NOT replace a file nor be mistaken for another database's copy

Each attempt to advance the schema MUST write its copy under a name no existing file holds, MUST NOT replace, rename or
delete a file it did not write during that attempt, and MUST discard its own copy when that copy does not record the
original version it is named for.

#### Scenario: A retry writes a new copy and leaves the first one alone

- **WHEN** a migration failed and rolled back after its copy was written, and the store opens the database again
- **THEN** a second copy exists beside the first, the bytes of the first are unchanged, and the migration is attempted
  again

#### Scenario: A copy from an earlier upgrade is never presented as this one

- **WHEN** a database was upgraded from version 12, then replaced offline by another backup at version 12, and the store
  opens it
- **THEN** a new copy is written from the database being opened, and the copy of the earlier upgrade is untouched

#### Scenario: A copy taken after someone else migrated is discarded

- **WHEN** another process migrates the database between this process's reading of the version and its copy
- **THEN** this process's copy, which records the supported version and not the original one, is deleted by this
  process, and the open succeeds on the migrated database

#### Scenario: Two processes opening at once leave only true copies

- **WHEN** two processes open the same outdated database concurrently
- **THEN** every copy that remains records the original version and passes its integrity check, at least one remains,
  and no process ends on a history mismatch
