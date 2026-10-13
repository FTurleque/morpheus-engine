# Spec Delta

## Purpose

Define the proof that a database written by a released version of MORPHEUS is brought to the current schema without
losing what it held, and how the list of released schema levels is kept complete.

## ADDED Requirements

### Requirement: The upgrade from every released schema level SHALL be executed on a database that holds data

For each schema level a released version shipped, a test MUST build a database with exactly the migrations of that
release, recorded with their pinned checksums, insert rows in every table that a later migration reads or rewrites,
open it with the current store, and assert that the ledger reaches `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION`, that
the checksums recorded before the upgrade are unchanged, and that a second opening changes nothing.

#### Scenario: A database of the last published release is upgraded

- **WHEN** a database built with the migrations of the most recent published release, holding a project, a snapshot,
  specification versions, a synchronization state, a policy pack with a version and a composition conflict, is opened by
  the current store
- **THEN** the store opens, the ledger holds one row per supported version, and every row inserted before the upgrade is
  found again under the same identity

#### Scenario: The oldest published release is still covered

- **WHEN** the same test runs from the level of the first published release
- **THEN** it passes under the same assertions, restricted to the tables that level has

#### Scenario: A migration that fails on existing rows fails the test

- **WHEN** a migration after the baseline's level is edited so that it fails or drops a row on a table that holds data
- **THEN** the upgrade test of that baseline fails, and the single-migration test of that migration is not the only one
  to notice

#### Scenario: The baseline does not follow an edited migration

- **WHEN** the text of a released migration is changed
- **THEN** the baseline's ledger still carries the pinned checksum, and the store refuses the database with a migration
  history mismatch instead of the test adapting to the change

### Requirement: What an upgraded database held SHALL be read back through the store

The assertions that follow an upgrade MUST read the pre-existing rows through the public operations of the stores that
own them, and MUST NOT rely only on SQL queries against the migrated tables.

#### Scenario: A synchronization state written before the revision column existed

- **WHEN** a synchronization state row exists in a database upgraded from a level that had no revision
- **THEN** the current store returns that state with the revision an existing row starts at, and a write that states
  that revision succeeds

#### Scenario: Specification versions written before sequences were unique

- **WHEN** specification versions exist in the upgraded database
- **THEN** the store lists them in their sequence order and the next published version receives the next sequence

### Requirement: An upgraded database SHALL still accept the writes its family allows

After the upgrade, at least one write through the store MUST succeed for each family whose tables a later migration
constrains with an index or a trigger.

#### Scenario: A policy pack written by an earlier release is updated

- **WHEN** a policy pack inserted at the baseline's level is updated through the policy store with the revision it holds
- **THEN** the update succeeds and the pack's revision advances by one

### Requirement: Every released version SHALL declare the schema level it shipped

The released schema levels MUST be declared in one place, and the build MUST fail when a version that has release notes
under `docs/user/release-notes/` and is lower than the current product version has no declared level, or when the
declaration no longer names the first published release.

#### Scenario: The version after a release must declare what was shipped

- **WHEN** the product version advances past a version whose release notes exist and no level is declared for it
- **THEN** the build fails and names the version without a level

#### Scenario: The version in progress is not required

- **WHEN** release notes exist for the current product version
- **THEN** the build does not require a level for it

#### Scenario: A declared level beyond the supported schema is refused

- **WHEN** a declared level is greater than `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION` or has no pinned checksum
- **THEN** the build fails and names the version and the level
