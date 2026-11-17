## Purpose

Define what reading the traceability of a quality report costs, and what that read establishes for each requirement,
task, design decision and change. Durable page: `docs/developer/specifications/quality-report-traceability-cost.md`.

## ADDED Requirements

### Requirement: A quality report SHALL read the traceability of its snapshot a number of times that does not depend on its size

Computing the quality report of a published snapshot MUST read the traceability store a number of times that is the
same whatever the numbers of current requirements, implementation tasks, design decisions, changes and links, and MUST
NOT read the links of each subject with a read of its own.

#### Scenario: A larger snapshot costs the same traceability reads

- **GIVEN** a published snapshot with a few requirements, tasks, decisions and changes, and one with many of each,
  every kind with linked and unlinked members
- **WHEN** the quality report of each is computed
- **THEN** the two reports make the same number of reads of the traceability store
- **AND** nothing is persisted

#### Scenario: The count is checked on every build

- **GIVEN** the links of each requirement are again read with a read of their own
- **WHEN** the ordinary test suite runs
- **THEN** a test fails and states the two read counts it compared

### Requirement: The findings of a quality report SHALL be those the links of its snapshot establish

A requirement MUST be orphan exactly when no link of the snapshot has it as source or target; a task MUST be covered
exactly when its change has an `AFFECTS` link to a current requirement; a decision MUST be traced exactly when its
change has a `DECIDED_BY` link to it. The memory and SQLite stores MUST yield the same report for the same links.

#### Scenario: A requirement linked in one direction only is not orphan

- **GIVEN** a current requirement that is only the target of a link, and another that is only the source of one
- **WHEN** the quality report is computed
- **THEN** neither is reported orphan, and a third requirement without any link is

#### Scenario: A link of another snapshot does not count

- **GIVEN** a requirement linked in an earlier snapshot and without link in the published one
- **WHEN** the quality report of the published snapshot is computed
- **THEN** the requirement is reported orphan

#### Scenario: Both stores give the same report

- **GIVEN** the same snapshot and links stored in the memory store and in the SQLite store
- **WHEN** the quality report is computed on each
- **THEN** the two reports have the same findings, in the same order, and the same metrics

#### Scenario: An unknown snapshot is refused by name

- **GIVEN** a snapshot identity that no store knows
- **WHEN** a set read of its traceability is made
- **THEN** each store refuses it with `snapshot not found for traceability link: <id>` and returns nothing
