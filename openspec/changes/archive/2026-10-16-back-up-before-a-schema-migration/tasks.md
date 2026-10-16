# Tasks

Specified by pull request 437; implemented on `feat/back-up-before-a-schema-migration`, after
`state-the-schema-upgrade-and-its-way-back` (AUD-DEP-01 precedes AUD-DEP-16).

## 1. Decide before writing code

- [x] 1.1 Maintainer: sibling file now (decision 9 of `design.md`), or wait for sprint 5's single connection policy and
      write to the layout's `backups` directory from the start. Proof: the decision, in the pull request and the ADR.
  - 2026-10-11: not answered explicitly. The maintainer was asked, was given the recommendation (sibling file now) and said to continue; the lot follows the recommendation, and ADR-0110 records `backups` as the destination to move to once the store has a single connection policy. Merging the pull request is the acceptance.
  - 2026-10-16: settled by the merge of the lot (sibling file, ADR-0110), as recorded above.
- [x] 1.2 Write the ADR (decisions 3, 4, 5, 7, 9; the rejected fixed-name-and-reuse design; the installer fallback).
      Cite ADR-0018, 0021, 0086, 0088. Read the highest attributed number with
      `bash .claude/skills/live-numbers/numbers.sh` at that moment; do not reuse the value quoted in `design.md`. Proof:
      the ADR file, `docs/architecture/adr/README.md` updated, no duplicate number.
  - 2026-10-11: ADR-0110 (`docs/architecture/adr/`, where the records now live). Number read from `numbers.sh` on the day: 109 records, highest 0109, no duplicate. Index updated.

## 2. Find out, then reproduce before fixing

- [x] 2.1 Does `VACUUM INTO` run on the connection `SqliteStoreConnection.open` holds, inside and outside a
      `SqliteConnectionScope`? Proof: a throwaway store test, its result quoted. If it does not, the copy takes its own
      connection and the design's risk paragraph is updated before any other task.
  - 2026-10-11: it runs: `anOutdatedDatabaseOpenedInsideARequestScopeIsCopiedOnTheScopedConnection` opens two stores inside a `SqliteConnectionScope` and finds one copy. No connection of its own is needed.
- [x] 2.2 Store test on the frozen schema-12 fixture of `R2UpgradeCompatibilityTest`: open it, assert one copy named for
      version 12 exists, passes `integrity_check`, records 12, and holds the pre-migration row counts. Proof: fails on
      `HEAD` (no copy is written).
  - 2026-10-11: `anOutdatedDatabaseIsCopiedAsItWasThenMigrated`, on a database built by applying V001 to V012 as `SqliteMigrationAtomicityTest` does (the R2 fixture is private to its test). Red before the copy was wired into `SqliteStoreConnection`: `expected one pre-migration copy, found []`.
- [x] 2.3 Negative tests, each with `assertThrows` **and** assertions on `PRE_MIGRATION_BACKUP_FAILED`, on both versions
      in the message and on the absence of the directory path in it: destination not writable. Assert `MAX(version)` is
      unchanged. Proof: fail on `HEAD`.
  - 2026-10-11: `aCopyThatCannotBeWrittenIsRefusedByNameWithoutAPathAndReplacesNothing`. A non-writable directory is not portable to Windows, so the test occupies the copy's name through the suffix supplier of `SqlitePreMigrationCopy`: `VACUUM INTO` refuses the existing target. It is exercised on the copy step, not through a store opening.
- [x] 2.4 No-copy tests: new database; database at the supported version opened twice (compare directory listings);
      database ahead of the program (existing refusal, no copy). Proof: pass on `HEAD` and after.
  - 2026-10-11: `aNewDatabaseAndAnUpToDateDatabaseAreNotCopied`, `aDatabaseAheadOfTheProgramIsRefusedWithoutACopy`.
- [x] 2.5 History mismatch: an outdated database with one altered recorded checksum. Proof: the existing mismatch
      message naming the version, not `PRE_MIGRATION_BACKUP_FAILED`.
  - 2026-10-11: `aDivergentHistoryIsStillRefusedAsAHistoryMismatch`: `SQLite migration history mismatch for version 3`.
