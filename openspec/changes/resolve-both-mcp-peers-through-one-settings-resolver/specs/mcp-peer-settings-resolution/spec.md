# Spec Delta

## Purpose

Define how the launch settings of an MCP peer are resolved from the environment and the JVM properties, for every peer
alike, and what refuses an integration that resolves them on its own.

## ADDED Requirements

### Requirement: The launch settings of every MCP peer SHALL be resolved by one implementation

The JAR path, its SHA-256 pin, the launcher, the home directory and the time bound of an MCP peer MUST be resolved,
validated and bounded by a single implementation that takes the peer as a parameter. An integration MUST contribute
only the peer's name and the names of its settings.

#### Scenario: The same malformed configuration for both peers

- **WHEN** MINOS and NEXUS are each configured with a JAR path that is not a file
- **THEN** both resolve to an invalid configuration whose error differs only by the peer's name

#### Scenario: A hardening is added once

- **WHEN** a new validation of the pin or of the launcher is added to the resolution
- **THEN** it applies to both peers without a change to either integration

### Requirement: The resolution SHALL keep every existing setting, state and refusal

For each peer, the names of its properties and environment variables, the precedence between them, the default and
maximum time bound, the three states (disabled, configured, invalid) and the rule that external code runs only when
the JAR is pinned MUST be unchanged. A configuration problem MUST NOT prevent MORPHEUS from starting.

#### Scenario: A pin without a JAR

- **WHEN** a peer's SHA-256 pin is set and its JAR is not
- **THEN** the configuration is invalid, the error names both settings of that peer, and MORPHEUS starts

#### Scenario: A JAR without a pin

- **WHEN** a peer's JAR is set and its pin is not
- **THEN** the configuration is valid and the peer is not started

#### Scenario: A time bound out of range

- **WHEN** a peer's time bound is set above the maximum
- **THEN** the configuration is invalid, the error states the bounds, and the default bound is the one retained

#### Scenario: The existing suites

- **WHEN** the settings and launcher suites of both integrations run after the change
- **THEN** they pass without a change to an expected setting name, state or status detail

### Requirement: The two peers SHALL be compared on the same inputs

The architecture suite MUST resolve the same table of configurations for every peer and fail when two peers differ in
anything but the peer's name, naming the input and the difference. The table MUST include an absent configuration, a
valid pinned one, each malformed setting and each bound.

#### Scenario: One peer's settings type alters a result

- **WHEN** one integration changes what its settings type exposes for a given input
- **THEN** the architecture suite fails and names the input and the peer

#### Scenario: A setting exists for one peer only

- **WHEN** a setting name is declared for one peer without its counterpart for the other
- **THEN** the architecture suite fails and names the setting

### Requirement: An integration SHALL NOT resolve a launch setting itself

The architecture suite MUST fail when a settings type of an integration depends on the file system or on the pin
verification, or carries a launcher command that the operating system would look up on its search path, and MUST name
the type. The launcher invariants MUST be asserted on the implementation that holds them.

#### Scenario: A path check pasted into one integration

- **WHEN** an integration's settings type tests a file itself
- **THEN** the architecture suite fails and names the type

#### Scenario: A bare launcher returns

- **WHEN** the resolution or a settings type is given a bare `java` command as a fallback
- **THEN** the architecture suite fails, as it does today
