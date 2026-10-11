# Design

## Context

- `SqliteStoreConnection.open(databasePath, …)` opens the connection, applies its preparation, then calls
  `new SqliteSchemaManager().migrate(connection)`. It is the only caller of `migrate`
  (`MultiResourceOwnershipContractTest` holds that). `migrate` receives a `Connection` only; the path is known one
  frame above. `SqliteConnectionScope.schemaReadyIfActive()` short-circuits the migration inside a prepared scope, and
  inside a scope `SqliteDatabaseSecurity.open` returns the scope's shared connection, not a new one.
- **Stores are opened per request**, not once at start-up (`ApiRuntime.java:42-51`, ADR-0109), and
  `MorpheusRemoteHttpServer` reaches `createBackup`, which opens the store. The first open of an outdated database can
  therefore happen inside an HTTP request or an MCP tool call.
- `SqliteServerMaintenance.createBackup` hardens the directory, **opens the store** (which migrates), runs `VACUUM INTO`,
  hardens the file and returns `verify(target)`. `verify` checks integrity, refuses a schema newer than supported **and
  refuses a ledger the running version does not accept**. `restoreOffline` does not migrate.
- The lock taken on open is shared: it does not serialise two processes opening the same database.
- The layout's `backups` directory (ADR-0088) is resolved in `morpheus-cli` (`CliLayout.backupsDirectory`); the store
  module does not know it, and twelve composition sites open a store (AUD-TRV-05, sprint 5).
- `VACUUM INTO` cannot run inside a transaction and fails if its target exists; `migrate` opens its own transaction.
- `R2UpgradeCompatibilityTest` holds a frozen database at schema 12.

## Goals / Non-Goals

**Goals**: the open that advances the schema of an existing database leaves a verified copy at the original version;
failing to keep that copy stops the advance; nothing changes for a new or up-to-date database.

**Non-Goals**: a downgrade migration; disaster recovery (the copy sits on the same volume); pruning copies; changing
`server backup create` so that it copies without migrating (decision 5); the Windows installer (decision 2).

## Decisions

1. **Automatic copy in the store, not an imperative step alone.** The imperative procedure is delivered by
   `state-the-schema-upgrade-and-its-way-back` and stays; it protects only the operator who reads it. *Rejected*:
   procedure only — `UPGRADE_1_1.md:63` did exactly that, and the next guide (`UPGRADE_1_2.md:24`) weakened it to "if
   desired" within one release.

2. **Not the Windows installer.** It is one of three distributed artifacts, and
   `distribution/windows/update-installation.ps1` knows nothing of the database. *Rejected.*

3. **One copy per attempt, under a name nobody else can hold.** For a database `<dir>/<name>`, the copy is
   `<dir>/<name>.pre-migration-v<NNN>-<UTC timestamp>-<8 random hex>`, the scheme `createBackup` already uses for its
   own files. `VACUUM INTO` refuses an existing target, so a copy never replaces a file. *Rejected (first draft of this
   design, withdrawn after review)*: a fixed name per original version, verified and reused when present. A reused copy
   can be stale — after an offline restore of an older backup, or a return to the previous program, the database at
   version N is no longer the one the existing `…vN` file was taken from — and it would be presented as the way back.
   It also needed an atomic "move unless present", which the JDK does not offer (`ATOMIC_MOVE` replaces on POSIX). The
   cost of the retained design is one more file per failed attempt; it is stated in the guide.

4. **The copy is checked against the version it is named for, and discarded when it is not that.** The version is read
   before the copy and outside any transaction, so another process may migrate in between. After `VACUUM INTO`, the copy
   must pass `integrity_check` and record the original version as its highest version. A copy that records another
   version is deleted by the process that just wrote it — it is not a pre-migration copy — and the open proceeds: the
   migration is then a no-op for this process. A copy that fails integrity is a refusal (decision 6).

5. **The copy is not passed through `verify`.** `verify` refuses a ledger the running version does not accept, and that
   refusal belongs to the migration: `sqlite-schema-history` requires "an explicit migration history mismatch naming the
   version". A database with a divergent history is copied (integrity and version only), then refused by the migration
   under its existing message. The copy that remains is harmless and is what an operator would want before touching
   such a database.