- [x] 2.6 Retry and staleness: make one migration fail after the copy (an existing store-test seam, not a production
      flag), reopen — two copies, first one's digest unchanged. Then the stale case: upgrade from 12, replace the
      database by another schema-12 file, reopen — a new copy whose content is the second database's. Measure how many
      copies a failing migration produces under repeated opens. Proof: the tests, and the number.
  - 2026-10-11: partly. `aRetryAfterAFailedMigrationWritesANewCopyAndLeavesTheFirstAlone` proves one copy per attempt and that the first is not rewritten. The stale case after an offline restore is covered by construction (no copy is ever reused) and has no test of its own. The number of copies a failing migration produces under load was not measured: it is one per opening that reaches the migration, unbounded, and ADR-0110 says so.
  - 2026-10-13: completed. `aDatabaseReplacedByAnotherOfTheSameOldVersionIsCopiedAsItIs` is the stale case: after a first upgrade, the database is replaced by another schema-12 file and reopened; a second copy is kept and holds the second database's content, the first copy still holds the first's. `aMigrationThatKeepsFailingKeepsOneCopyPerAttempt` is the measurement: three failing opens leave three copies, each at the original version, none reused and none removed. A database that cannot be migrated costs one copy of itself per attempt.
- [x] 2.7 Concurrency, with **two processes** (two JVMs through `ProcessBuilder` in a store test, as the process-tree
      tests already do), not two threads. Proof: every remaining copy records the original version; run it enough times
      to see the discarded-copy branch at least once, or force it with a seam.
  - 2026-10-11: done with two threads and two physical connections, **not two processes**: `twoConcurrentOpeningsLeaveOnlyTrueCopies`, passed on three consecutive runs. It showed something that predates this change: the loser of two concurrent migrations can fail with `SQLite schema migration failed`. The discarded-copy branch is forced separately by `aCopyTakenAfterSomeoneElseMigratedIsDiscarded`.
  - 2026-10-13: done with two processes. `twoProcessesOpeningOneOutdatedDatabaseLeaveOnlyTrueCopies` starts two JVMs (`FixtureOpenStoreProcess`) on one outdated database, three rounds: at least one opens the store, the other opens it or is refused by it, the database ends at the supported version, and every copy left holds the database as it was. The discarded-copy branch stays forced by `aCopyTakenAfterSomeoneElseMigratedIsDiscarded`; the two processes are started back to back, not released by a common latch.

## 3. The change

- [x] 3.1 In `SqliteStoreConnection.open`, before `migrate`: read the recorded version (absent ledger → no copy); if
      `0 < version < SUPPORTED_SCHEMA_VERSION`, take the copy. Keep `SqliteSchemaManager.migrate(Connection)` free of
      file-system knowledge. Proof: 2.2 and 2.4 pass.
  - 2026-10-11: in `SqliteStoreConnection.open`, skipped when the scope already marked the schema ready; `SqliteSchemaManager` is untouched.
- [x] 3.2 The copy: unique name (decision 3), `VACUUM INTO`, hardening as `createBackup` does, then integrity and
      recorded version — not `verify` (decision 5). Three similar lines do not justify a helper; extract only what both
      callers genuinely share. Proof: `server backup create` tests pass unmodified; 2.5 passes.
  - 2026-10-11: `SqlitePreMigrationCopy`: nothing extracted from `SqliteServerMaintenance`, whose tests pass unmodified.
- [x] 3.3 Discard the process's own copy when it records another version than the one read; never touch a file the
      attempt did not write. Proof: 2.6 and 2.7 pass.
  - 2026-10-11: the copy is deleted only by the attempt that wrote it.
- [x] 3.4 Refusal: `KnowledgeStoreException` naming `PRE_MIGRATION_BACKUP_FAILED` and both versions, no path, cause
      chained. Proof: 2.3 passes; the D2 and audit gates stay green.
  - 2026-10-11: message: `PRE_MIGRATION_BACKUP_FAILED: cannot keep a copy of the database at schema version <n> before migrating it to <m>`; cause chained.
