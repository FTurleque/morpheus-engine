# sqlite-store-packages Specification

## Purpose
Define the two packages of the SQLite store, which classes may reach its runtime, what its adapter package may
contain, and which modules the store may depend on.

## Requirements

### Requirement: The SQLite store SHALL be split into an adapter package and a runtime package

Every main class of `morpheus-store-sqlite` MUST reside in `com.morpheus.store.sqlite.adapter` or in
`com.morpheus.store.sqlite.runtime`. A class that implements an interface of `com.morpheus.application..` MUST reside
in the adapter package. The adapter package MUST NOT contain a top-level class that implements no such interface,
except `SqliteSnapshotBusinessContentReader` and `SqliteSnapshotBusinessContentWriter`.

#### Scenario: A helper class is added to the adapter package

- **WHEN** a top-level class that implements no interface of `com.morpheus.application..` is added to
  `com.morpheus.store.sqlite.adapter` and is not one of the two named collaborators
- **THEN** the architecture suite fails and names the class

#### Scenario: A class is left in the parent package

- **WHEN** a main class of the module resides directly in `com.morpheus.store.sqlite`
- **THEN** the architecture suite fails and names the class

### Requirement: Only the SQLite store SHALL reach its runtime package, except two named classes

A production class outside `com.morpheus.store.sqlite..` MUST NOT depend on a class of
`com.morpheus.store.sqlite.runtime`, other than `SqliteOperationRuntime` and `SqliteServerMaintenance` and their
nested types. A main source outside `morpheus-store-sqlite` MUST NOT name another class of that package, so that a
compile-time constant read across the boundary is refused as well. The exception MUST be a closed list that names
AUD-ARC-01 as the decision that removes or confirms it.

#### Scenario: A transport adapter uses the transaction runner

- **WHEN** a main class of `morpheus-api`, `morpheus-cli` or `morpheus-mcp` depends on `SqliteTransactionRunner`
- **THEN** the architecture suite fails and names both classes

#### Scenario: A transport adapter reads a constant of the runtime

- **WHEN** a main class of `morpheus-cli` reads a public compile-time constant of a runtime class other than the two
  named ones, so that no dependency is compiled
- **THEN** the textual companion of the rule fails and names the source file

#### Scenario: The named exceptions keep working

- **WHEN** `morpheus-api`, `morpheus-cli` and `morpheus-mcp` depend on `SqliteOperationRuntime` and
  `morpheus-api` and `morpheus-cli` on `SqliteServerMaintenance`
- **THEN** the rule passes

### Requirement: The SQLite store SHALL NOT depend on another persistence module or on a provider

No class of `com.morpheus.store.sqlite..` MUST depend on a class of another `com.morpheus.store..` package or of
`com.morpheus.provider..`. The rules that hold it are `storesDoNotDependOnEachOther` and
`storesDoNotDependOnProvidersTransportsOrIntegrations` of `AdapterSiblingArchitectureTest`, which select
`com.morpheus.store..` and its sub-packages, the adapter and the runtime packages included.

#### Scenario: A store borrows from the memory store

- **WHEN** a class of `com.morpheus.store.sqlite.adapter` depends on a class of `com.morpheus.store.memory`
- **THEN** the architecture suite fails and names both classes

#### Scenario: The runtime borrows from a provider

- **WHEN** a class of `com.morpheus.store.sqlite.runtime` depends on a class of `com.morpheus.provider..`
- **THEN** the architecture suite fails and names both classes

### Requirement: The relocation SHALL NOT change the behaviour of the store

Moving the classes MUST NOT change a query, a schema version, a migration resource, a refusal message or the result of
any test of `morpheus-store-sqlite`, `morpheus-store-memory` or their persistence parity tests. A visibility MUST widen
only where a class of the other package uses the type or member.

#### Scenario: The suites after the move

- **WHEN** `./mvnw clean verify` runs on the relocated module
- **THEN** every test that passed on the base passes, the executed-test counts are not lower, and neither coverage
  scale is lower than on the base
