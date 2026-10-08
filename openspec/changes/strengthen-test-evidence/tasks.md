# Tasks

## 1. Verification scripts that must not damage the machine

- [x] 1.1 Make `verify-windows-setup-lifecycle.ps1` refuse to start, naming the key, when `HKCU:\...\Uninstall\{4D0DC052-2FD6-49F5-88F4-E32C9B1EB67A}_is1` already exists (or switch it to the smoke AppId); verify by creating a dummy key, running the script, and checking it fails before any `Start-Process` and leaves the key intact
- [x] 1.2 Add behavioural tests for `scripts/check-diff-coverage.py` on a synthetic diff and JaCoCo XML (pass, fail on lines, fail on branches, file absent from the report); verify they run in `ci.yml`
  - 2026-10-08: `scripts/tests/test_check_diff_coverage.py` (stdlib `unittest`, 7 cases: pass, line minimum, branch minimum, source absent from every report, aggregate preferred and mapped back, test sources ignored, unsafe path refused); run by a new Linux step of `ci.yml`

## 2. Tests that cannot pass vacuously

- [x] 2.1 Replace the `return` on symlink creation failure by `assumeTrue` in the six tests listed by PRV-AUD-6; verify that forcing the failure path reports them skipped, not passed
  - 2026-10-08: the audit did not name the six; the pattern was found in 22 tests across `api`, `application`, `provider-sdk`, `provider-markdown`, `provider-openspec` and `provider-synthetic`, all converted (`assumeTrue`, or `abort` in a `catch`); one test (`LocalSourceInventorySecurityTest`) asserted the link only when it happened to exist. Forcing the failure path in the markdown helper reports the test skipped
- [x] 2.2 Assert the refusal message, not only the exception type, in `SqliteSchemaMigrationTest#modifiedMigrationHistoryIsRejected` and `SqliteDatabaseLeaseTest`; verify by changing the message and watching them fail

## 3. Behaviour exercised for real

- [x] 3.1 Test a migration failing midway (pre-existing conflicting table at v12): opening fails with a checked message, the ledger stays at 12, no V013–V020 object exists, data intact, reopening succeeds after removing the conflict
- [x] 3.2 Test the database lease against a second JVM holding it (`ProcessBuilder`), on Windows and Linux; verify shared/shared succeeds and exclusive is refused
- [ ] 3.3 Record real `tools/list` and tool responses from pinned MINOS and NEXUS versions as fixtures and assert the gateways parse them; verify a schema change in the recording fails the test
  - Deferred (maintainer decision, 2026-10-08): recordings of the real peers would carry the names and paths of indexed projects; to be done on a dedicated neutral project

## 4. Code hygiene found by SpotBugs

- [x] 4.1 Remove or read `currentAttempted`, `changeAttempted`, `deltaAttempted` in `OpenSpecSpecificationContentReader.ReadState`; verify SpotBugs no longer reports `URF_UNREAD_FIELD` there
  - 2026-10-08, done in PR #408 (commit `3d845406`), which rewrites `ReadState` (maintainer decision): the three fields and their assignments are removed; SpotBugs at max effort and low threshold reported 3 `URF_UNREAD_FIELD` on `ReadState` before and none after, the other 11 findings of the module unchanged
- [x] 4.2 Narrow the `catch (NullPointerException)` of `MorpheusReasoningApiService.analyze` to the request conversion; verify an NPE injected in the service is no longer reported as "reasoning request contains a null value"

## 5. Mutation-testing follow-up

- [x] 5.1 Add `morpheus-store-sqlite` tests where the database, its `-journal`, `-wal` or `-shm` is a symbolic link or a non-regular file at offline restore, each asserting the refusal message and that the live database is untouched; verify the PIT lot on `SqliteServerMaintenance` kills the four `rejectUnsafeEntry` removals
  - 2026-10-08: `SqliteOfflineRestoreEntryTest`, 8 cases (database and three sidecars, each as a symbolic link and as a directory), each asserting the message and an unchanged live database; removing each of the four `rejectUnsafeEntry` calls by hand fails 2 tests (the PIT profile lives on PR #407, not on `develop`)
- [x] 5.2 Add a test comparing `BackupVerification.sha256()` with a SHA-256 computed independently of `SqliteServerMaintenance`; verify the `MessageDigest.update` removal is killed
  - 2026-10-08: `theBackupChecksumIsTheSha256OfTheBackupFile` compares with a digest computed in the test; removing `digest.update` fails it
- [x] 5.3 Make `LocalWritePermissionHardenerTest#refusesAclDirectoryGrantingMutationToBroadPrincipalWhenAclIsAvailable` resolve a broad group independently of the Windows display language (or `assumeTrue` when none resolves); verify on a French Windows that it runs and that the `isTrustedAclPrincipal` mutation is killed
  - 2026-10-08: the broad group is resolved from its well-known SID (S-1-1-0, then S-1-5-32-545) through the Windows PowerShell shipped under `%SystemRoot%`, and the test is reported skipped when none resolves; it now runs on this French Windows (it returned, passing, before) and fails when `isTrustedAclPrincipal` always answers true
- [x] 5.4 Add tests driving `ProviderIngestionBudget` through a provider read past the line budget and past the file-count budget, each asserting the refusal metric; verify the PIT removals of `requireLines` and `requireFiles` are killed, then decide whether the post-read `requireDocumentBytes`/`requireAggregateBytes`/`requireEvidenceBytes` re-checks (equivalent mutants after a bounded read) are removed or kept with a comment
  - 2026-10-08: `ProviderIngestionBudgetTest#theFileBudgetAloneRefusesTheReadPastIt` and `#theLineBudgetAloneRefusesTheReadPastIt` exceed one budget each (the existing many-files test also exhausted the aggregate bytes, which refused the read without the file check); `StructuredMarkdownSpecificationContentReaderTest#aDocumentPastTheLineBudgetIsRefusedNamingTheMetric` drives the default budget through a real provider read. Removing `requireFiles` or `requireLines` by hand now fails a test
  - Decision: the post-read `requireDocumentBytes` / `requireAggregateBytes` / `requireEvidenceBytes` are kept, with a comment stating they cross-check the resolver's bound and are equivalent mutants (measured: removing each fails no test)
- [ ] 5.5 For each other PIT-AUD finding qualified "test manquant" in the audit document, add the test in the mutated class's module; verify the same PIT command kills the mutation
  - Deferred (maintainer decision, 2026-10-08): needs the audit record of PR #407 and PIT runs
