# Spec Delta

## Purpose

Define how a policy pack store refuses an invalid write — a missing row, a stale or skipped revision, a mismatched
audit — so that the memory store and the SQLite store give the same refusal for the same write.

## ADDED Requirements

### Requirement: Removing a missing row is refused as not found

A policy pack store SHALL refuse the removal of an activation or of an override that does not exist with a not-found
refusal that names the missing row, and the memory and SQLite stores SHALL give the same refusal type and message.

#### Scenario: An activation that does not exist

- **WHEN** an activation that does not exist is removed directly from a store, with a well-formed audit record
- **THEN** the store refuses it as not found, naming the pack, writes nothing, and records no audit

#### Scenario: An override that does not exist

- **WHEN** an override that does not exist is removed directly from a store, with a well-formed audit record
- **THEN** the store refuses it as not found, naming the rule, writes nothing, and records no audit

### Requirement: A write is checked in one order

A policy pack store SHALL check a write in this order: the target of the audit record (action, pack, rule, scope);
then the stored state, existence before revision; then the version the audit record names, which depends on the stored
row. The first failed check SHALL decide the refusal, in both stores.

#### Scenario: A mismatched audit on a missing row

- **WHEN** a missing row is removed with an audit record whose target does not match the write
- **THEN** both stores refuse the write as an audit target mismatch, not as not found

#### Scenario: A stale revision with a well-formed audit

- **WHEN** an existing row is written with an expected revision that is not its current one
- **THEN** both stores refuse the write as a revision conflict naming the expected and current revisions

### Requirement: A skipped revision step is refused before anything is written

A policy pack store SHALL refuse a compare-and-set write whose replacement does not advance the revision, or the latest
version, by exactly one, with the same invalid-argument refusal and message in both stores, before writing anything.

#### Scenario: An update that skips a revision

- **WHEN** a pack definition is replaced with a revision two steps above the current one
- **THEN** both stores refuse it with the same invalid-argument message, and the stored definition, its versions and
  its audit are unchanged
