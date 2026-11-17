## Purpose

Define what reading the outgoing or incoming traceability links of an endpoint costs, and what the read returns.
Durable page: `docs/developer/specifications/traceability-link-read-cost.md`.

## ADDED Requirements

### Requirement: Reading the links of an endpoint SHALL cost a number of statements that does not depend on the links returned

The SQLite store MUST read the outgoing or incoming links of an endpoint, with their evidence, using the same number
of prepared statements whatever the number of links returned and of evidence per link, and MUST NOT prepare a
statement per link. A read that returns links MUST NOT also check that the snapshot exists.

#### Scenario: A small and a large read prepare the same statements

- **GIVEN** an endpoint with three outgoing links of one evidence each, and another with three hundred outgoing links
  of four evidence each, in the same snapshot
- **WHEN** the outgoing links of each are read, then the incoming links of their targets
- **THEN** each pair of reads prepares the same number of statements
- **AND** nothing is written to the database

#### Scenario: The count is checked on every build

- **GIVEN** the evidence of each link is again read by a statement of its own
- **WHEN** the ordinary test suite runs
- **THEN** a test fails, on both platforms, and states the two counts it compared

### Requirement: A read of endpoint links SHALL return every link with all its evidence

An outgoing or incoming read MUST return the links of the endpoint in the snapshot, ordered by link id, each with the
complete set of its evidence identities; the memory and SQLite stores MUST return equal lists for the same stored
links. An unknown snapshot MUST be refused by name, and a known snapshot without link MUST yield an empty list.

#### Scenario: Several evidence per link are all returned

- **GIVEN** links stored with one, two and five evidence identities, and one link shared by two snapshots
- **WHEN** their endpoint is read in each snapshot, through each store
- **THEN** each link carries exactly the evidence it was stored with, and the two stores return equal lists

#### Scenario: An unknown snapshot is still refused

- **GIVEN** a snapshot identity that the store does not know
- **WHEN** the outgoing links of an endpoint are read in it
- **THEN** the store refuses with `snapshot not found for traceability link: <id>`

#### Scenario: A known snapshot without link answers empty

- **GIVEN** a published snapshot in which the endpoint has no link
- **WHEN** its outgoing and incoming links are read
- **THEN** both reads return an empty list and raise no failure
