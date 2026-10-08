# Spec Delta

## Purpose

Define the order in which a policy store returns the audit of policy pack writes.

## ADDED Requirements

### Requirement: The policy audit is returned in the order it was written

A policy pack store SHALL return its audit records in chronological order of their timestamp, and records with the
same timestamp in the order they were written; the memory and SQLite stores SHALL return the same order for the same
writes.

#### Scenario: Timestamps whose text forms sort differently from time

- **WHEN** two audit records are written with timestamps `T…:00Z` then `T…:00.500Z`, or `T…:00.500Z` then
  `T…:00.500100Z`
- **THEN** the store returns them in that order

#### Scenario: Records written within the same instant

- **WHEN** several audit records are written with the same timestamp
- **THEN** the store returns them in the order they were written

#### Scenario: An existing database keeps its history

- **WHEN** a database whose audit was written before this change is opened
- **THEN** its records are returned in chronological order and none is lost or rewritten in content; records it
  already holds with an identical timestamp keep a deterministic order, since their write order was never recorded
