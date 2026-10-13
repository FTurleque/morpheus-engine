# Spec Delta

## Purpose

Define the package each module of the reactor owns, how the build holds module and package together, and where the
shared MCP transport lives.

## ADDED Requirements

### Requirement: Every module with production sources SHALL own one declared root package

Each module declared in the root POM that carries sources under `src/main/java` MUST have one root package recorded
in a table held by the architecture tests, every production package of the module MUST sit under that root, and the
build MUST fail when a module has no entry or an entry has no module.

#### Scenario: A new module without an entry fails the build

- **WHEN** a module with production sources is added to the `<modules>` of the root POM and not to the table
- **THEN** the architecture suite fails and names the module

#### Scenario: A class outside its module's package fails the build

- **WHEN** a production class of a module is placed in a package that is not under the module's declared root
- **THEN** the suite fails and names the module and the package

#### Scenario: An entry for a module that no longer exists fails the build

- **WHEN** a module is removed from the root POM and its entry stays
- **THEN** the suite fails and names the entry

### Requirement: Two modules SHALL NOT share or nest a root package without saying so

No module's root package MUST equal another module's root or lie inside it, unless the table declares that nesting
with its reason; and an entry whose package does not follow from the module's name MUST carry its reason.

#### Scenario: An undeclared nesting fails the build

- **WHEN** a module's sources are placed under another module's root package and the table does not declare it
- **THEN** the suite fails and names both modules

#### Scenario: The known departures are explained

- **WHEN** the table is read
- **THEN** the entries of the MCP transport, of the provider SDK and of the provider testkit each state why their
  package is not the one their module name suggests

### Requirement: The shared MCP transport SHALL live outside the integrations' and the MCP adapter's packages

The classes of `morpheus-mcp-transport` MUST reside under a root package that is neither under
`com.morpheus.integration..` nor under `com.morpheus.mcp..`, and MUST keep depending on no other MORPHEUS package.

#### Scenario: A rule about integrations no longer reaches the transport

- **WHEN** a rule selects `com.morpheus.integration..`
- **THEN** it selects classes of the MINOS and NEXUS integrations only

#### Scenario: The transport rule still judges the transport

- **WHEN** `theMcpTransportDependsOnNoOtherMorpheusPackage` runs after the move
- **THEN** its subject is the transport's classes and is not empty, and a dependency from the transport to any other
  MORPHEUS package still fails the build

### Requirement: Moving the transport SHALL relax no prohibition

Every module that could not depend on the transport before the move MUST still be refused that dependency by a rule
that names the transport's package.

#### Scenario: The HTTP adapter still cannot use the transport

- **WHEN** a class of `com.morpheus.api..` references a class of the transport after the move
- **THEN** the architecture suite fails, as it did before

#### Scenario: Providers and stores still cannot use the transport

- **WHEN** a class of a provider or of a store references a class of the transport
- **THEN** the suite fails, as it did before

### Requirement: The rule page and the decision record SHALL name the package the rule judges

The repository rule on the transport and ADR-0109 MUST name the transport's package as the sources declare it.

#### Scenario: A reader looking for the transport finds it

- **WHEN** the rule page's entry on the transport is read after this change
- **THEN** it gives the package the rule selects and the module that ships it
