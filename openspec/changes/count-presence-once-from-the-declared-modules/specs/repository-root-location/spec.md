# Spec delta : repository-root-location

## ADDED Requirements

### Requirement: A validator SHALL read only the POMs and the Surefire reports of the modules the root POM declares

A script of `scripts/` that counts reactor POMs or Surefire executions MUST read the root POM and, for each module the
root POM declares, that module's POM and its `target/surefire-reports`. It MUST NOT walk the repository root
recursively, so a workspace copy under `.claude/worktrees` or a nested checkout changes none of its counts.

#### Scenario: A workspace copy does not fail D2

- **GIVEN** a full copy of the repository under `.claude/worktrees/<name>` with its own POMs and Surefire reports
- **WHEN** `validate-d2.sh` or `validate-d2.ps1` runs on a sound build of the repository
- **THEN** the reactor POM check passes and names no POM of the copy
- **AND** the written, generated and architecture counts equal those of the repository alone

#### Scenario: The executions of a copy do not hide a drop

- **GIVEN** a repository whose written count is one below `testsMinimum`, and a workspace copy holding enough reports
  to make up the difference
- **WHEN** `validate-m21` runs on either platform
- **THEN** it refuses, naming `testsMinimum`, the observed count of the repository and the required count
- **AND** it writes no passing `validation-summary.txt`

#### Scenario: A module that produced no report is not filled from elsewhere

- **GIVEN** a declared module without `target/surefire-reports` and a copy of that module's reports under
  `.claude/worktrees`
- **WHEN** a validator counts the executions
- **THEN** the module contributes no execution to the count

#### Scenario: A recursive walk of the root in a script is refused

- **GIVEN** a script of `scripts/` edited to read `rglob(` or `Get-ChildItem -Recurse` from the repository root
- **WHEN** the architecture suite runs
- **THEN** the build fails and names the script and the line
