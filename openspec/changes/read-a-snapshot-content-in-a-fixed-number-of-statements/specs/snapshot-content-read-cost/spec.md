# Spec Delta

## Purpose

Define what reading the business content of a snapshot costs, and what it returns.

## ADDED Requirements

### Requirement: Reading a snapshot's content SHALL cost a number of statements that does not depend on its size

The SQLite store MUST read the business content of a snapshot with a number of prepared and executed statements that
is the same for a snapshot holding one element of each kind and for a snapshot holding many.

#### Scenario: Two snapshots of different sizes cost the same

- **WHEN** the content of a snapshot holding one scenario, change, constraint and acceptance criterion is read, then
  the content of a snapshot holding a hundred of each, every one with several ordered values
- **THEN** the two reads prepare the same number of statements and execute the same number of queries

#### Scenario: The count is checked on every build

- **WHEN** a child value is again read by a query issued per parent row
- **THEN** a test of the ordinary suite fails, on both platforms, and states the two counts it compared

#### Scenario: The time is held by the budget

- **WHEN** the content-read gate runs on the fixture its budget declares
- **THEN** the read meets the bound of `docs/project/roadmap/M19_PERFORMANCE_BUDGETS.md`

### Requirement: The content read SHALL be the value that was stored

The read MUST return the same elements in the same order as before this change, each with its ordered values in the
order of their ordinal, and the memory and SQLite stores MUST return equal values for the same stored content.

#### Scenario: Ordered values keep their order and their owner

- **WHEN** a snapshot is stored whose changes have scope, out-of-scope and risk values, whose constraints have blocking
  targets and supporting evidence, whose scenarios have preconditions and whose criteria have verification evidence,
  with several values per owner
- **THEN** each store returns every value under the owner it was stored for, in the order it was stored

#### Scenario: An owner without values gets an empty list

- **WHEN** a change is stored with no risk and a scenario with no precondition
- **THEN** the content read holds an empty list for each, and the other owners keep their values

#### Scenario: A snapshot without content is still absent

- **WHEN** the content of a snapshot that has no content projection is read
- **THEN** the store answers that there is none, without reading any child table
