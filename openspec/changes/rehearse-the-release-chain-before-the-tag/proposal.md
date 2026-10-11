# Proposal

## Why

AUD-DEP-12 (sprint 2, after AUD-DEP-01, AUD-DEP-03, AUD-DEP-04 and AUD-TST-01) reports that the release chain has not
run since `v1.2.0`. Re-read on `develop` at `9277a083` on 2026-10-11, the finding **holds, its numbers have moved, and
its cause is narrower and more fixable than written**:

- `pom.xml:9` is `1.2.1`; `git tag -l` gives `v1.0.0`, `v1.1.0`, `v1.2.0`; `git rev-list --count v1.2.0..HEAD` is
  **1663** today (1588 at the audit). `.github/workflows/release.yml:3-6` triggers on `push` of a `v*` tag and on nothing
  else. `docs/release/RELEASE_NOTES_1.2.1.md` exists.
- **A rehearsal already exists and no workflow runs it.** `scripts/validate-m20.sh:57-61` and `validate-m20.ps1:331-348`
  create a local tag `m20-validation-<sha>`, run `distribution/build-release.{sh,ps1}` on it, check the archive, its
  checksum and the manifest against the head, and delete the tag. The workflows launch `validate-m19.{sh,ps1}`,
  `validate-m21.sh` and `validate-m28.ps1` and nothing else. The repository lists M20 among its active gates
  (`.claude/CLAUDE.md`, "Gates actifs"), and it is active only on the maintainer's machine.
- **The Linux packaging is run by no workflow at all.** `validate-m28.ps1:59-93` builds the Windows image and installer
  on every pull request; `distribution/build-portable.sh` — `jpackage` on Linux, the runtime proof, the launcher smoke —
  is reached only from `build-release.sh`, i.e. from a tag or from `validate-m20.sh`. A regression there is invisible
  until the tag.
- **The tracking issue was closed by accident.** Issue #185 ("validate attested workflow on next v1.2.1+ tag") is the
  one every governance page names as open (`docs/validation/RELEASE_QUALIFICATION.md:6-10`,
  `docs/architecture/risks/register.md:187`, `docs/governance/ROADMAP.md:100`, `.claude/CLAUDE.md`). It was closed on
  2026-10-09T19:09:40Z by commit `7123238f`, at the minute `develop` was promoted to `main` (merge `20cf2e0d`). That
  commit's message says "#185 stays open" — and also "a qualification tool that … could close #185", which GitHub reads
  as a closing keyword. No release was qualified; the issue's own closing criterion is unmet.

The audit proposes a throwaway pre-release tag as the fallback. The repository forbids exactly that:
`RELEASE_QUALIFICATION.md` ("Ne jamais créer un tag ou une release artificielle"), asserted by
`ReleaseQualificationContractTest#theOperatorProcedureKeepsIssue185OpenUntilARealReleaseIsQualified`, and
`release.yml:53` refuses any tag that is not `vX.Y.Z`. The remedy has to exercise the chain without publishing anything.

## What Changes

- The M20 release validator runs on a schedule on both platforms, in the nightly workflow, on the same footing as the
  M19 budgets: same scripts as the release, local tag, nothing pushed. A regression in the release build fails a run the
  night it lands, not the day of the tag.
- The rehearsal compares the files the two builds produce with the names the release workflow requires before
  publishing, so a mismatch between `build-release.*` and `release.yml` is found without a tag.
- What a rehearsal does **not** exercise is stated by the rehearsal itself and by the qualification procedure:
  attestation, publication, and the public read-back stay proven only by a real release. A rehearsal produces no
  qualification evidence and cannot close the tracking issue.
- The tracking issue is reopened, and the pages that name it say what closes it.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `gate-reachability`: adds the release build to the gates that must be reachable by a workflow on both platforms, and
  what a rehearsal of it may and may not claim.

## Impact

- `.github/workflows/nightly.yml` (one lane, two platforms, one comparison job), `scripts/validate-m20.{sh,ps1}` (default
  version, a statement of what was not exercised), `docs/validation/RELEASE_QUALIFICATION.md`,
  `docs/validation/VALIDATION_M20.md`, `docs/architecture/risks/register.md` (DT-11), `GateReachabilityArchitectureTest`.
- `release.yml` itself is **not** modified by this change: its trigger, its jobs and its permissions stay as they are.
- GitHub state outside the repository: issue #185 (reopen), runner minutes for one more nightly lane.
- No CLI command, MCP tool or HTTP route moves. No product code changes.
- Order: after `state-the-schema-upgrade-and-its-way-back` and `disclose-third-party-components-with-the-release`, whose
  additions to the release build are first exercised here.
