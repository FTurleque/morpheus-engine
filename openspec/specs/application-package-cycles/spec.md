# application-package-cycles Specification

## Purpose
Define which dependencies between the packages of the application layer may lie on a cycle, and how that set changes.

## Requirements

### Requirement: The dependencies that lie on a cycle between application packages SHALL be named

The architecture suite MUST refuse any dependency between first-level packages of `com.morpheus.application` that
closes a cycle, read on the compiled classes, and MUST hold no list of tolerated dependencies or components.

#### Scenario: A new dependency closes a cycle

- **WHEN** a class of one application package comes to depend on a class of another that already reaches it
- **THEN** the architecture suite fails and names the cycle, package by package

#### Scenario: A new dependency between two packages already in a cycle

- **WHEN** a dependency is added between two packages that already reach each other through others
- **THEN** the suite fails in the same way

#### Scenario: A dependency outside any cycle

- **WHEN** a dependency is added that leaves the package graph acyclic
- **THEN** the suite passes

#### Scenario: The rule is proven on a known cycle

- **WHEN** the same rule is evaluated on two test packages that depend on each other
- **THEN** it fails and names both

### Requirement: The package of the store ports SHALL take part in no cycle

`com.morpheus.application.store` MUST NOT depend on an application package that depends on it, directly or through
others.

#### Scenario: A store port speaks the vocabulary of a capability

- **WHEN** an interface of `application.store` comes to depend on a type of a capability package that uses the store
  ports
- **THEN** the architecture suite fails and names the interface and the type

#### Scenario: The port lives with its capability

- **WHEN** a port is declared in the package of the capability whose types it uses
- **THEN** the suite passes, and both stores still implement it with identical behaviour

### Requirement: The architecture record SHALL state the rule and its measurement

The architecture record MUST state the rule, the measurement it started from with its date and revision, and the
moves that brought the application packages to no cycle.

#### Scenario: A reader looks for the current number of cycles

- **WHEN** a contributor reads the record or the repository's rules to know the state of the application's cycles
- **THEN** they find a dated measurement and a rule without exception, not a number presented as current
