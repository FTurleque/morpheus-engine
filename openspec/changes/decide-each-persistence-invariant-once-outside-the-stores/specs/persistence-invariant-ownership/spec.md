# Spec delta : persistence-invariant-ownership

## Purpose

Define where a persistence rule that does not depend on the storage is decided, what the memory and SQLite stores keep,
and the build gate that refuses such a rule written again in a store. Durable page:
`docs/developer/specifications/persistence-invariant-ownership.md`.

## ADDED Requirements

### Requirement: A storage-independent invariant SHALL give the same refusal in both stores

A write that breaks a persistence rule decidable without the storage — revision or version step, budget per scope,
monotony of an observation, identity of a replacement, time bounds of a sync — MUST be refused by the memory store and
by the SQLite store with the same exception type and the same message, before anything is written.

#### Scenario: A freshness observation that moves backwards
- **GIVEN** a portfolio membership whose latest freshness observation is at a given instant
- **WHEN** an observation dated before it is written to the memory store and to the SQLite store
- **THEN** both refuse it as an invalid argument with the message `freshness observation must not move backwards`
- **AND** the stored observation is unchanged in both stores

#### Scenario: A saved view replacement that skips a revision
- **GIVEN** a saved view at revision 1
- **WHEN** a replacement at revision 3 is written to each store with the expected revision 1
- **THEN** both refuse it with the message `saved view replacement must advance revision by exactly one`
- **AND** neither store records a new version

#### Scenario: A sync completion before its attempt
- **GIVEN** a sync attempt of a project
- **WHEN** its completion is written to each store with a completion instant before the attempt instant
- **THEN** both refuse it with the message `completedAt must not be before attemptedAt`
- **AND** the sync state of the project is unchanged in both stores

### Requirement: A storage-independent refusal SHALL NOT be written in a store

The architecture suite MUST fail when a refusal message that both stores write is neither in the declared table of
refusals that only the storage can decide nor in the declared list of refusals awaiting their move. The failure MUST
name the message and the two store files.

#### Scenario: A rule is copied into both stores
- **GIVEN** a new refusal message of at least 25 characters written as a literal in a memory store and in its SQLite twin
- **WHEN** the architecture suite runs
- **THEN** it fails, naming the message and both files

#### Scenario: A refusal only the storage can decide
- **GIVEN** a refusal for a missing row, a stale revision or an identity collision, declared in the table with its
  category
- **WHEN** the architecture suite runs
- **THEN** the message is admitted in both stores

### Requirement: The refusals awaiting their move SHALL only decrease

The declared list of storage-independent refusals that both stores still write MUST equal the set measured on the
sources, in both directions, and its size MUST NOT exceed the ceiling its gate declares. A refusal moved out of the
stores MUST leave the list, and the ceiling MUST be lowered in the same change.

#### Scenario: A refusal is moved out of the stores
- **GIVEN** a listed refusal whose check now lives outside the stores
- **WHEN** the architecture suite runs without the list being updated
- **THEN** it fails, naming the message as listed but no longer written by both stores

#### Scenario: A refusal is added to the list
- **GIVEN** a list one entry longer than its declared ceiling
- **WHEN** the architecture suite runs
- **THEN** it fails, naming the list and its ceiling
