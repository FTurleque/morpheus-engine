# Proposal

## Why

The SQLite migration ledger records the SHA-256 of each migration script as raw bytes, and the scripts are
checked out with platform line endings (`* text=auto`, no rule for `*.sql`). A database created by a Windows build
is therefore refused by a Linux build of the same version ("SQLite migration history mismatch for version 1"),
and an offline restore of a backup from the other platform passes verification, replaces the live database and
discards it. Measured on 2026-10-08 at `3ec3ea46`: `V001__foundation.sql` hashes to `eeb86cf3…` as the LF git blob
and to `a4342864…` in the CRLF copy shipped inside `dist/morpheus-1.2.1-windows-x64.zip` (audit finding
STO-AUD-1, `docs/audits/AUDIT_OUTILLE_2026-10-08.md`).

## What Changes

- The migration checksum becomes a property of the migration's text, not of the checkout: line endings are
  normalised before hashing.
- Databases already recorded with the platform-specific digest keep opening, without their ledger being rewritten.
- `*.sql` resources are checked out with LF on every platform.
- A golden test pins the canonical checksum of every migration, so a byte change in a released migration fails
  the build instead of failing on a user's database.
- Offline restore verifies the backup's migration ledger against the migrations the runtime knows **before** the
  live database is quarantined and discarded; an incompatible ledger is refused and the live database is left intact.
- ADR-0021 is amended: "the checksum is the SHA-256 of the applied script" gains its normalisation rule.

## Capabilities

### New Capabilities

- `sqlite-schema-history`: how the SQLite store identifies applied migrations, which recorded histories it accepts,
  and what an offline restore verifies before replacing the live database.

### Modified Capabilities

(none — `openspec/specs/` holds no capability yet)

## Impact

- `morpheus-store-sqlite`: `SqliteSchemaManager` (checksum and ledger comparison), `SqliteServerMaintenance`
  (`verify` and `restoreOffline`), their tests (`SqliteSchemaMigrationTest`, `R2UpgradeCompatibilityTest`,
  `SqliteServerMaintenanceTest`, `SqliteRestoreQuarantineOrderingTest`).
- `.gitattributes`.
- `docs/adr/0021-*.md` (amendment), `SECURITY.md` / operator documentation for restore.
- Existing user databases: none rewritten; Windows-created databases become readable by Linux builds.
