# Spec Delta

## Purpose

Define how the SQLite knowledge store identifies the schema migrations applied to a database, which recorded
migration histories it accepts when opening or restoring a database, and what it refuses.

## ADDED Requirements

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
