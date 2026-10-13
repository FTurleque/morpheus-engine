# Tasks

## 1. Measure before changing

- [ ] 1.1 List every script file under `scripts/` outside `scripts/tests/` with what launches it (workflow step, other
      script, dispatcher, current page) and what tests it (a test that runs it, a test that reads it, nothing). Proof:
      the table. On 2026-10-13 at `8b26d3d7`: 50 files; the audit counted 44. Re-measure, trust neither.
- [ ] 1.2 List the validator targets and, per platform, the workflow step or chain that launches each. Proof: the table,
      compared with the one produced by task 2.3 of `rehearse-the-release-chain-before-the-tag`. On 2026-10-13: 13
      targets, of which `m19`, `m20`, `m21` (Linux), `m28` (Windows), and by chain `r2` and `m27` (Windows).
- [ ] 1.3 List the refusals each validator claims (its `exit 1` and `throw` sites) and the files it reads. Proof: the
      table from which the fabricated checkout and the cases are written.
- [ ] 1.4 Run once, on Windows and on Linux, each validator that no workflow launches on that platform. Proof: per
      target and platform, the exit code and the `validation-summary.txt` or `failure-summary.txt`. A validator that
      does not finish is a finding, reported before anything is repaired.
- [ ] 1.5 Find whether any test runs the four scripts of the Dependency-Check lane, or only reads them. Proof: the test
      and the line that launches the process, or the statement that none does.

## 2. Reproduce before fixing

- [ ] 2.1 Build the fabricated checkout and compare the files it holds with those a real `./mvnw clean verify` leaves
      (names, first lines, attributes read). Proof: the comparison.
- [ ] 2.2 Write the cases of the contract for `validate-m21` on both platforms. Proof: the result on `HEAD`; a refusal
      that does not happen is a defect of the validator, listed.
- [ ] 2.3 Run the shared cases of the ratchet reader against `Read-QualityRatchets.ps1`. Proof: the result on `HEAD`.
- [ ] 2.4 Write the inventory rule with no excuse. Proof: red, naming the scripts of 1.1 that nothing runs in a test.

## 3. The change

- [ ] 3.1 Extend the cases to every target that can be run on the fabricated checkout; excuse the others by name with
      the reason. Proof: per target, the cases executed on each platform.
- [ ] 3.2 The dispatcher's tests.
- [ ] 3.3 The shared lists of cases for the libraries that exist in two languages.
- [ ] 3.4 `ci.yml`: run the script tests on the Windows leg; rename the existing step for what it runs. Make a missing
      tool a failure in CI. Check what `AuditHardeningWorkflowContractTest`, `D2RepositoryHardeningArchitectureTest`
      and `GateReachabilityArchitectureTest` assert about `ci.yml` before editing it.
- [ ] 3.5 The inventory rule and its excuses. Mechanism: textual assertion, intent "a script that nothing launches, or
      that nothing tests and nobody excused, must not be in `scripts/`" (ADR-0103; skill `enforcement-choice`). Add the
      rule that each leg runs the script tests.
- [ ] 3.6 Correct each validator in which 2.2 or 3.1 showed a refusal that does not happen; one commit per correction,
      with its case.
- [ ] 3.7 Put the decision of open question 1 to the maintainer with the results of 1.4, target by target. A retirement
      follows `milestone-quadruplet` and the precedent of `docs/project/validation/README.md`.
- [ ] 3.8 Break each rule once, then revert: remove a comparison from a validator; remove the step from one leg; add an
      untested script; move a launch into a comment; leave an excuse for a tested script. Proof: the named failures.

## 4. Verification and destinations

- [ ] 4.1 The pull request's required checks on both platforms. Proof: the script tests executed on each leg, with the
      number of cases run and none skipped.
- [ ] 4.2 Measure the duration of the new step on the Windows leg. Proof: the figure, in the pull request.
- [ ] 4.3 Every `.ps1` added or edited is ASCII (`rules/tooling.md`); the hook's check is run.
- [ ] 4.4 `scripts/README.md`: how the validators are tested, how to add a case, how to excuse a script. `.claude/rules`
      is touched only if a sentence there describes the old state.
- [ ] 4.5 Pull request description: the finding, the tables of 1.1 and 1.2, the validators corrected, the excuses and
      their reasons, the justification of the test added under `morpheus-architecture-tests/` and of the workflow edit.
