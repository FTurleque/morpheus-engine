# Pull request description (task 4.5) — draft, to open once the branch is pushed

Title: `test(scripts): the validators are run for real once, and the provenance writer survives a detached HEAD (AUD-TRV-07)`

## Finding

AUD-TRV-07 reports that the scripts of `scripts/` are the repository's enforcement device and that none is tested. Read
again on `develop`, the finding held in part; the work already merged closed most of it (the validators are provoked on a
fabricated checkout, both platforms run the script tests, the inventory refuses an unlaunched or untested script, the
literal floors of `m22` to `m27` follow the living ratchets). What remained was the question the fabricated checkout
cannot answer: **does each validator that no workflow launches still run to its end on a real tree?** Nobody had run
them. This change runs them, on both platforms, and reports.

## What this change adds

- **Task 1.3**: the refusal table, `refusals-table.md` (545 `exit 1` / `throw` sites in the 26 validator files, by zone
  against the build and the packaging switch). It says which zone the tests reach and which only a real run observes.
- **Task 1.4**: the real runs, `real-validator-runs.md` (table, findings, every summary the validators wrote).
- **Task 2.1**: the comparison of the fabricated checkout with the files a real `./mvnw clean verify` leaves (in
  `tasks.md`): same root element and attributes read in the Surefire reports, same keys in the coverage evidence, same
  SBOM names, same artifact names. One file is fabricated and not left by a build: the Dependency-Check report.
- **A correction, one case first**: `write-build-provenance.ps1` called `.Trim()` on the empty output of
  `git branch --show-current` on a detached HEAD without a tag. `validate-m22.ps1` ran its whole build, printed
  `JaCoCo: PASS`, then died at that line. The `.sh` writer was not affected. Seven `.ps1` validators call the writer
  (`m21` to `m27`). New `scripts/tests/test_write_build_provenance.py`, both interpreters on the same cases; red on the old
  line, green on the new one. `scripts/README.md` lists the file.

## The real runs

`develop` at `29c942ae`, version 1.2.1, a fresh clone per platform, each validator alone with its full `clean verify`.

| target | Linux (bash, WSL) | Windows (PowerShell 5.1) |
|---|---|---|
| `d2` (no Dependency-Check scan) | PASS | PASS |
| `m21` | launched by `ci.yml` | PASS |
| `m22` | PASS | FAIL (the correction above), PASS after it |
| `m23` to `m26` | PASS | PASS |
| `r3` (with `m28`, `r2`, `m27`) | PASS | PASS |

Every run counted the same 4194 tests and 693 architecture tests against the minimums `3820 / 585`. Nothing hangs;
durations (503 s to 2149 s per target on a loaded development machine) are in `real-validator-runs.md`.

## Tests

`python -m unittest discover -s scripts/tests`: 529 cases on Windows, 1 skipped (`jq` is not installed on the machine;
with `CI` set that class fails instead of being skipped). The new file alone: 8 cases on Windows, 4 skipped by name on
Linux (the Windows PowerShell 5.1 cases), 4 run. `GateReachabilityArchitectureTest`,
`D2RepositoryHardeningArchitectureTest`, `RepositoryDocumentationCoherenceTest` and
`BuildProvenanceSbomContractTest` pass (49 cases).

## Task 3.7 — decided by the maintainer

Open question 1 (what becomes of `d2`, `m22` to `m26`, `r3`, which no workflow launched): with the results of the real
runs below, **all are kept, with the lane `validators-without-a-lane` of `nightly.yml` as it is. Nothing is retired.**

## Inventory (task 1.1, 1.2)

50 script files outside `scripts/tests/`, each launched by something and run by a test or excused by name
(`python scripts/tests/test_script_inventory.py` prints the whole table). By launcher:

| launched by | files |
|---|---|
| `ci.yml` | `check-diff-coverage.py`, `validate-m21.sh`, `validate-m28.ps1`, `classify-sonar-quality-gate.sh` |
| `nightly.yml` | `validate-m19`, `validate-m20` (both variants), `validate-d2.sh`, `validate-m22.sh` to `validate-m26.sh`, `validate-r3.sh`, `check-release-rehearsal-assets.py`, `check-spotbugs-reports.py`, `classify-sonar-quality-gate.sh` |
| `security.yml` | the four Dependency-Check scripts (`classify-`, `read-`, `write-`, `report-`) |
| `release.yml` | `validate-m28.ps1` |
| another script | `lib/*` (by the validators), `write-build-provenance.*` (by `m21` to `m27`), `verify-*.ps1` (by `m28`), `write-third-party-notices.py` (by `build-portable.*`), `validate.ps1` (by `validate.cmd`), `m27`/`r2` (by `r2`/`m28`), `validate-m28.sh`/`validate-r2.sh`/`validate-m27.sh` (by `r3.sh`) |
| by hand, named with a reason | `validate.cmd`, `verify-release-provenance.*` |

The 13 validator targets, per platform:

| target | Linux | Windows |
|---|---|---|
| `m19`, `m20` | `nightly.yml` | `nightly.yml` |
| `m21` | `ci.yml` | dispatcher only (run for real here) |
| `m28` | through `r3` (lane) | `ci.yml`, `release.yml` |
| `r2`, `m27` | through `r3` (lane) | through `m28` |
| `d2`, `m22` to `m26`, `r3` | lane `validators-without-a-lane` | dispatcher only (run for real here) |

## Governance files touched, and why

- `scripts/write-build-provenance.ps1` — the correction above, ASCII, CRLF kept.
- `scripts/tests/`, `scripts/README.md` — the new test and its line in the table.
- The change's own `tasks.md` and the three evidence files in `openspec/changes/test-the-validators-that-give-a-verdict/`.
- No ratchet, no OpenAPI file, no manifest line, no test under `morpheus-architecture-tests/` and no workflow is edited
  by this pull request. The workflow edit (the script tests on the Windows leg, the lane `validators-without-a-lane`)
  and the test of the gate-reachability family are those of the earlier commits of this change, already on `develop`.

## Not done, and why

- **Tasks 4.1 and 4.2** need this pull request's required checks: the cases run on each leg and the duration of the
  Windows step. They are filled from the pull request's runs.
- The Windows `.ps1` variants stay reachable by hand and through the dispatcher only; `nightly.yml` is Linux. The real
  runs above are the only observation of them.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
