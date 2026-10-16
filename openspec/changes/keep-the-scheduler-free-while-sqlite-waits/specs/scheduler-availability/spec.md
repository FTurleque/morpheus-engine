# Spec Delta

## Purpose

Define what the servers still do while the database makes its callers wait.

## ADDED Requirements

### Requirement: A waiting database SHALL NOT stop the server from answering what does not need it

While requests are blocked on the database, a request that needs no database MUST be answered within a stated bound, on
both servers.

#### Scenario: Health answers while a writer holds the database

- **WHEN** a writer holds the database for longer than the busy timeout and more reads are in flight than there are
  processors
- **THEN** a health request is answered within the stated bound

#### Scenario: The reads end

- **WHEN** the writer releases the database
- **THEN** every read in flight completes or is refused with a named refusal, and none is left waiting

### Requirement: The remedy SHALL be chosen from a measurement and recorded

The pinning of carrier threads MUST be measured before the concurrency model is changed, and the chosen remedy MUST be
recorded with the measurement in a decision record.

#### Scenario: The measurement exists

- **WHEN** the decision record is read
- **THEN** it states, per platform, the processor count, the carriers held and the latency of a request that needs no
  database, before and after

### Requirement: A monitor on a database path SHALL say what it protects

Every `synchronized` method or block of the SQLite store that remains MUST be justified by a test or a written reason,
and one that protects nothing MUST be removed.

#### Scenario: A monitor without a reason is refused

- **WHEN** a `synchronized` method is added to a store without appearing in the list of justified monitors
- **THEN** the architecture suite fails and names it
