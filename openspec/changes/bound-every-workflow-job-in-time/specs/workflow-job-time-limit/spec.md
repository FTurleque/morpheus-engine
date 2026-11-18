# Spec delta : workflow-job-time-limit

## Purpose

Guarantee that no job of the repository's GitHub Actions workflows can hold a runner, or a required check, until the
platform default expires. Durable page: `docs/developer/specifications/workflow-job-time-limit.md`.

## ADDED Requirements

### Requirement: Every job of a workflow SHALL declare its own time limit

Every job of every workflow under `.github/workflows/` MUST declare `timeout-minutes` at job level, and a contract test
MUST fail the build, naming the workflow and the job, when one does not. A job that only calls a reusable workflow is
outside the rule, since GitHub Actions does not accept the key there.

#### Scenario: The required verification job is bounded

- **GIVEN** the `verify` job of `ci.yml`, whose Windows leg builds and installs the real setup program
- **WHEN** a step of that leg blocks
- **THEN** the job is stopped at its declared limit and the check `exact-head (windows-latest)` fails
- **AND** the runner is not held until the platform default of 360 minutes

#### Scenario: A job without a limit is refused

- **GIVEN** a workflow of `.github/workflows/` with a job that declares no `timeout-minutes`
- **WHEN** the architecture suite runs
- **THEN** the build fails and the message names the workflow file and the job
- **AND** no job that declares a limit is named

#### Scenario: A limit declared on a step only is not enough

- **GIVEN** a job whose steps declare `timeout-minutes` but whose job level does not
- **WHEN** the architecture suite runs
- **THEN** the build fails and names that job

#### Scenario: A new workflow is covered without editing the test

- **GIVEN** a workflow file added under `.github/workflows/` with one job without a limit
- **WHEN** the architecture suite runs
- **THEN** the build fails and names the new file and its job
