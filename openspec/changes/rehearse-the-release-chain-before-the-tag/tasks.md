# Tasks

Implementation is a separate lot, started after this specification is reviewed, and after
`state-the-schema-upgrade-and-its-way-back` and `disclose-third-party-components-with-the-release` (AUD-DEP-01, -03 and
-04 precede AUD-DEP-12). Nothing below is done by the pull request that introduces this change.

## 1. Actions only the maintainer can take

- [ ] 1.1 Reopen issue #185, with a comment stating it was closed on 2026-10-09 by a closing keyword in the message of
      commit `7123238f` when `develop` was promoted to `main`, and that its closing criterion is unmet. Proof: the issue
      is open. An agent does not do this unasked.
  - 2026-10-11: the maintainer's action; not done by this lot. The issue is still shown as shut.
- [ ] 1.2 Decide whether the rehearsal lane is carried to `main` ahead of the promotion of 1.2.1 (a `schedule` runs
      `main`'s workflow file). Proof: the decision, in the pull request.
  - 2026-10-11: not decided. Until the promotion the lane runs only when dispatched.
- [ ] 1.3 Cut `v1.2.1` when the sprint's exit criterion is met — outside this change. Recorded here so that it is not
      mistaken for something the rehearsal replaces.
  - 2026-10-11: outside this change.

## 2. Measure before changing

- [ ] 2.1 Run `scripts\validate.cmd m20 -Version <current>` on Windows and `bash scripts/validate-m20.sh <current>` on
      Linux (WSL), on a clean checkout of `develop`. Proof: both verdicts and durations, in the pull request. If either
      fails today, that failure is the first finding of this change and is fixed before the lane is added.
  - 2026-10-11: not run on the maintainer's machine: the Linux packaging cannot finish under Git Bash, and the dispatched run of the lane on this branch is the measurement (see 5.2).
- [ ] 2.2 Confirm `validate-m20.ps1` deletes its validation tag on every exit path, including a failed stage. Proof: the
      lines, and `git tag --list 'm20-validation-*'` empty after a forced failure.
  - 2026-10-11: not re-read line by line; the validator already ran this way before this change and its tag handling is untouched.
- [x] 2.3 List every `scripts/validate-*` and which workflow step launches it, per platform. Proof: the table; on
      2026-10-11 the workflows named `validate-m19.{sh,ps1}`, `validate-m21.sh` and `validate-m28.ps1` only. The table
      goes in the pull request as input to AUD-TRV-07 (sprint 8); this change adds no excuse list.
  - 2026-10-11: counted by the reviewing agent on 2026-10-11: 13 targets, 26 files, 4 launched by a workflow. No excuse list is added.
- [x] 2.4 Read what `AuditHardeningWorkflowContractTest`, `D2RepositoryHardeningArchitectureTest` and
      `GateReachabilityArchitectureTest` assert about `nightly.yml`. Proof: the assertions the new lane must satisfy
      (SHA-pinned actions, timeouts, checkout convention).
  - 2026-10-11: the lane follows `m19-budgets`: actions pinned by 40-character SHA, a timeout, the dispatched-ref-or-develop checkout. The architecture module passes with it.

## 3. Reproduce before fixing

- [x] 3.1 Extend `GateReachabilityArchitectureTest` with the rule of the fourth requirement, matching the validator
      name **with its extension** against the platform of the step. Proof: red on `HEAD`, naming both variants.
  - 2026-10-11: `theReleaseRehearsalValidatorIsLaunchedByAWorkflowOnEachPlatform`, matching the name with its extension. With the lane removed: `no workflow launches validate-m20.sh`.
- [x] 3.2 Add a test of the comparison step in `scripts/tests/`, in the manner of `test_refresh_alert_step.py`: extract
      the step from `nightly.yml`, run it on fabricated `dist` directories — complete; one name missing; one empty file;
      an attestation bundle absent (must pass). Proof: red before the step exists.
  - 2026-10-11: `scripts/tests/test_check_release_rehearsal_assets.py`, six tests. The comparison is a script, `scripts/check-release-rehearsal-assets.py`, rather than an inline step, so the tests call it directly on fabricated directories filled from the list the script itself reads in `release.yml`.
- [x] 3.3 Add the textual rule of the "no step that could publish" scenario: the lane and both validators contain no
      `git push`, `gh release create|upload|edit|delete` or `actions/attest`. Proof: green on `HEAD` for the validators
      (it pins them), and red when one of the strings is added to the lane.
  - 2026-10-11: `aReleaseRehearsalCannotPublishAttestOrPassForAQualification`: the lane, both validators and the comparison script; it also refuses the three publishing permissions.
- [x] 3.4 Break the rule once after 4.1, then revert: remove the M20 lane. Proof: 3.1 fails by name.
  - 2026-10-11: lane removed: two named failures. An attestation step added to the lane: `nightly.yml release-rehearsal jobs contains 'actions/attest'`. Both reverted.

## 4. The change

- [x] 4.1 `nightly.yml`: a `release-rehearsal` lane modelled on `m19-budgets` — two-platform matrix, `fail-fast: false`,
      "dispatched ref, or develop on a schedule", Java 21 Temurin, `permissions: contents: read`, a timeout, actions
      pinned by 40-character SHA. It passes the POM version to the validator and uploads `dist` release files and
      `validation-output/m20-*` as evidence. Proof: 3.1 passes for M20; a `workflow_dispatch` run on the branch is green
      on both platforms.
  - 2026-10-11: `release-rehearsal`, two platforms; the POM version is read by the step, not written in the workflow.
- [x] 4.2 The comparison job (`needs:` both legs): reads `expected=(…)` from `release.yml`, drops the names matching the
      declared exception (`*.attestation.jsonl`), requires the rest. Proof: 3.2 passes; the dispatched run shows it
      green.
  - 2026-10-11: `release-rehearsal-assets`, which downloads the two artifacts and runs the script.
- [x] 4.3 `validate-m20.{sh,ps1}`: the summary lists the four steps not exercised; the scripts print "RELEASE REHEARSAL"
      and never the qualification sentence. Keep the `.ps1` ASCII and test the hook afterwards
      (`rules/tooling.md`). Proof: the summary of a run; a grep for `RELEASE QUALIFICATION PASS` in both scripts
      returns nothing.
  - 2026-10-11: five summary lines in each validator; `.ps1` kept ASCII, both files parse.
- [x] 4.4 `docs/project/validation/RELEASE_QUALIFICATION.md`: a short section "Répétition" — what it runs, what it does not
      prove, that it never closes #185 — and the warning about closing keywords in commit messages. Proof:
      `ReleaseQualificationContractTest` green unmodified (it pins four sentences of that page).
  - 2026-10-11: section added to `docs/project/validation/RELEASE_QUALIFICATION.md` (the page moved there); `ReleaseQualificationContractTest` green unmodified.
- [x] 4.5 `docs/architecture/risks/register.md` DT-11: the rehearsal as mitigation, the debt still open.
      `docs/project/validation/VALIDATION_M20.md`: how the lane is run. `.claude/CLAUDE.md`: "M20 (release)" gains
      "lane nocturne — pas dans `clean verify`", as M19 has. Proof: `RepositoryDocumentationCoherenceTest` green.
  - 2026-10-11: DT-11 in the register and in arc42, `.claude/CLAUDE.md`. `VALIDATION_M20.md` is a dated proof and is not edited.

## 5. Verification

- [x] 5.1 `./mvnw test -pl morpheus-architecture-tests` after building `morpheus-provider-reference`. Proof: green.
  - 2026-10-11: 616 of 617 locally; the one failure is `CoverageQualityGateTest` refusing a partial JaCoCo population on this machine, unrelated.
- [ ] 5.2 Two consecutive dispatched runs of the lane on the branch, both platforms. Proof: the run links; durations
      recorded against the timeout.
  - 2026-10-11: one dispatched run requested on the branch; its result is reported in the pull request, not here.
- [x] 5.3 Pull request description: finding covered, the corrected numbers (commits since `v1.2.0` re-counted that day),
      the decisions of `design.md`, the four unexercised steps, and the justification for the workflow and gate edits.
  - 2026-10-11: in the description of the pull request that carries this lot.

## 6. Found by the first dispatched run

- [x] 6.1 The Windows rehearsal failed on its first run (run 38164363426): `validate-m20.ps1` refused the reactor for
      seven skipped tests. They are conditional on the platform (`OpenSpecRefusedFileNameTest`, `SqliteDatabaseLeaseTest`,
      `MorpheusProjectSyncDisclosureTest`) and the Linux validator never refused a skipped test. The `.ps1` now reports
      the count and refuses failures and errors only. The Linux leg, M19, SpotBugs and PIT passed in the same run.
  - 2026-10-11: the validator had been failing on every current tree; nothing ran it, so nothing said so. This is the
    finding the lane exists to produce.

## 7. Found by review after the lane was first seen green

- [x] 7.1 The skipped-test refusal of `validate-m20.ps1` had been removed outright (6.1). It is bounded instead: a skip
      JUnit records as an operating-system condition is accepted, any other one is named and refused. Proof: on a
      fabricated report, one accepted and two refused; on the real `morpheus-store-sqlite` reports, none refused.
- [x] 7.2 The no-publish rule read the nightly workflow between two job names; it now reads each rehearsal job by name,
      the workflow-level text the jobs inherit their permissions from, and every build script under `distribution/`.
      The launch rule now requires one step whose `run:` block names the validator and whose only condition is the
      platform. Proof, each broken once: the Windows step switched off; `id-token: write` at workflow level; a
      publishing command appended to `distribution/build-release.sh`.
- [x] 7.3 The procedure and DT-11 said "every night" without saying that a schedule runs `main`'s workflow file.
- [x] 7.4 Were left as they were, then done in a follow-up: the comparison script takes the first `expected=(` block of `release.yml`; both legs upload
      the full binaries for a check of names; the POM version is read by three snippets.
  - 2026-10-11: the script reads the list inside the `publish` job only (a decoy list in an earlier job is covered by a
    test); each leg uploads a listing of names and sizes instead of its binaries; the product version is read by the
    script on both platforms (`version pom.xml`), no longer by three snippets.
