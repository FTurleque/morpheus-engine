## Purpose

Define what one governance evaluation costs in store reads, for the reports its rules share and for the overrides of
its scope. Durable page: `docs/developer/specifications/policy-evaluation-cost.md`.

## ADDED Requirements

### Requirement: An evaluation SHALL read the overrides of its scope once

A governance evaluation, a pack evaluation and a dry-run MUST read the overrides of their scope with one store read,
whatever the number of packs and rules they evaluate, and MUST NOT read an override per rule. The override applied to
each rule MUST be the one stored for its pack and rule identity.

#### Scenario: Many rules, one override read

- **GIVEN** a project scope with two active packs of many rules each, and overrides stored for some of their rules
- **WHEN** the scope is evaluated
- **THEN** the policy store is asked for the overrides of the scope once, and never for the override of one rule
- **AND** nothing is persisted and no audit record is written

#### Scenario: Each rule receives its own override

- **GIVEN** a `FORCE_WARN` override on one rule and a `DISABLE` override on a rule of the same identity in another pack
- **WHEN** the scope is evaluated
- **THEN** each rule reports the effective decision and the reason suffix of its own override, and the rules without
  override report their source decision

### Requirement: An evaluation SHALL compute each report its quality and lifecycle rules share once

Within one evaluation, the quality report and the composition state of the project's `ACTIVE` snapshot, and the
content and current requirements a lifecycle guard reads, MUST each be computed at most once, and the completeness of a
change at most once per change named by a lifecycle guard. The store reads of an evaluation MUST NOT grow with the
number of quality thresholds or of lifecycle guards on one change.

#### Scenario: A pack of quality rules computes one report

- **GIVEN** a pack of one `QUALITY_THRESHOLD` rule, and a pack of many such rules on several metrics
- **WHEN** each pack is evaluated on the same project
- **THEN** the two evaluations make the same number of reads of the content, requirement and traceability stores
- **AND** each rule reports the same decision, observed value and evidence as when it is evaluated alone

#### Scenario: Lifecycle guards on one change share their inputs

- **GIVEN** several `LIFECYCLE_GUARD` rules naming the same change with different target states
- **WHEN** their pack is evaluated
- **THEN** the business content is read once and the traceability of that change is read once

#### Scenario: No active snapshot stays unknown

- **GIVEN** a project without `ACTIVE` snapshot and a pack of quality and lifecycle rules
- **WHEN** the pack is evaluated
- **THEN** each of those rules has the source decision `UNKNOWN` with its existing reason, and the aggregate decision
  is `UNKNOWN`, never `PASS` nor `BLOCK`

#### Scenario: The count is checked on every build

- **GIVEN** a report is again computed for each rule that reads it
- **WHEN** the ordinary test suite runs
- **THEN** a test fails and states the two read counts it compared
