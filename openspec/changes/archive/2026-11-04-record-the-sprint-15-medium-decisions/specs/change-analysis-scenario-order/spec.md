## Purpose

Define the order in which a change analysis returns the current and proposed scenarios of a requirement.

## ADDED Requirements

### Requirement: The scenarios of an analysis SHALL follow their source key, never their generated identity

The current and the proposed scenarios of an analysed requirement MUST be ordered by their source key, then by their
title, and only then by their identity. Identities are generated at publication, so two scenarios published in the
same millisecond carry identities in no meaningful order, and an order taken from them changes from one publication of
the same source to the next.

#### Scenario: Two scenarios published in the same millisecond

- **WHEN** the same change is analysed after two publications of the same source
- **THEN** its proposed scenarios come in the same order both times

#### Scenario: Identities opposed to source keys

- **WHEN** the scenario with the lower identity has the higher source key
- **THEN** it comes second

#### Scenario: Scenarios without a source key

- **WHEN** scenarios carry no source key
- **THEN** they follow their title, and two of the same title follow their identity
