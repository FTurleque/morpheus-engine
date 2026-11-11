# Spec Delta

## Purpose

Define how a record of the product is constructed, what an entry that supplies a default must say, and what the
repository rule against compatibility shims covers.

## ADDED Requirements

### Requirement: A record SHALL offer one public constructor

A record declared under `src/main/java` of a product module MUST NOT declare a public constructor other than its
canonical one, unless it is named, with its reason, on a list held by the architecture tests; and the build MUST fail
when a listed record no longer declares one.

#### Scenario: A second public constructor fails the build

- **WHEN** a record gains a public constructor that takes fewer arguments than it has components
- **THEN** the architecture suite fails and names the record and the constructor

#### Scenario: Removing a comment changes nothing

- **WHEN** the Javadoc of a secondary constructor no longer says "compatibility"
- **THEN** the suite still fails for that constructor

#### Scenario: A listed record is cleaned up

- **WHEN** a record on the exception list no longer declares a second public constructor
- **THEN** the suite fails and asks for its removal from the list

### Requirement: A default SHALL be obtained through a factory that names it

Where production code needs a record with some components left at a default, the record MUST expose a named static
factory whose name states the assumption, and the value produced MUST be the one the removed constructor produced.

#### Scenario: A constraint whose semantics are not known

- **WHEN** a provider builds a `Constraint` from a statement alone
- **THEN** it calls a factory whose name says the semantics are unknown, and the resulting constraint carries `UNKNOWN`
  applicability, severity and satisfaction, an unknown blocking policy and no supporting evidence, as before

#### Scenario: No entry exists for tests alone

- **WHEN** a constructor or a factory of a record is called by no production class
- **THEN** it does not exist in `src/main/java`

### Requirement: The rule against compatibility shims SHALL state what it covers

The rule page MUST state that a record has one public constructor and cite the test that enforces it, and MUST say
which retained elements are not shims: a default method of a port, and a public name whose retention is recorded in
the convergence manifest.

#### Scenario: No source claims compatibility for an element the rule allows

- **WHEN** `src/main/java` is searched for a Javadoc that justifies an element by source or backward compatibility
- **THEN** each match is either gone or is the declared stub of the convergence manifest