- [x] 3.5 Disclosure: a test through the remote projection and one through the local HTTP surface, opening an outdated
      database whose copy cannot be written. Proof: no server path in either response.
  - 2026-10-11: asserted on the message (no directory name in it). No test through the remote or the local HTTP surface was written.
  - 2026-10-13: still open. The refusal can only be produced by a copy that cannot be written; the store test forces it by a seam on the copy's name, which an HTTP request cannot reach, and making a directory unwritable is not portable to the Windows runners. The message is asserted to name no location at its source.
  - 2026-10-16: closed by reading both boundaries, not by an end-to-end HTTP test, which cannot reach the condition portably. Remote: `POST /server/backups` ends a `KnowledgeStoreException` in the facade's `RuntimeException` branch, a fixed `INTERNAL_ERROR` text. Local: the `STATE_CONFLICT` branch relays the message through `BoundaryFailureMessage.safe`, which replaces any message that names a location. The message itself is asserted location-free at its source.
- [x] 3.6 Break the rule once, then revert: skip the copy when the directory is not writable ("migrate anyway"). Proof:
      2.3 fails by name.
  - 2026-10-11: with the refusal turned into a silent return, `aCopyThatCannotBeWritten...` fails: `Expected KnowledgeStoreException to be thrown, but nothing was thrown`. Reverted.

## 4. Around the change

- [x] 4.1 `docs/user/UPGRADE_1_2_1.md`: one paragraph — the file that appears, that there is one per attempt, that it is
      never pruned, that the manual backup remains step one. `SECURITY.md` or `OPERABILITY.md`: the file, its
      permissions. Proof: the `schema-upgrade-guidance` coherence test stays green.
  - 2026-10-11: a paragraph in `UPGRADE_1_2_1.md` and one in `SECURITY.md`.
- [x] 4.2 Persistence parity: run the `*PersistenceParityTest` suites. Proof: green, unmodified.
  - 2026-10-11: the parity suites ran in the full reactor, green and unmodified.
- [x] 4.3 M19: confirm the extra version read does not move a budget (`scripts\validate.cmd m19`, no `-Version`), and
      that no copy is taken on the fresh databases the gates use. Proof: budgets unchanged.
  - 2026-10-11: the M19 budgets were not replayed locally; they run in the nightly lane. A fresh database records no version, so no copy is taken there; the extra read of the recorded version is the only added cost.
  - 2026-10-16: M19 green on both platforms in the nightly lane, which checks out `develop` on a schedule: run 38847430665 (16/10, `develop` at `557fed02`), also 38678268919 (15/10) and 38508249481 (14/10).
- [x] 4.4 `./mvnw clean verify`; the new branches count against the per-module scale of `morpheus-store-sqlite` and the
      changed-line gate (80 % lines / 70 % branches of the diff). Proof: green on both platforms in CI.
  - 2026-10-11: `./mvnw clean verify` on Windows: BUILD SUCCESS, 3 868 test executions, 0 failure. Changed-line gate replayed with `check-diff-coverage.py`: 84.9 % of changed lines, 77.8 % of changed branches. Linux is left to CI.
- [x] 4.5 Pull request description: finding covered, the ADR, the decisions of `design.md`, the review objections it
      records, the rule/code divergence on `rules/security.md`, and the justification for any test added under
      `morpheus-architecture-tests/`.
  - 2026-10-11: in the description of the pull request that carries this lot.
- [x] 4.6 Decide with the maintainer whether `docs/developer/reference/openapi/morpheus-v1-remote-m26.yaml` should declare a 5xx for
      `POST /api/v1/server/backups`, now that this request can raise the refusal. If yes, it is a contract change with
      its own justification (`contracts/public-surfaces.tsv` itself does not move). Proof: the decision.
  - 2026-10-11: not decided; raised in the pull request.
  - 2026-10-16: the maintainer decided yes. `morpheus-v1-remote-m26.yaml` declares `500` (`InternalError`) on `POST /server/backups`, the status the facade already returns; `contracts/public-surfaces.tsv` does not move.
