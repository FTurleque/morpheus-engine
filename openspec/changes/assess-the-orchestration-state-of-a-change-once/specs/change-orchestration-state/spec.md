## ADDED Requirements

### Requirement: Building the orchestration state SHALL read the snapshot content once and assess the requested change only

Building the orchestration state of one change MUST read the business content of the `ACTIVE` snapshot once and
assess the completeness of that change only, once, whatever the number of changes in the snapshot and the number of
target states evaluated. The number of store reads of one state MUST NOT grow with either.

#### Scenario: More changes in the snapshot cost no more reads

- **GIVEN** two published snapshots, one holding the requested change alone and one holding it beside many other
  changes, each with outgoing traceability links
- **WHEN** the orchestration state of that change is built on each, with a supplied lifecycle state
- **THEN** the two builds make the same number of reads of the content, requirement and traceability stores
- **AND** nothing is persisted, and each state reports `persisted=false`

#### Scenario: Evaluating every transition reads the content once

- **GIVEN** a published snapshot and a supplied lifecycle state, so that nine target states are evaluated
- **WHEN** the orchestration state is built
- **THEN** the business content of the snapshot is read exactly once and the current requirements are listed exactly
  once

#### Scenario: The state of a change does not depend on its neighbours

- **GIVEN** the requested change with the same constraints, criteria, tasks, decisions and links in both snapshots
- **WHEN** its orchestration state is built on each
- **THEN** every field but `snapshot` is equal between the two states, `transitionEvaluations` included

#### Scenario: The count is checked on every build

- **GIVEN** a completeness report of the whole snapshot is again computed for each evaluated transition
- **WHEN** the ordinary test suite runs
- **THEN** a test fails and states the two read counts it compared

#### Scenario: A change absent from the snapshot is still refused

- **GIVEN** a published snapshot that does not contain the requested change
- **WHEN** its orchestration state is requested
- **THEN** the request is refused as a store failure that names the change, and no transition is evaluated
