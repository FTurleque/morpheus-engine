# Spec Delta

## Purpose

Define what a domain value looks like when a transport shows it, who owns that shape, and what proves that two
transports show the same one.

## ADDED Requirements

### Requirement: A domain type shown on more than one transport SHALL have one public shape

For each domain type that more than one of the HTTP, MCP and CLI surfaces shows, the keys of its public shape and the
value given to each key MUST be defined once, and every transport that shows the type MUST use that definition. The
list of such types MUST be explicit and MUST state, for each, the transports that show it.

#### Scenario: A requirement on two transports

- **WHEN** the same published requirement is read through the HTTP surface and through the MCP surface
- **THEN** both answers carry the same keys with the same values for that requirement

#### Scenario: A field is added to a shared shape

- **WHEN** a key is added to the public shape of a shared domain type
- **THEN** every transport that shows the type carries it, without a change to an adapter

### Requirement: The transports SHALL be compared on the content of their answers

The architecture suite MUST, for one published fixture, obtain each shared domain type through every transport that
shows it and fail when two transports disagree on a key or on a value, naming the domain type, the key and the two
transports. What each transport wraps around the shape MUST be named by the comparison, not ignored silently.

#### Scenario: One transport alters a shape on its way out

- **WHEN** an adapter renames or drops a key of a shared shape before answering
- **THEN** the architecture suite fails and names the domain type, the key and the two transports

#### Scenario: A shared type nobody compares

- **WHEN** a domain type is declared as shown on two transports and the comparison does not exercise it
- **THEN** the architecture suite fails and names the type

### Requirement: A transport adapter SHALL NOT declare its own projection of a shared domain type

The architecture suite MUST fail when a type of a transport adapter declares a method that takes a shared domain type
and returns its payload, whatever the method is called, and MUST name the type and the method.

#### Scenario: A pasted projection

- **WHEN** an adapter class declares a private method that turns a requirement into a map
- **THEN** the architecture suite fails and names the class and the method

#### Scenario: An adapter-only type

- **WHEN** an adapter projects a type that only its own transport shows
- **THEN** the suite does not fail

### Requirement: Sharing the shapes SHALL NOT change a public answer

Moving the projections MUST leave every HTTP answer and every MCP tool result byte-identical, and MUST NOT require a
change to the convergence manifest or to an OpenAPI document.

#### Scenario: The adapter suites and the contracts

- **WHEN** the adapter suites and the route convergence gates run after the move
- **THEN** they pass with no expected payload, manifest line or OpenAPI operation edited
