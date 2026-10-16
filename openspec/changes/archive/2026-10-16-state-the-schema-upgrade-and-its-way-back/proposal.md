# Proposal

## Why

AUD-DEP-01 (sprint 2, the only High of the lot) reports that the unreleased 1.2.1 advances the SQLite schema and that no
document tells an operator how to upgrade or how to come back. Re-read on `develop` at `9277a083` on 2026-10-11, the
finding **holds, and is worse than written on one point**:

- `SqliteSchemaManager.java:45` declares `SUPPORTED_SCHEMA_VERSION = 20`; `:83-91` refuses a database whose ledger is
  ahead with `SQLite schema version <n> is newer than supported <m>`. `git tag -l` gives `v1.0.0`, `v1.1.0`, `v1.2.0`:
  every migration after V015 is unreleased.
- `docs/user/` holds `UPGRADE_1_1.md` and `UPGRADE_1_2.md` and no guide for 1.2.1. `UPGRADE_1_2.md:9`, `:15` and `:108`
  say the schema is "V015 inchangé" and that data "restent structurellement compatibles" on the way back to 1.1.0; `:24`
  makes the backup optional ("si une sauvegarde supplémentaire est souhaitée"). Those sentences are true of 1.2.0 and
  read as current.
- **Not in the audit.** The audit's remedy is "make `server backup create` mandatory before installing". Taken with the
  new binary, that backup is useless for a way back: `SqliteServerMaintenance.createBackup` opens the store first, "to
  ensure the live database has the current application schema before copying it" (`:134-136`), and opening migrates
  (`SqliteStoreConnection.java:75`). A backup created by 1.2.1 is already at the new schema, and the previous binary
  refuses it exactly as it refuses the live database. The backup that allows a way back is the one taken **with the
  installed version, before the new one is started once**. A guide that only says "back up first" reproduces the trap.

- **Found while implementing, and it overturns the audit's impact statement.** The refusal of a future schema does not
  exist in the published releases: it was added on 2026-08-12 (`fc2a3c07`, "reject future SQLite schemas"), after
  `v1.2.0` (2026-07-30). `git show v1.2.0:…/SqliteSchemaManager.java` applies the migrations it knows, finds them
  recorded, and opens. 1.2.0 therefore does **not** refuse a database migrated by 1.2.1: it starts and runs on a schema
  it does not know (a sequence table it does not maintain, triggers that may reject its writes, a conflict resolution
  value it cannot read). Only its backup verification refuses a newer schema
  (`v1.2.0:…/SqliteServerMaintenance.java:167`). Read from the 1.2.0 source; not executed.

An operator who installs 1.2.1, starts it once, and returns to 1.2.0 does not get the explicit refusal the audit
describes: they get a program that silently works on data it was never written for, and the only procedure in the tree
told them the schema was unchanged. This is the one finding of the audit whose effect cannot be undone
for a user, which is why it gates the tag.

## What Changes

- A new upgrade guide for the version being released, stating the schema version reached, the mandatory backup **taken
  with the previous version**, how to verify it, and the way back (restore that backup offline, then start the previous
  program).
- The earlier guides say which release they describe and point to the latest guide, so that "schema unchanged" stops
  reading as a statement about the current version.
- The guide that leaves 1.2.0 says that 1.2.0 does not refuse a migrated database and must never be started on one.
- A test ties the latest guide to the code: it must state the value of `SUPPORTED_SCHEMA_VERSION` and quote the refusal
  the program gives from this version on. Adding a migration without touching the guide fails the build, so the guide cannot go stale
  the way `UPGRADE_1_2.md` did.

## Capabilities

### New Capabilities

- `schema-upgrade-guidance`: what the operator documentation states about a schema advance — the version reached, the
  backup that makes a way back possible, the way back itself — and how that statement is kept equal to the code.

### Modified Capabilities

(none — `sqlite-schema-history` specifies what the store accepts and refuses; nothing in it changes here)

## Impact

- `docs/user/UPGRADE_1_2_1.md` (new), `docs/user/UPGRADE_1_2.md`, `docs/user/UPGRADE_1_1.md`, `docs/user/README.md`,
  `docs/user/release-notes/RELEASE_NOTES_1.2.1.md` (one pointer).
- One test in `morpheus-architecture-tests` (documentation coherence family).
- No CLI command, MCP tool, HTTP route or schema changes. No product code changes.
- Blocks the `v1.2.1` tag; unblocks AUD-DEP-12, AUD-DEP-16 (`back-up-before-a-schema-migration`) and AUD-TST-10.
