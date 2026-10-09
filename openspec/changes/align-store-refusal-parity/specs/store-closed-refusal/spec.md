# Spec Delta

## Purpose

Define what a SQLite store does when it is used after being closed, so that every store refuses the same way and a
caller can handle a closed store with one rule.

## ADDED Requirements

### Requirement: A closed SQLite store refuses every operation as a storage failure

Every SQLite store SHALL refuse every operation called after it was closed with the storage-failure refusal the
transports already map, carrying a message that names the store and no underlying cause, before touching the
database.

#### Scenario: Any operation after close

- **WHEN** any read or write operation of a SQLite store is called after the store was closed
- **THEN** it is refused as a storage failure whose message names the store as closed, and the refusal has no cause

#### Scenario: The same refusal for every store

- **WHEN** each SQLite store is closed and then used
- **THEN** all of them refuse with the same refusal type, so one handler covers every store

#### Scenario: Closing twice

- **WHEN** a closed SQLite store is closed again
- **THEN** the second close succeeds without effect
