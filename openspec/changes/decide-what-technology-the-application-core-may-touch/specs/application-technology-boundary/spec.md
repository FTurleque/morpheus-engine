# Spec Delta

## Purpose

Define which technology the application layer may reach, through which classes, and where that is written.

## ADDED Requirements

### Requirement: The architecture record SHALL say what the application layer may touch

An architecture decision record MUST state, for each technology family - outbound network, file access, process
environment, standard streams, threads, SQL, JSON library, process execution, class loading - whether
`com.morpheus.application` may use it, and MUST name every class that does today with what was decided for it: it
stays, or it leaves.

#### Scenario: A reader asks whether a use case may open a file

- **WHEN** a contributor reads the record to know whether a new application class may read the workspace
- **THEN** the record answers for that family, and names the classes already allowed to

#### Scenario: A class that does I/O has no decision

- **WHEN** a class of the application reaches a confined family and the record does not name it
- **THEN** the architecture suite fails, and the record cannot be said to describe the code

### Requirement: A technology the application does not use SHALL be refused in all of its packages

A family the record states as unused MUST be refused for every class under `com.morpheus.application`, not only for
the packages that carried a ban before the record.

#### Scenario: A use case acquires a database dependency

- **WHEN** a class of any application package comes to depend on `java.sql`
- **THEN** the architecture suite fails and names the class and the family

#### Scenario: The stricter bans of the deterministic cores still hold

- **WHEN** the suite runs after this change
- **THEN** the textual assertions on `application.reasoning` and `application.policy` are still present and still pass

### Requirement: A technology the application does use SHALL be confined to named classes

A family in use MUST be reachable only from the classes the record names for it. The named set MUST be exactly the set
that reaches the family: it may shrink, and it MUST NOT grow without a consolidated record replacing it.

#### Scenario: A second class opens an outbound connection

- **WHEN** a class other than the named ones comes to depend on the HTTP client
- **THEN** the architecture suite fails and says the class is not named by the record

#### Scenario: A named class no longer reaches the family

- **WHEN** a named class stops using the family it is named for
- **THEN** the suite fails until the name is removed, so the list only shrinks

#### Scenario: A path is not an access

- **WHEN** an application class uses `java.nio.file.Path` as a value without opening, listing or writing anything
- **THEN** the suite does not count it as file access

### Requirement: The descriptions of the application layer SHALL say what is enforced

The repository's instructions and its architecture pages MUST describe the application layer as the record decides it,
and MUST NOT present as technology-free a layer the record allows to do I/O.

#### Scenario: The record allows local file access

- **WHEN** the record keeps a family in the application
- **THEN** the diagram caption, the rules page and the building-block view say so and point to the record
