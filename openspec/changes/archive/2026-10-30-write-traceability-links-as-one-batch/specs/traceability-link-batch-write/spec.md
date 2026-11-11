# Spec Delta

## Purpose

Define what storing a batch of traceability links guarantees, on every store, and what it costs.

## ADDED Requirements

### Requirement: A batch of links SHALL be stored entirely or not at all

A traceability store MUST store every link of a batch, or none of them, and the memory and SQLite stores MUST behave
alike.

#### Scenario: A collision in the middle stores nothing

- **WHEN** a batch is stored whose tenth link has the identity of a link already defined with a different content
- **THEN** each store refuses the batch with a failure that names the colliding identity, and none of the nine links
  before it is readable in the snapshot

#### Scenario: A link already defined identically joins the snapshot

- **WHEN** a batch holds a link that an earlier snapshot already defined with the same content
- **THEN** the link is readable in the new snapshot with the evidence it was first stored with, and is still readable
  in the earlier one

#### Scenario: A link listed twice is stored once

- **WHEN** a batch holds the same link twice, with the same content
- **THEN** the batch is accepted and the snapshot holds the link once

#### Scenario: An unknown snapshot refuses the batch

- **WHEN** a batch is stored for a snapshot that does not exist
- **THEN** each store refuses it by name and stores nothing

### Requirement: Storing a batch SHALL cost a number of prepared statements that does not depend on its size

The SQLite store MUST write a batch of links with the same number of prepared statements whether the batch holds a few
links or many, and MUST NOT look each link up with a statement of its own.

#### Scenario: A small and a large batch prepare the same statements

- **WHEN** a batch of three links is stored, then a batch of three thousand
- **THEN** the two writes prepare the same number of statements

#### Scenario: The count is checked on every build

- **WHEN** a statement is again prepared for each link of a batch
- **THEN** a test of the ordinary suite fails and states the two counts it compared

#### Scenario: The publication stays within its budget

- **WHEN** the publication gate runs on its frozen fixture
- **THEN** the publication meets the bound of `docs/project/roadmap/M19_PERFORMANCE_BUDGETS.md`, and the pull request
  states the measured time before and after
