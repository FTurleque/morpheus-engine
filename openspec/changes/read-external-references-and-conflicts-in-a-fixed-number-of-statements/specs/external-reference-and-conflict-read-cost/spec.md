## Purpose

Define what reading the external references of an owner and the composition state of a snapshot cost, and what these
reads return. Durable page: `docs/developer/specifications/external-reference-and-conflict-read-cost.md`.

## ADDED Requirements

### Requirement: Reading the external references of an owner SHALL cost a number of statements that does not depend on them

The SQLite store MUST read the external references of an owner in a snapshot, with their resolved attributes and their
resolution history, using the same number of prepared statements whatever the number of references, of resolved
references and of history events, and MUST NOT prepare a statement per reference.

#### Scenario: A few and many references prepare the same statements

- **GIVEN** an owner with two references, one resolved, and another owner with two hundred references, half resolved,
  each with several attributes and history events
- **WHEN** the references of each owner are read
- **THEN** the two reads prepare the same number of statements
- **AND** nothing is written to the database

#### Scenario: The count is checked on every build

- **GIVEN** the history of each reference is again read by a statement of its own
- **WHEN** the ordinary test suite runs
- **THEN** a test fails and states the two counts it compared

### Requirement: Reading the composition state of a snapshot SHALL cost a number of statements that does not depend on its conflicts

The SQLite store MUST read the composition state of a snapshot, with its providers, conflicts and candidates, using
the same number of prepared statements whatever the number of conflicts and of candidates per conflict, and MUST NOT
prepare a statement per conflict.

#### Scenario: Few and many conflicts prepare the same statements

- **GIVEN** a composition state with two conflicts of two candidates, and one with three hundred conflicts of three
  candidates
- **WHEN** each state is read
- **THEN** the two reads prepare the same number of statements

#### Scenario: A snapshot without composition state reads no child table

- **GIVEN** a snapshot for which no composition state is stored
- **WHEN** its composition state is read
- **THEN** the store answers that there is none, without reading the provider, conflict or candidate tables

### Requirement: The references and composition state read SHALL be the values that were stored

Each read MUST return every child value under the parent it was stored for, with resolution history ordered by event
index, attributes keyed as stored and candidates in candidate order; a reference without history or attribute MUST get
empty ones. The memory and SQLite stores MUST return equal values for the same stored state.

#### Scenario: Child values keep their parent and their order

- **GIVEN** references and conflicts stored with several attributes, history events and candidates each, in an order
  that differs from their identity order
- **WHEN** they are read through each store
- **THEN** each value is under its own parent, history and candidates keep their stored order, and the two stores
  return equal values

#### Scenario: A reference without history between two that have some

- **GIVEN** an unresolved reference without history or attribute, stored between two resolved references that have both
- **WHEN** the references of their owner are read
- **THEN** the middle reference has an empty history and no resolved target, and the two others keep theirs

#### Scenario: An unknown snapshot is still refused for references

- **GIVEN** a snapshot identity that the store does not know
- **WHEN** the external references of an owner are read in it
- **THEN** each store refuses it with `snapshot not found for external reference: <id>` and returns nothing
