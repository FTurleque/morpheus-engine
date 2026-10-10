# Spec Delta

## Purpose

Define when a gate, a validator or a documented verification command that the repository declares counts as
reachable: run by a real pipeline on every platform it claims, present on both platforms, and named by documentation
only in a form that resolves.

## ADDED Requirements

### Requirement: A test class that Surefire does not select SHALL be run by a validator that a workflow runs

Every test class under a module's `src/test/java` that the module's Surefire configuration does not select - by the
default includes (`Test*`, `*Test`, `*Tests`, `*TestCase`) or by the module's own `<includes>` - and that declares a test
method MUST be named in the executable text of at least one validator script on each platform, and that validator MUST
be named in the executable text of a workflow of `.github/workflows/`. Comments do not count.

#### Scenario: The five M19 budget gates are all accounted for

- **WHEN** the architecture suite lists the test classes outside Surefire's default includes
- **THEN** it finds exactly `M19PerformanceGate`, `M19QueryPerformanceGate`, `M19TraceabilityPerformanceGate`,
  `M19CompositionPerformanceGate` and `M19FullPublishPerformanceGate`, each named in `scripts/validate-m19.sh` and
  `scripts/validate-m19.ps1`, and `validate-m19` is named by a workflow

#### Scenario: A sixth gate that no validator names is refused

- **WHEN** a test class named `M19ExampleGate` with a `@Test` method is added and neither `validate-m19` script
  names it
- **THEN** the architecture suite fails and names `M19ExampleGate`

#### Scenario: A validator that no workflow runs is refused

- **WHEN** every workflow stops naming `validate-m19` in a `run:` step
- **THEN** the architecture suite fails and names the gate classes that thereby become unreachable

#### Scenario: A comment is not an invocation

- **WHEN** the only remaining mention of `validate-m19` in the workflows is inside a YAML comment
- **THEN** the architecture suite still fails

#### Scenario: A module that restricts its own includes is honoured

- **WHEN** a test class `*Test` is added to `morpheus-coverage-report`, whose POM selects only
  `**/AggregateCoverageGateTest.java`
- **THEN** the architecture suite fails and names the class, because Surefire would not run it

### Requirement: The M19 budget gates SHALL run on a schedule on both platforms and a missed budget SHALL fail the run

The scheduled workflow MUST run `scripts/validate-m19.sh` on a Linux runner and `scripts/validate-m19.ps1` on a
Windows runner, MUST upload the validator's summary, failure summary and logs (including the `M19_METRIC` lines) as
artefacts whether the run passes or fails, and MUST end failed when a validator fails. The frozen budgets, the fixture
profile and the iteration protocol of `docs/roadmap/M19_PERFORMANCE_BUDGETS.md` SHALL NOT be changed to make a run pass.

#### Scenario: A nightly run produces the evidence on both platforms

- **WHEN** the nightly workflow runs on `develop`
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

### Requirement: Pinned analysis tooling SHALL be exercised on a schedule without gating on its findings

SpotBugs (`audit-spotbugs`) MUST run over the whole reactor on a schedule in report-only mode and PIT
(`audit-mutation`) MUST run on the scope its profile declares; both MUST upload their reports as artefacts; the run MUST
fail when an analysis did not take place and MUST NOT fail because of the alerts or surviving mutants it reports.

#### Scenario: Alerts do not fail the run

- **WHEN** the scheduled SpotBugs step finds alerts in one or more modules
- **THEN** the step succeeds, the artefact holds each module's `spotbugsXml.xml` and `spotbugs.html`, and the job
  summary states the total and the count per module

#### Scenario: An analysis that did not happen fails the run

- **WHEN** a module that has classes under `target/classes` produces no SpotBugs XML, or an XML with `errors` or
  `missingClasses` greater than zero
- **THEN** the step fails and names the module, instead of reporting a clean result

#### Scenario: A PIT scope that mutates nothing fails the run

