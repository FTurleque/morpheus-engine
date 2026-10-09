# Proposal

## Why

The 2026-10-08 audit (`docs/audits/AUDIT_OUTILLE_2026-10-08.md`) found places where a green test proves less than its
name says, where a verification script can damage the machine it runs on, or where a behaviour is only exercised
against fixtures written in this repository. None of them is a product defect by itself; each lets a defect pass
unnoticed. The findings are grouped here because their remedy is the same kind of work — a test or a guard — and none
changes externally observable behaviour.

## What Changes

- **PKG-AUD-1** — `scripts/verify-windows-setup-lifecycle.ps1` runs the production installer with the production
  AppId and never checks whether that AppId's uninstall key already exists; on a machine where MORPHEUS is installed
  it rewrites, then deletes, the real installation's registration. The script refuses to start when the key exists,
  or uses the smoke AppId the `.iss` already defines for that purpose.
- **PRV-AUD-6** — six symlink tests `return` (and pass) when the platform refuses to create a link; they use
  `assumeTrue` so the run reports them skipped.
- **STO-AUD-4 / STO-AUD-5** — a migration failing midway, and the database lease held by another process, are tested.
- **INT-AUD-1** — the MINOS and NEXUS contracts are checked against recorded responses of the real peers (pinned
  versions), not only against fixture peers that reuse MORPHEUS's own constants.
- **CI-AUD-5** — `scripts/check-diff-coverage.py`, which blocks every pull request, gets behavioural tests on fixtures.
- **SB-AUD-1 / SB-AUD-2** — the three write-only fields of `OpenSpecSpecificationContentReader.ReadState` are removed
  or read; the `catch (NullPointerException)` of `MorpheusReasoningApiService.analyze` is narrowed to argument
  conversion so an internal defect is not reported to the client as an invalid request.
- **PIT-AUD-1** — removing any of the four `rejectUnsafeEntry` calls of the offline restore (database, `-journal`,
  `-wal`, `-shm`), or the `MessageDigest.update` of the backup checksum, fails no test of the repository measured by
  the audit: the restore's refusal of a symbolic link or non-regular file, and the checksum value, get tests.
- **PIT-AUD-2** — the only test of the refusal of an ACL granting mutation to a broad group resolves the group by its
  English name (`Everyone`, `BUILTIN\Users`, `Users`) and returns, passing, when none resolves: on a French Windows
  (measured) it verifies nothing. It resolves the group independently of the display language, or reports itself
  skipped.
- **PIT-AUD-3** — removing the line-budget or file-count-budget check of a provider read fails no test of the
  repository, provider tests included; the post-read size re-checks are probably redundant with the bounded read.
- **PIT-AUD-*** — the other surviving and uncovered mutations the audit qualified as missing tests get their tests, in
  the module of the mutated class.

## Capabilities

### New Capabilities

(none — tests, verification scripts and internal code hygiene; no externally observable behaviour changes)

### Modified Capabilities

(none)

## Impact

- `scripts/verify-windows-setup-lifecycle.ps1`, `scripts/check-diff-coverage.py` (+ tests).
- Tests in `morpheus-provider-markdown`, `morpheus-provider-openspec`, `morpheus-provider-synthetic`,
  `morpheus-provider-sdk`, `morpheus-store-sqlite`, `morpheus-integration-minos`, `morpheus-integration-nexus`.
- `morpheus-provider-openspec` (`ReadState`), `morpheus-api` (`MorpheusReasoningApiService`).
