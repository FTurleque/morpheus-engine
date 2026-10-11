# Tasks

Implementation is a separate lot, started after this specification is reviewed, and after
`state-the-schema-upgrade-and-its-way-back` and `disclose-third-party-components-with-the-release` (AUD-DEP-01, -03 and
-04 precede AUD-DEP-12). Nothing below is done by the pull request that introduces this change.

## 1. Actions only the maintainer can take

- [ ] 1.1 Reopen issue #185, with a comment stating it was closed on 2026-10-09 by a closing keyword in the message of
      commit `7123238f` when `develop` was promoted to `main`, and that its closing criterion is unmet. Proof: the issue
      is open. An agent does not do this unasked.
- [ ] 1.2 Decide whether the rehearsal lane is carried to `main` ahead of the promotion of 1.2.1 (a `schedule` runs
      `main`'s workflow file). Proof: the decision, in the pull request.
- [ ] 1.3 Cut `v1.2.1` when the sprint's exit criterion is met — outside this change. Recorded here so that it is not
      mistaken for something the rehearsal replaces.

## 2. Measure before changing

- [ ] 2.1 Run `scripts\validate.cmd m20 -Version <current>` on Windows and `bash scripts/validate-m20.sh <current>` on
      Linux (WSL), on a clean checkout of `develop`. Proof: both verdicts and durations, in the pull request. If either
      fails today, that failure is the first finding of this change and is fixed before the lane is added.
- [ ] 2.2 Confirm `validate-m20.ps1` deletes its validation tag on every exit path, including a failed stage. Proof: the
      lines, and `git tag --list 'm20-validation-*'` empty after a forced failure.
- [ ] 2.3 List every `scripts/validate-*` and which workflow step launches it, per platform. Proof: the table; on
      2026-10-11 the workflows named `validate-m19.{sh,ps1}`, `validate-m21.sh` and `validate-m28.ps1` only. The table
      goes in the pull request as input to AUD-TRV-07 (sprint 8); this change adds no excuse list.
- [ ] 2.4 Read what `AuditHardeningWorkflowContractTest`, `D2RepositoryHardeningArchitectureTest` and
      `GateReachabilityArchitectureTest` assert about `nightly.yml`. Proof: the assertions the new lane must satisfy
      (SHA-pinned actions, timeouts, checkout convention).

## 3. Reproduce before fixing

- [ ] 3.1 Extend `GateReachabilityArchitectureTest` with the rule of the fourth requirement, matching the validator
      name **with its extension** against the platform of the step. Proof: red on `HEAD`, naming both variants.
- [ ] 3.2 Add a test of the comparison step in `scripts/tests/`, in the manner of `test_refresh_alert_step.py`: extract
      the step from `nightly.yml`, run it on fabricated `dist` directories — complete; one name missing; one empty file;
      an attestation bundle absent (must pass). Proof: red before the step exists.
- [ ] 3.3 Add the textual rule of the "no step that could publish" scenario: the lane and both validators contain no
      `git push`, `gh release create|upload|edit|delete` or `actions/attest`. Proof: green on `HEAD` for the validators
      (it pins them), and red when one of the strings is added to the lane.
- [ ] 3.4 Break the rule once after 4.1, then revert: remove the M20 lane. Proof: 3.1 fails by name.

## 4. The change

- [ ] 4.1 `nightly.yml`: a `release-rehearsal` lane modelled on `m19-budgets` — two-platform matrix, `fail-fast: false`,
      "dispatched ref, or develop on a schedule", Java 21 Temurin, `permissions: contents: read`, a timeout, actions
      pinned by 40-character SHA. It passes the POM version to the validator and uploads `dist` release files and
      `validation-output/m20-*` as evidence. Proof: 3.1 passes for M20; a `workflow_dispatch` run on the branch is green
      on both platforms.
- [ ] 4.2 The comparison job (`needs:` both legs): reads `expected=(…)` from `release.yml`, drops the names matching the
      declared exception (`*.attestation.jsonl`), requires the rest. Proof: 3.2 passes; the dispatched run shows it
      green.
- [ ] 4.3 `validate-m20.{sh,ps1}`: the summary lists the four steps not exercised; the scripts print "RELEASE REHEARSAL"
      and never the qualification sentence. Keep the `.ps1` ASCII and test the hook afterwards
      (`rules/tooling.md`). Proof: the summary of a run; a grep for `RELEASE QUALIFICATION PASS` in both scripts
      returns nothing.
- [ ] 4.4 `docs/project/validation/RELEASE_QUALIFICATION.md`: a short section "Répétition" — what it runs, what it does not
      prove, that it never closes #185 — and the warning about closing keywords in commit messages. Proof:
      `ReleaseQualificationContractTest` green unmodified (it pins four sentences of that page).
- [ ] 4.5 `docs/architecture/risks/register.md` DT-11: the rehearsal as mitigation, the debt still open.
      `docs/project/validation/VALIDATION_M20.md`: how the lane is run. `.claude/CLAUDE.md`: "M20 (release)" gains
      "lane nocturne — pas dans `clean verify`", as M19 has. Proof: `RepositoryDocumentationCoherenceTest` green.

## 5. Verification

- [ ] 5.1 `./mvnw test -pl morpheus-architecture-tests` after building `morpheus-provider-reference`. Proof: green.
- [ ] 5.2 Two consecutive dispatched runs of the lane on the branch, both platforms. Proof: the run links; durations
      recorded against the timeout.
- [ ] 5.3 Pull request description: finding covered, the corrected numbers (commits since `v1.2.0` re-counted that day),
      the decisions of `design.md`, the four unexercised steps, and the justification for the workflow and gate edits.
