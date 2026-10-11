# Design

## Context

- The store migrates on every open (`SqliteStoreConnection.java:75` → `SqliteSchemaManager.migrate`), inside one
  transaction, and refuses a ledger ahead of `SUPPORTED_SCHEMA_VERSION` before applying anything.
- `server backup create` is the only supported copy (`VACUUM INTO`, then `integrity_check` and a schema check). It opens
  the store before copying, so it migrates the database it is about to copy.
- `server restore --confirm` is offline only and verifies the backup's ledger under the same acceptance rule as opening
  (`openspec/specs/sqlite-schema-history`, "Offline restore verifies the ledger before replacing the database").
- 1.2.0 shipped with schema V015 (`UPGRADE_1_2.md:15`, a dated statement about a published release). The value reached
  by the version being released is whatever the constant says on the tagged commit; it is 20 today.

## Goals / Non-Goals

**Goals**: an operator reading the documentation before upgrading knows the schema advances, takes a backup that the
previous program can still open, and knows the way back; the guide cannot silently diverge from the constant.

**Non-Goals**: taking that backup automatically (`back-up-before-a-schema-migration`, AUD-DEP-16); an upgrade test from
real databases at each schema level (AUD-TST-10, sprint 8); a downgrade migration — the store has none and this change
adds none; rewriting the dated content of older guides.

## Decisions

1. **The guide states the number, and a test compares it to the constant.** The audit asks for a value "read from
   `SqliteSchemaManager`, never copied". A Markdown page cannot read a constant; the only way to honour the intent is to
   let the page state it and make the build fail when the two differ. *Rejected*: a guide that avoids the number
   ("the schema advances") — the operator needs the number to recognise the refusal message and to check a backup with
   `server backup verify`. *Rejected*: generating the guide at build time — a generated user page is not reviewed, and
   the distribution does not ship `docs/`.

2. **The rule targets the latest guide, not "a guide per version".** The test selects the `docs/user/UPGRADE_*.md` with
   the highest version in its name and requires it to state the constant. A patch release without a migration needs no
   new guide; a release with a migration fails the build until the latest guide is updated or superseded. *Rejected*:
   requiring `UPGRADE_<ProductMetadata.version()>.md` — it forces an empty guide for every patch.

3. **The backup is the one taken by the installed version.** The guide orders the steps: stop the server, run
   `server backup create` and `server backup verify` with the program currently installed, keep the file outside the
   installation directory, only then install. It says in one sentence why a backup made after the first start of the new
   version does not allow a way back. *Rejected*: changing `createBackup` so that it copies without migrating — it is a
   behaviour change of a public capability, belongs to AUD-DEP-16, and would not help an operator who already started
   the new version.

4. **The guide quotes the refusal, and the test checks the quote against the source.** The sentence
   `is newer than supported` is what the operator will read; the test asserts it is in the guide and still in
   `SqliteSchemaManager.java`. A reworded refusal fails the build instead of orphaning the guide. This is a textual
   assertion by nature (`.md` target, ADR-0103): the intention is "this sentence appears here".

5. **Older guides are scoped by a banner, their body is left alone.** `UPGRADE_1_1.md` and `UPGRADE_1_2.md` are dated
   records of published releases (`documentation currency is per block`); the banner names the release they describe and
   links the latest guide. The one sentence that reads as current — `UPGRADE_1_2.md:108` — is bounded to "1.2.0".
   *Rejected*: editing "V015" wherever it appears; it was true of 1.2.0 and must stay so.

6. **No ADR.** No structural decision: documentation plus a coherence test, in the family
   `RepositoryDocumentationCoherenceTest` already covers.

## Risks / Trade-offs

- The test proves the guide names the right number and the right refusal; it cannot prove the prose is correct. Review
  by the maintainer remains the control for the procedure itself.
- The constant is a compile-time constant; the architecture module reads it through
  `SqliteServerMaintenance.SUPPORTED_SCHEMA_VERSION` (public). `javac` inlines it, which is harmless here: the test is
  recompiled with the reactor.
- The command names in `UPGRADE_1_1.md` (`server backup --output`, `server backup-verify --input`) are not the commands
  the CLI serves today (`server backup create`, `server backup verify`, `MorpheusServerCli.java:73-76`). It is a dated
  page; the new guide names the current commands, and the existing `gate-reachability` requirement on current
  documentation applies to it.

## Open Questions

- Whether the operator may skip 1.2.0 (1.1.0 → 1.2.1 directly). The migrations are cumulative and nothing forbids it;
  the guide should say so only if the maintainer confirms it is supported. Task 1.1.
