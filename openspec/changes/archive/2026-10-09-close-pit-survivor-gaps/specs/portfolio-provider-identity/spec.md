# Spec Delta

## Purpose

Define what a portfolio membership remembers of the providers it was registered with, whichever store holds it.

## ADDED Requirements

### Requirement: A membership reads back the providers it was registered with

A portfolio store SHALL return, for a membership, exactly the set of provider identifiers the membership was
registered with — the same number of identifiers, each equal to one registered — and the memory and SQLite stores
SHALL return the same set for the same registration.

#### Scenario: A membership with several providers round-trips through SQLite

- **WHEN** a membership is registered with the providers `openspec` and `markdown` and read back from the SQLite store
- **THEN** it carries exactly those two providers

#### Scenario: An identifier carrying a separator cannot become two providers

- **WHEN** a membership is registered, through HTTP, MCP or the store directly, with one provider identifier that
  contains a line break
- **THEN** either the registration is refused with an explicit reason naming the identifier, or the membership
  reads back with that one identifier — never with two

#### Scenario: The two stores agree

- **WHEN** the same membership is registered in the memory store and in the SQLite store
- **THEN** both return the same provider set, or both refuse it with the same reason
