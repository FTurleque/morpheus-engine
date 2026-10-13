# Spec Delta

## Purpose

Define when a cost that grows with the data counts as budgeted: declared before the code that bounds it, and measured
on a fixture that holds the rows it grows with.

## ADDED Requirements

### Requirement: A cost a correction claims to bound SHALL have a budget declared before the correction

The read of a snapshot's business content, the analysis of a change and the read of a journal page MUST each have a
row in `docs/project/roadmap/M19_PERFORMANCE_BUDGETS.md`, dated, stating the operation, the fixture and the bound, and
that row MUST be committed before the code it judges is changed. The bound MUST be checked by a gate that
`validate-m19` runs on both platforms.

#### Scenario: The budget precedes the correction

- **WHEN** the history of the repository is read
- **THEN** the commit that adds each row and its gate is an ancestor of the commit that changes the code the row judges

#### Scenario: The uncorrected code is measured

- **WHEN** a new gate is first run, on the code as it was before the correction
- **THEN** its `M19_METRIC` lines and its verdict are recorded in the pull request that adds it, whether the budget
  is met or missed

#### Scenario: A missed budget fails the lane

- **WHEN** one of the three operations exceeds its declared bound on a reference environment
- **THEN** the validator exits non-zero and the nightly job of that platform ends failed

### Requirement: A budget SHALL be measured on a fixture that holds the rows the cost grows with

A gate that measures the read of a snapshot's content MUST read a snapshot holding the numbers of changes and
acceptance criteria that the frozen fixture profile declares, with at least one row in every child table of the
content projection; a gate that measures the analysis of a change MUST analyse a proposal whose dependency traversals
reach the traversal's node budget in both directions.

#### Scenario: The content fixture fills every child table

- **WHEN** the content-read gate has published its snapshot
- **THEN** each child table of the content projection holds rows for that snapshot, and the gate fails before
  measuring if one is empty

#### Scenario: The analysis fixture reaches the node budget

- **WHEN** the change-analysis gate runs its proposal
- **THEN** the analysis reports the traversal truncated at the node budget for at least one requirement in each
  direction, and the gate fails before measuring if it does not

### Requirement: A new budget SHALL NOT move an existing one

Adding a budget MUST leave every existing row of the budget document, every existing fixture size and the measurement
protocol unchanged, and the existing gates MUST measure the same content as before.

#### Scenario: The publication gate publishes what it published

- **WHEN** the publication gate runs after the change
- **THEN** it publishes the same counts of requirements, scenarios and links as before, and reports the same metric
  names

#### Scenario: The heap budget is not raised to make room

- **WHEN** the new fixtures do not fit in the heap the validator grants
- **THEN** the fixtures are reduced and their rows state the size used; the heap setting of the validator is unchanged
