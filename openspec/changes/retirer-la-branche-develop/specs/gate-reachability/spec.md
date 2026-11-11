# Spec delta : gate-reachability

## MODIFIED Requirements

### Requirement: The M19 budget gates SHALL run on a schedule on both platforms and a missed budget SHALL fail the run

The scheduled workflow MUST run `scripts/validate-m19.sh` on a Linux runner and `scripts/validate-m19.ps1` on a
Windows runner, MUST upload the validator's summary, failure summary and logs (including the `M19_METRIC` lines) as
artefacts whether the run passes or fails, and MUST end failed when a validator fails. The frozen budgets, the fixture
profile and the iteration protocol of `docs/project/roadmap/M19_PERFORMANCE_BUDGETS.md` SHALL NOT be changed to make a run pass.

#### Scenario: A nightly run produces the evidence on both platforms

- **WHEN** the nightly workflow runs on a schedule, on the commit of the default branch `main`
- **THEN** a Linux job and a Windows job each execute the five gates, and each uploads an artefact holding
  `validation-summary.txt` with the measured `M19_METRIC` values

#### Scenario: A budget miss fails the lane

- **WHEN** a budget constant of a gate is lowered below its measured value in a throw-away change and the validator is
  run
- **THEN** the validator exits non-zero, `failure-summary.txt` names the stage "M19 performance gates", and the job
  of that platform ends failed

#### Scenario: A runner below the reference environment gives no verdict

- **WHEN** the validator runs on a machine with fewer than four logical processors or less than 8 GiB of memory
- **THEN** it refuses to report a budget verdict and the job fails with the reference-environment stage named, rather
  than passing or reporting a miss

### Requirement: The release build SHALL be rehearsed on a schedule on both platforms and a failed rehearsal SHALL fail the run

A workflow MUST run the M20 release validator on Linux and on Windows on a schedule and on demand, through the same
`distribution/build-release` scripts the release workflow calls, and a validator that fails MUST fail its job.

#### Scenario: Both platforms rehearse the release build

- **WHEN** the nightly workflow runs on a schedule
- **THEN** a Linux job runs `scripts/validate-m20.sh` and a Windows job runs `scripts/validate-m20.ps1`, each with the
  current product version, each producing the platform's archive, checksum and release manifest for the checked-out
  commit

#### Scenario: A broken Linux packaging is found without a tag

- **WHEN** a change makes `distribution/build-portable.sh` fail and no tag is pushed
- **THEN** the next rehearsal fails on Linux and names the failing stage

#### Scenario: A rehearsal can be run on a branch before a merge

- **WHEN** the workflow is dispatched manually on a branch
- **THEN** it rehearses that branch's commit, not the commit of the default branch `main`

#### Scenario: The validator is given the current version

- **WHEN** the lane invokes the validator
- **THEN** the version argument is the one the root POM declares, and a validator left on its dated default fails on the
  version check instead of passing on an old value
