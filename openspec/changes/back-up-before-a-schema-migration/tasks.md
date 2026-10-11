# Tasks

Implementation is a separate lot, started after this specification is reviewed and after
`state-the-schema-upgrade-and-its-way-back` (AUD-DEP-01 precedes AUD-DEP-16). Nothing below is done by the pull request
that introduces this change.

## 1. Decide before writing code

- [ ] 1.1 Maintainer: sibling file now (decision 9 of `design.md`), or wait for sprint 5's single connection policy and
      write to the layout's `backups` directory from the start. Proof: the decision, in the pull request and the ADR.
- [ ] 1.2 Write the ADR (decisions 3, 4, 5, 7, 9; the rejected fixed-name-and-reuse design; the installer fallback).
      Cite ADR-0018, 0021, 0086, 0088. Read the highest attributed number with
      `bash .claude/skills/live-numbers/numbers.sh` at that moment; do not reuse the value quoted in `design.md`. Proof:
      the ADR file, `docs/adr/README.md` updated, no duplicate number.

## 2. Find out, then reproduce before fixing

- [ ] 2.1 Does `VACUUM INTO` run on the connection `SqliteStoreConnection.open` holds, inside and outside a
      `SqliteConnectionScope`? Proof: a throwaway store test, its result quoted. If it does not, the copy takes its own
      connection and the design's risk paragraph is updated before any other task.
- [ ] 2.2 Store test on the frozen schema-12 fixture of `R2UpgradeCompatibilityTest`: open it, assert one copy named for
      version 12 exists, passes `integrity_check`, records 12, and holds the pre-migration row counts. Proof: fails on
      `HEAD` (no copy is written).
- [ ] 2.3 Negative tests, each with `assertThrows` **and** assertions on `PRE_MIGRATION_BACKUP_FAILED`, on both versions
      in the message and on the absence of the directory path in it: destination not writable. Assert `MAX(version)` is
      unchanged. Proof: fail on `HEAD`.
- [ ] 2.4 No-copy tests: new database; database at the supported version opened twice (compare directory listings);
      database ahead of the program (existing refusal, no copy). Proof: pass on `HEAD` and after.
- [ ] 2.5 History mismatch: an outdated database with one altered recorded checksum. Proof: the existing mismatch
      message naming the version, not `PRE_MIGRATION_BACKUP_FAILED`.
- [ ] 2.6 Retry and staleness: make one migration fail after the copy (an existing store-test seam, not a production
      flag), reopen — two copies, first one's digest unchanged. Then the stale case: upgrade from 12, replace the
      database by another schema-12 file, reopen — a new copy whose content is the second database's. Measure how many
      copies a failing migration produces under repeated opens. Proof: the tests, and the number.
- [ ] 2.7 Concurrency, with **two processes** (two JVMs through `ProcessBuilder` in a store test, as the process-tree
      tests already do), not two threads. Proof: every remaining copy records the original version; run it enough times
      to see the discarded-copy branch at least once, or force it with a seam.

## 3. The change

- [ ] 3.1 In `SqliteStoreConnection.open`, before `migrate`: read the recorded version (absent ledger → no copy); if
      `0 < version < SUPPORTED_SCHEMA_VERSION`, take the copy. Keep `SqliteSchemaManager.migrate(Connection)` free of
      file-system knowledge. Proof: 2.2 and 2.4 pass.
- [ ] 3.2 The copy: unique name (decision 3), `VACUUM INTO`, hardening as `createBackup` does, then integrity and
      recorded version — not `verify` (decision 5). Three similar lines do not justify a helper; extract only what both
      callers genuinely share. Proof: `server backup create` tests pass unmodified; 2.5 passes.
- [ ] 3.3 Discard the process's own copy when it records another version than the one read; never touch a file the
      attempt did not write. Proof: 2.6 and 2.7 pass.
- [ ] 3.4 Refusal: `KnowledgeStoreException` naming `PRE_MIGRATION_BACKUP_FAILED` and both versions, no path, cause
      chained. Proof: 2.3 passes; the D2 and audit gates stay green.
- [ ] 3.5 Disclosure: a test through the remote projection and one through the local HTTP surface, opening an outdated
      database whose copy cannot be written. Proof: no server path in either response.
- [ ] 3.6 Break the rule once, then revert: skip the copy when the directory is not writable ("migrate anyway"). Proof:
      2.3 fails by name.

## 4. Around the change

- [ ] 4.1 `docs/user/UPGRADE_1_2_1.md`: one paragraph — the file that appears, that there is one per attempt, that it is
      never pruned, that the manual backup remains step one. `SECURITY.md` or `OPERABILITY.md`: the file, its
      permissions. Proof: the `schema-upgrade-guidance` coherence test stays green.
- [ ] 4.2 Persistence parity: run the `*PersistenceParityTest` suites. Proof: green, unmodified.
- [ ] 4.3 M19: confirm the extra version read does not move a budget (`scripts\validate.cmd m19`, no `-Version`), and
      that no copy is taken on the fresh databases the gates use. Proof: budgets unchanged.
- [ ] 4.4 `./mvnw clean verify`; the new branches count against the per-module scale of `morpheus-store-sqlite` and the
      changed-line gate (80 % lines / 70 % branches of the diff). Proof: green on both platforms in CI.
- [ ] 4.5 Pull request description: finding covered, the ADR, the decisions of `design.md`, the review objections it
      records, the rule/code divergence on `rules/security.md`, and the justification for any test added under
      `morpheus-architecture-tests/`.
- [ ] 4.6 Decide with the maintainer whether `docs/openapi/morpheus-v1-remote-m26.yaml` should declare a 5xx for
      `POST /api/v1/server/backups`, now that this request can raise the refusal. If yes, it is a contract change with
      its own justification (`contracts/public-surfaces.tsv` itself does not move). Proof: the decision.
