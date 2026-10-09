# Design

## Context

`SqliteSchemaManager.applyMigration` reads each `db/migration/V0NN__*.sql` resource as UTF-8 and compares
`sha256(script)` with the `checksum` column of `schema_migrations`; any difference throws
`KnowledgeStoreException("SQLite migration history mismatch for version N")`. `.gitattributes` declares
`* text=auto` and fixes line endings only for `*.sh`, `mvnw`, `*.ps1`: Windows checkouts (and the `windows-latest`
release job) package CRLF scripts, Linux checkouts LF ones. `SqliteServerMaintenance.verify` checks
`integrity_check` and `MAX(version)` only; `restoreOffline` then quarantines, replaces and `discard()`s the live
database. `R2UpgradeCompatibilityTest` recomputes expected checksums from the same resources, so no test sees the
platform dependency. See proposal.md for the measured digests.

## Goals / Non-Goals

**Goals:**
- One canonical checksum per migration text, whatever the checkout.
- Every database written by a released 1.x build keeps opening, on both platforms.
- A restore never discards the live database for a backup the runtime would refuse to open.

**Non-Goals:**
- Rewriting existing ledgers to the canonical digest (ADR-0021: history is never rewritten silently).
- Detecting tampering beyond the content of the migration (the ledger is not a security boundary).

## Decisions

1. **Normalise `\r\n` to `\n` before hashing** rather than relying on `*.sql text eol=lf` alone. The attribute fixes
   future builds, but databases created by 1.0–1.2.1 Windows builds already hold CRLF digests, and a developer
   checkout with `core.autocrlf` settings or a renormalisation gap would reintroduce the split. Alternative rejected:
   hashing the bytes as stored after forcing LF — it breaks every existing Windows database.
2. **Accept exactly two digests per migration**: the canonical (LF) one, and the digest of the same text with
   CRLF endings. Nothing else is accepted, so a content change is still refused. Alternative rejected: an allowlist of
   historical digests per release — larger, and must be maintained for every future release.
3. **Pin canonical digests as literals in a golden test** in `morpheus-store-sqlite`, so the build fails on a content
   change. The current tests derive expectations from the resources and therefore cannot fail.
4. **Restore compares the backup ledger with the runtime manifest inside `verify`** (version, name, accepted
   digest), before `Quarantine.capture`. Running the check after replacement would be too late: `discard()` has
   already deleted the previous database.
5. **`.gitattributes` gains `*.sql text eol=lf`**, plus a `git add --renormalize` check, so packaged resources stop
   differing between platforms.

## Risks / Trade-offs

- [Accepting the CRLF digest also accepts a CRLF-only edit of a migration] → such an edit cannot change SQL
  semantics; documented in the ADR-0021 amendment.
- [A platform build already in users' hands keeps writing the platform digest until upgraded] → both digests are
  accepted, so mixed populations open.
- [Linux behaviour is inferred, not yet measured on a Linux-built artifact] → task 5.2 measures it.

## Migration Plan

No data migration. Ship the reader change first (accepts both digests); the `.gitattributes` change can land in the
same release. Rollback: a build released before the fix (v1.2.0 and earlier, 15 migrations at most) already refuses
a schema-20 database as newer, so no released build loses access; a pre-fix *development* build of the same schema
version would refuse, on Windows, a database created after the fix (inferred from the checksum rule, not run).
