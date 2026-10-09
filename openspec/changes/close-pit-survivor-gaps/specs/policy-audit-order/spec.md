# Spec Delta

## Purpose

Define the order in which a policy store returns the audit of policy pack writes.

## ADDED Requirements

### Requirement: The policy audit is returned by instant, then by identity

A policy pack store SHALL return its audit records in chronological order of their timestamp, and records sharing a
timestamp in the order of their identity, so that the order is the same on every read; the memory and SQLite stores
SHALL apply the same order, and the public audit SHALL return the order the store returns.

#### Scenario: Timestamps whose text forms sort differently from time

- **WHEN** audit records are written with timestamps `T…:00Z`, then `T…:00.500Z`, then `T…:00.500100Z`
- **THEN** each store returns them in that order

#### Scenario: Records sharing one instant

- **WHEN** several audit records are written with the same timestamp
- **THEN** each store returns them ordered by identity, and returns the same order on every read

#### Scenario: An existing database keeps its history

- **WHEN** a database whose audit was written before this change is opened
- **THEN** its records are returned in chronological order and none is lost or rewritten, since the stored format
  does not change
