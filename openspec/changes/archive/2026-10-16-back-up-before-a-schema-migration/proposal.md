# Proposal

## Why

AUD-DEP-16 (sprint 2, after AUD-DEP-01) reports that nothing in the product guarantees a backup exists before the schema
advances. Re-read on `develop` at `9277a083` on 2026-10-11, the finding **holds**:

- `SqliteSchemaManager.java:47-57`: `migrate` wraps the ledger creation, the refusal of a future schema and every
  migration in one `SqliteTransactionRunner.runVoid`, with no copy taken first. It is reached from one place,
  `SqliteStoreConnection.java:75`, on every open.
- The migration itself is safe — atomic, checksummed ledger, divergent history refused
  (`openspec/specs/sqlite-schema-history`). The gap is the way back: once the ledger is ahead, a program that has the
  check refuses the database (`SqliteSchemaManager.java:83-91`) — and the published 1.2.0, which predates that check,
  opens it and runs on a schema it does not know. Either way only a copy taken before the advance is a way back.
- `contracts/public-surfaces.tsv:54-56` exposes `server.backup.create`, `server.backup.verify` and `server.restore`, all
  manual. And the manual path has a trap the audit did not see: `SqliteServerMaintenance.createBackup` opens the store
  before copying (`:134-136`), so a backup requested from the new program is taken **after** the migration. An operator
  who installs, then remembers to back up, gets a copy the previous program refuses too.

`state-the-schema-upgrade-and-its-way-back` makes the manual backup mandatory in the procedure. A procedure covers the
operator who reads it. This change covers the others: the first start of a version that advances the schema leaves
behind a copy of the database as it was.

## What Changes

- Before applying a migration to a database that already records a schema version lower than the supported one, the
  store writes a verified copy of that database at its **original** schema version, named by that version, and only then
  migrates.
- If the copy cannot be written or does not verify, the store refuses to open, with a named reason, and the database is
  left at its original version. No silent degradation: a schema advance without a way back is not offered.
- A new database, and a database already at the supported version, are opened exactly as today: no copy, no extra file.
- The upgrade guide gains the sentence that describes this copy and where it is; the manual backup stays the documented
  first step.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `sqlite-schema-history`: adds what the store does before it advances the recorded schema of an existing database, and
  what it refuses when it cannot keep a copy.

## Impact

- `morpheus-store-sqlite`: `SqliteStoreConnection` (the only caller of `migrate`, and the only place that holds the
  database path), `SqliteServerMaintenance` (the copy and its verification already live there), tests.
- `docs/user/UPGRADE_1_2_1.md`, `SECURITY.md` or `docs/developer/OPERABILITY.md` (a new file appears beside the
  database; its permissions and retention are stated).
- An ADR (see `design.md`): the store starts writing a file nobody asked for, and can now refuse to start for a reason
  that is not about the database itself.
- No CLI command, MCP tool or HTTP route is added or changed. `store-memory` has no schema and no file: the persistence
  parity tests are unaffected, and task 4.2 confirms it.
