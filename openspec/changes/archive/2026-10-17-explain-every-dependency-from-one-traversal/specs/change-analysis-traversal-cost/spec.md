# Spec Delta

## Purpose

Define what explaining the dependencies of a change costs, and that the explanation does not change.

## ADDED Requirements

### Requirement: Explaining the dependencies of a change SHALL cost no more store reads than finding them

For each changed requirement and each direction, the analysis of a change MUST read the traceability store a number of
times that grows at most in proportion to the number of nodes its traversal discovers, and MUST NOT search the graph
again for each node discovered.

#### Scenario: Twice the dependencies, at most twice the reads

- **WHEN** a change is analysed over a dependency chain of a given length, then over a chain twice as long, within the
  requested depth
- **THEN** the second analysis reads the traceability store at most twice as often as the first, plus a constant

#### Scenario: The count is checked on every build

- **WHEN** a search of the graph is again started for each discovered node
- **THEN** a test of the ordinary suite fails and states the two counts it compared

#### Scenario: The time is held by the budget

- **WHEN** the change-analysis gate runs on the fixture its budget declares
- **THEN** the analysis meets the bound of `docs/project/roadmap/M19_PERFORMANCE_BUDGETS.md`

### Requirement: The analysis of a change SHALL return what it returned

The dependency impacts, their explanation paths, their order and the warnings of an analysis MUST be the same as
before this change for the same snapshot, proposal and depth.

#### Scenario: Two shortest paths lead to one entity

- **WHEN** an impacted entity can be reached from the changed requirement by two paths of the same length
- **THEN** the analysis explains it by the same path as before, on every run

#### Scenario: A path through an unresolved link is still reported

- **WHEN** the explanation path of an impacted entity holds a link that is not resolved
- **THEN** the analysis carries the warning `TRACEABILITY_PATH_PARTIALLY_RESOLVED` for it, with the same depth and
  target as before

#### Scenario: A truncated traversal still explains what it found

- **WHEN** the dependency traversal stops at its node budget or at its link budget
- **THEN** the analysis carries the warning `TRACEABILITY_TRAVERSAL_TRUNCATED` with the same reason as before, and
  every entity it reports has its explanation path

### Requirement: Answers that embed a traversal SHALL NOT change shape

The answers of `trace-requirement` and `change-context`, on every transport that serves them, MUST be identical to
those returned before this change for the same request.

#### Scenario: The trace of a requirement is unchanged

- **WHEN** the trace of a requirement and the context of a change are read through the CLI, HTTP and MCP after the
  change
- **THEN** each answer has the same keys and the same values as before
