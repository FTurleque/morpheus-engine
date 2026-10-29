# Spec Delta

## Purpose

Define which dependencies between the packages of the application layer may lie on a cycle, and how that set changes.

## ADDED Requirements

### Requirement: The dependencies that lie on a cycle between application packages SHALL be named

The architecture suite MUST compute, from the compiled classes, the dependencies between the first-level packages of
`com.morpheus.application` that belong to a cycle, and MUST refuse any such dependency that is not in a named list
held in the suite.

#### Scenario: A new dependency closes a cycle

- **WHEN** a class of one application package comes to depend on a class of another, and the two packages were not
  mutually reachable before
- **THEN** the architecture suite fails and names the dependency, source package to target package

#### Scenario: A new dependency between two packages already in a cycle

- **WHEN** a dependency is added between two packages that already belong to the same cycle, in a direction not named
- **THEN** the suite fails in the same way

#### Scenario: A dependency outside any cycle

- **WHEN** a dependency is added that leaves every package as reachable or unreachable from the others as before
- **THEN** the suite passes

### Requirement: The named list SHALL only shrink

The named list MUST be exactly the set the suite computes. A name whose dependency no longer lies on a cycle MUST fail
the suite until it is removed, and no threshold expressed as a number of cycles MUST be used in its place.

#### Scenario: A cycle is broken

- **WHEN** a change removes a dependency and several named dependencies no longer lie on a cycle
- **THEN** the suite fails naming each of them, and passes once they are removed from the list

#### Scenario: One cycle is traded for another

- **WHEN** a change removes one named dependency and introduces an unnamed one that lies on a cycle
- **THEN** the suite fails on the unnamed one

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

The record that deferred the cycle rule for the application MUST state the rule that replaced the deferral, the
measurement it started from with its date and revision, and where the living list is.

#### Scenario: A reader looks for the current number of cycles

- **WHEN** a contributor reads the record or the repository's rules to know the state of the application's cycles
- **THEN** they find a dated measurement and the name of the list to read, not a number presented as current