- **WHEN** the pinned PIT scope matches no covered line
- **THEN** the PIT step fails (the profile's `pit.coverageThreshold` already refuses a scope without covered lines)
  and the report is not published as a result

### Requirement: Every Maven profile of the root POM SHALL be named by a workflow or excused with a reason

The architecture suite MUST compare the profile ids declared in the root `pom.xml` with the profile names that
workflows pass to Maven, and MUST accept an unnamed profile only if it appears in a written manual-only list that gives
a reason for it.

#### Scenario: A profile that stops being run is refused

- **WHEN** the scheduled workflow stops passing `-Paudit-spotbugs`
- **THEN** the architecture suite fails and names `audit-spotbugs`, while `d2-security` and `d2-security-tests`, still
  named by `security.yml`, are not reported

#### Scenario: A profile whose name is a prefix of another is not hidden by it

- **WHEN** only `-Pd2-security-tests` is passed by the workflows
- **THEN** the architecture suite reports `d2-security` as named by no workflow

#### Scenario: A new profile that nothing runs is refused

- **WHEN** a profile `audit-example` is added to the root POM, no workflow names it and the manual-only list does not
  contain it
- **THEN** the architecture suite fails and names `audit-example`

### Requirement: A validator SHALL exist on both platforms or on neither

For every `scripts/validate-<target>.ps1` there MUST be a `scripts/validate-<target>.sh`, and for every
`scripts/validate-<target>.sh` there MUST be a `scripts/validate-<target>.ps1`; the `validate-m15` to `validate-m18`
scripts, which have no `.sh` and cannot start, SHALL be removed from `scripts/`.

#### Scenario: A lone PowerShell validator is refused

- **WHEN** a file `scripts/validate-m99.ps1` exists and `scripts/validate-m99.sh` does not
- **THEN** the architecture suite fails and names target `m99` and the missing platform

#### Scenario: A lone shell validator is refused

- **WHEN** a file `scripts/validate-m99.sh` exists and `scripts/validate-m99.ps1` does not
- **THEN** the architecture suite fails and names target `m99` and the missing platform

#### Scenario: The dispatcher offers only runnable targets

- **WHEN** `scripts\validate.cmd list` is run after the removal
- **THEN** its output holds no target among `m15`, `m16`, `m17`, `m18`, and every target it lists has both a `.ps1`
  and a `.sh`

#### Scenario: The retired validators remain recoverable

- **WHEN** a reader needs the code of a retired validator
- **THEN** `docs/validation/README.md` names the last commit that held each of the four scripts, and the validation
  records `VALIDATION_M15.md` to `VALIDATION_M18.md` are unchanged

### Requirement: A validator SHALL check the checkout it is run from

No file `scripts/validate-*.ps1` or `scripts/validate-*.sh` MUST switch or check out a literal branch name, because a
branch that disappears makes the validator unable to start.

#### Scenario: A validator that pins a branch is refused

- **WHEN** a validator script contains `git switch` or `git checkout` followed by a branch name
- **THEN** the architecture suite fails and names the script and the line

### Requirement: Documentation that is current SHALL name only commands that resolve

The current developer-facing surfaces (`docs/developer/*.md`, `scripts/README.md`, `README.md`, `docs/README.md`,
`distribution/README.md`) MUST NOT name a `validate-<target>.cmd` wrapper, which does not exist, and any
`scripts\validate.cmd <target>` they name MUST refer to an existing target. Dated records (`docs/validation/VALIDATION_*.md`,
`docs/roadmap/*_EXECUTION.md`, the ADRs) SHALL keep the command they recorded and MUST be pointed to the supported
form from `docs/validation/README.md`.

#### Scenario: A current guide cites the removed wrapper

- **WHEN** `docs/developer/POLICY_PLATFORM.md` contains `.\validate-m25.cmd`
- **THEN** the architecture suite fails and names the file and the line

#### Scenario: The M19 replay command is the one that works

- **WHEN** the M19 budgets are replayed with the command the documentation gives
- **THEN** `scripts\validate.cmd m19` is accepted by the validator, and `scripts\validate.cmd m19 -Version 1.2.1` is
  documented nowhere because the validator has no `-Version` parameter

#### Scenario: A dated record is annotated, not rewritten

- **WHEN** `docs/validation/VALIDATION_M19.md` is read after the change
- **THEN** it still shows the command that was run at the time, and a dated note next to it names
  `scripts\validate.cmd m19` as the form supported today, while `docs/validation/README.md` states the equivalence for
  every other record

#### Scenario: A guide that calls a block historical still has to resolve

- **WHEN** `docs/developer/REMOTE_SERVER_PLATFORM.md` is scanned after the change
- **THEN** it names `scripts\validate.cmd m26` and no `validate-m26.cmd`, and any dated result beside it is unchanged
