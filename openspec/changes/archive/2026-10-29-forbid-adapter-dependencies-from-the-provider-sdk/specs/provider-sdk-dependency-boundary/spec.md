# Spec Delta

## Purpose

Define what the published provider SDK may depend on, and that the sibling dependency rules cover every subject they
announce.

## ADDED Requirements

### Requirement: The provider SDK SHALL depend on no adapter

No production class under `com.morpheus.sdk..` MUST depend on a class of a store, of a provider, of an integration, of
the MCP adapter, of the HTTP adapter or of the CLI, and the build MUST fail when one does.

#### Scenario: A dependency from the SDK to a store fails the build

- **WHEN** a class of `morpheus-provider-sdk` references a class under `com.morpheus.store..`
- **THEN** the architecture suite fails, names both classes, and gives the reason that the SDK depends inward only

#### Scenario: The rule judges at least one class

- **WHEN** the rule runs
- **THEN** its subject is not empty, and the package pattern it uses resolves to imported classes

### Requirement: The sibling rules SHALL have every subject they declare

The class that holds the sibling dependency rules MUST declare the package families it takes as subjects, and the build
MUST fail when a declared family is the subject of none of its rules.

#### Scenario: A subject is declared and no rule has it

- **WHEN** a package family is added to the declared subjects without a rule whose subject it is
- **THEN** the architecture suite fails and names the family

#### Scenario: The documentation of the class does not restate the list

- **WHEN** the Javadoc of the class is read after this change
- **THEN** it refers to the declared subjects and does not enumerate them in prose

### Requirement: The repository rules SHALL name the SDK's package as the code does

The pages under `.claude/` that describe the SDK boundary MUST spell the package `com.morpheus.sdk.provider` and cite
the rule that enforces the boundary by its method name.

#### Scenario: The misspelled package is gone

- **WHEN** `.claude/` is searched for `provider.sdk` as a package name
- **THEN** no page uses it
