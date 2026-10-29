# Spec Delta

## Purpose

Define where the version of a dependency or of a build plugin is decided in the reactor, and how the build refuses a
module that decides one.

## ADDED Requirements

### Requirement: A module POM SHALL state no dependency or plugin version

The POM of every module declared in the root POM MUST NOT contain a `<version>` element other than the version of its
parent and `${project.version}` on a module of the same reactor, and the build MUST fail otherwise.

#### Scenario: A literal version in a module fails the build

- **WHEN** a module POM declares a third-party dependency with a literal version
- **THEN** the architecture suite fails and names the POM and the line

#### Scenario: A property-backed version in a module fails the build

- **WHEN** a module POM declares a dependency or a plugin with a `${...}` version other than `${project.version}`
- **THEN** the suite fails in the same way

#### Scenario: A sibling module is referenced by the project version

- **WHEN** a module depends on another module of the reactor with `${project.version}`
- **THEN** the suite reports nothing for it

#### Scenario: A newly declared module is judged

- **WHEN** a module is added to the `<modules>` of the root POM
- **THEN** its POM is read by the same test without any other change

### Requirement: Every third-party version SHALL be decided in the root POM

Each third-party dependency a module declares MUST obtain its version from the root POM, through an imported BOM or a
managed entry, and each build plugin a module configures MUST obtain its version from the root plugin management.

#### Scenario: The resolved versions are unchanged by the move

- **WHEN** the dependency tree and the generated SBOM are produced before and after this change
- **THEN** every component has the same version in both

#### Scenario: The logging backend follows the logging API

- **WHEN** the SLF4J version is changed in the root POM
- **THEN** the API and the backend packaged by the CLI change together, with no edit to a module POM

### Requirement: The build rule SHALL describe the mechanism as enforced

The repository's build rule MUST state that a module POM carries no version except the two allowed forms, and MUST
cite the test that enforces it.

#### Scenario: The rule page and the POMs agree

- **WHEN** the rule page is read after this change
- **THEN** every module POM satisfies the sentence as written, and the page names the test that would fail otherwise
