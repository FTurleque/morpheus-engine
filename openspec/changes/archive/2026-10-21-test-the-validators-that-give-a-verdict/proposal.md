# Proposal

## Why

AUD-TRV-07 (sprint 8) reports that the 44 scripts of `scripts/` are the repository's enforcement device and that none
is tested except the differential coverage gate. Re-read on `develop` at `8b26d3d7` on 2026-10-13, the finding **holds
in part**: the small scripts have been tested since, the validators have not.

- `scripts/` holds 50 script files today outside `scripts/tests/`: 20 `.ps1` and 20 `.sh` at its root, 2 `.ps1` and 3
  `.sh` under `lib/`, 4 `.py`, 1 `.cmd` (the audit: 44, counting the root `.ps1` and `.sh` of its date).
- `scripts/tests/` holds 7 test files and 56 test functions, where the audit found one file. They cover the three
  Python checkers, `write-third-party-notices.py`, `classify-sonar-quality-gate.sh`, the shell ratchet reader
  `lib/read-quality-ratchet.sh`, and one step of `security.yml`.
- **No test executes a validator.** The 13 targets `validate-<target>` (26 files) and the dispatcher `validate.ps1` are
  read as text by architecture tests and executed by none. What a validator does with a failing Surefire report, a
  count below the minimum, a missing piece of evidence or a dirty tree is observed only when a workflow runs it for
  real, after a full build.
- **The script tests run on one platform.** `ci.yml:45-48` runs `python3 -m unittest discover -s scripts/tests` under
  `if: runner.os == 'Linux'`. `test_read_quality_ratchet.py:5` says of the PowerShell reader that it "has no CI lane
  and is verified by hand on Windows". Every `.ps1` of the repository is in that state.
- **A test that cannot run passes.** Three of the seven files skip their cases when `bash` or `jq` is missing
  (`unittest.skipIf`); a runner without the tool reports success.
- The four scripts of the Dependency-Check lane (`classify-dependency-check-failure.sh`, `read-`, `write-` and
  `report-dependency-check-*.sh`) are read as text by `DependencyCheckWorkflowContractTest`; no test found runs them.
- Of the 13 validator targets, the workflows launch four directly (`m19` and `m20` on both platforms, `m21` on Linux,
  `m28` on Windows) and two more through the chain of `validate-m28.ps1` (`r2`, `m27`). Seven targets — `d2`, `m22` to
  `m26`, `r3` — are launched by no workflow on either platform, and so are `validate-m21.ps1`, `validate-m28.sh`,
  `validate-r2.sh` and `validate-m27.sh`. Whether each still runs to its end is not known:
  `rehearse-the-release-chain-before-the-tag` counted them and left the question to this change.

The repository holds its code by gates and its gates by scripts. A validator that stops refusing answers PASS, and
that is how `validate-d2` compared the test count with a floor one fifth of the living one until sprint 1.

## What Changes

- The verdict of a validator is tested without building the product: on a fabricated checkout, each refusal a validator
  claims is provoked, and the absence of a PASS line and of a passing summary is asserted.
- The script tests run on Linux **and** on Windows in CI, and a case that cannot run there fails instead of being
  skipped.
- A library that exists in two languages is tested in both, on the same cases.
- An inventory says, for every script, what launches it and what tests it; a script that nothing launches, or that
  nothing tests without a written reason, fails the build.
- The validators no workflow launches are run once on each platform, and each gets a decision: a lane, a test, or
  retirement as `validate-m15` to `validate-m18` were retired.

## Capabilities

### New Capabilities

- `validator-self-test`: the guarantee that a script giving a verdict on the repository is itself exercised, on both
  platforms, and that every script is launched by something and tested or excused by name.

### Modified Capabilities

(none — `gate-reachability` says which gates a workflow must reach, `gate-verdict-currency` says where a validator
reads its minimums; this change tests that they do, it does not change what they must do)

## Impact

- `scripts/tests/` (new tests and their fabricated checkout), `.github/workflows/ci.yml` (one step on the Windows leg,
  the existing step renamed), `scripts/README.md`.
- The validators change only where a test shows a refusal that does not happen; each such correction is named in the
  pull request. No product code.
- One rule in the gate-reachability family of `morpheus-architecture-tests` (the inventory).
- No ADR expected; the retirement of a validator, if the maintainer chooses it, follows the precedent recorded in
  `docs/project/validation/README.md` and the constraints of the milestone quadruplet.
- After `make-declared-gates-reachable` and `make-gate-verdicts-follow-living-sources` (sprint 1, archived); takes the
  table of task 2.3 of `rehearse-the-release-chain-before-the-tag` as input.
