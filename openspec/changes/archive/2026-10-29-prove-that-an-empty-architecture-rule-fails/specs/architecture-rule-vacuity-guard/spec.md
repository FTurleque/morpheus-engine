# Spec Delta

## Purpose

Define what the architecture suite guarantees about a rule that selects no class, and what it refuses so that the
guarantee cannot be switched off.

## ADDED Requirements

### Requirement: A rule of the architecture suite that retains no class SHALL fail, and the suite SHALL prove it by evaluating one

The architecture suite MUST contain a test that evaluates, in the configuration the gates run under, a rule whose
selection retains no class, and that passes only when the evaluation fails for the reason that no class was checked.

#### Scenario: The guard is active

- **WHEN** the suite runs with the configuration of the repository
- **THEN** the vacuous rule fails for having checked no class, and the test that expects it passes

#### Scenario: The guard is disabled globally

- **WHEN** a configuration file on the test classpath, or a system property of the test run, makes ArchUnit accept a
  rule that checks no class
- **THEN** the test fails and says that a rule selecting nothing now passes

#### Scenario: The vacuous rule fails for another reason

- **WHEN** the evaluation of the vacuous rule fails because the classes could not be imported
- **THEN** the test fails: only the failure for emptiness is accepted

### Requirement: No rule of the suite SHALL opt out of failing when it retains no class

The test sources of the architecture suite MUST NOT call the per-rule opt-out that lets a rule pass on an empty
selection.

#### Scenario: A rule is allowed to be empty

- **WHEN** a rule of the suite is written with the opt-out, so that it passes while its package holds no class
- **THEN** the suite fails and names the file and the line

#### Scenario: The opt-out is only mentioned

- **WHEN** the opt-out is named in a comment or in a string of a test
- **THEN** the suite does not fail on it

### Requirement: No committed setting SHALL disable the guard for any invocation of the gates

A configuration file of the architecture rule engine, a POM, a validator or a workflow of the repository MUST NOT
set the guard to off. A configuration file that sets other properties MUST be accepted.

#### Scenario: A property file turns the guard off

- **WHEN** an `archunit.properties` anywhere in the repository sets `archRule.failOnEmptyShould` to `false`
- **THEN** the suite fails and names the file

#### Scenario: A validator passes the setting on its command line

- **WHEN** a script under `scripts/` or a workflow passes the system property that turns the guard off to Maven
- **THEN** the suite fails and names the file

#### Scenario: A property file for another setting

- **WHEN** an `archunit.properties` is committed that sets only the number of cycles to report
- **THEN** the suite passes