6. **`server backup create` keeps migrating first, and benefits from this change.** Its copy is by design at the current
   schema. The open it performs first is now the open that writes the pre-migration copy. *Rejected*: a
   `--without-migrating` option — a new public option on a `WRITE` capability for a case the automatic copy covers.

7. **Refusal is named, fail-closed, and carries no path.** If the copy cannot be written or fails integrity, opening
   fails with a `KnowledgeStoreException` whose message carries `PRE_MIGRATION_BACKUP_FAILED`, the original and the
   supported versions, and **no file-system location**; the underlying cause stays in the exception chain. Because the
   open can happen inside a request, the message can travel to a caller: the remote surface already answers such a
   failure with a generic 500 (`MorpheusRemoteHttpServer.java:262`), and the local one must not be given a path to
   print. *Rejected*: migrate anyway with a warning — the product has no log an operator reads (AUD-TRV-04), and the
   repository forbids silent degradation.

8. **ADR required.** The store gains a side effect on open and a new reason to refuse. No number is assigned here:
   `bash .claude/skills/live-numbers/numbers.sh` gave 109 numbered records, highest `0109`, no duplicate, on 2026-10-11
   — re-read it when the ADR is written. It references ADR-0018 (SQLite store), ADR-0021 (migrations), ADR-0086 (bounded
   SQLite concurrency) and ADR-0088, whose layout provides a `backups` directory that this copy does not use, and says
   why (decision 9).

9. **A sibling of the database, not the layout's `backups` directory — for now.** The store does not know that
   directory; threading it through twelve composition sites duplicates the defect AUD-TRV-05 names, one sprint before it
   is fixed. The requirements say "a copy", not "a sibling file", so the destination can become a property of the single
   connection policy sprint 5 introduces. The maintainer may prefer to wait for sprint 5 and write to `backups` from the
   start: task 1.1.

## Risks / Trade-offs

- **Disk space**: one copy the size of the database per attempt, never pruned. A migration that keeps failing adds a
  copy each time it is retried — and, with per-request opens, each time a request retries it. Task 2.6 measures whether a
  failing migration under load can fill a volume; if so, the open must refuse to copy again while a valid copy of the
  same original version **and the same content digest** exists, which is the only safe form of reuse.
- **First-request latency**: the `VACUUM INTO` happens on whichever open comes first after the upgrade, possibly a
  request. Once, then never (`schemaReadyIfActive`, and an up-to-date database takes no copy).
- **Reading the version on every open** adds one query before `migrate` when no scope is active. Task 4.3 measures it
  against the M19 budgets; `migrate` already runs the same query inside its transaction.
- **A restrictive data directory** now stops an upgrade that used to succeed. Intended: the refusal names the cause and
  nothing was migrated.
- **`POST /api/v1/server/backups`** can now be the request that writes the copy or raises the refusal.
  `docs/developer/reference/openapi/morpheus-v1-remote-m26.yaml:36-48` declares no 5xx for it; the gap exists today and this change makes it
  easier to reach. Task 4.6 decides whether the contract should say so.
- **`VACUUM INTO` on a scope's shared connection** has not been tried. If it cannot run there, the copy needs its own
  connection: task 2.1 finds out before anything else is written.

## Review objections and how they were handled

- *Reused copy can be stale; "atomic move without replacement" does not exist; version read outside the transaction* —
  accepted; decisions 3 and 4 replace the first draft.
- *The refusal is not confined to start-up* — accepted; context and decision 7 corrected, the path removed.
- *`PRE_MIGRATION_BACKUP_FAILED` would mask a history mismatch* — accepted; decision 5.
- *ADR must cite 0021, 0086, 0088* — accepted; decision 8.
- *`rules/security.md` says the `SqliteTransactionRunner` and backup invariants are asserted textually; no architecture
  test contains those strings* — confirmed by `git grep` on 2026-10-11 (no match for `catch (Error failure)`,
  `rollbackSuppressing`, `VACUUM INTO` or `integrity_check` under `morpheus-architecture-tests`). It is a rule/code
  divergence that predates this change; it is reported in the pull request and not fixed here.
