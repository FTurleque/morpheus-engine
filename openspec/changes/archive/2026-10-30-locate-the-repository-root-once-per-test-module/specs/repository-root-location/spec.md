# Spec Delta

## Purpose

Define how a test finds the repository it runs in, who owns that decision in each test module, and what refuses a
private copy.

## ADDED Requirements

### Requirement: A test module SHALL locate the repository root through one owner

Each test module whose tests read files of the repository MUST have exactly one type that locates the repository root,
and every test class of that module MUST obtain the root from it. The owner MUST identify the root by one anchor,
documented where it is declared, and MUST find it from the module directory, from the repository root and from any
directory below them.

#### Scenario: A suite run from its module directory

- **WHEN** an architecture suite runs with the module directory as working directory
- **THEN** it receives the directory that holds the reactor's root POM

#### Scenario: A suite run from the repository root

- **WHEN** the same suite runs with the repository root as working directory
- **THEN** it receives the same directory

#### Scenario: A nested checkout

- **WHEN** the suite runs inside a worktree nested in another checkout of the repository
- **THEN** it receives the worktree's own root, not the outer one

### Requirement: A failed location SHALL say where it started and what it looked for

When no ancestor of the working directory is the repository root, the owner MUST fail with an unchecked failure whose
message names the directory the search started from and the anchor it looked for.

#### Scenario: A test run outside the repository

- **WHEN** the owner is asked for the root from a directory that has no repository above it
- **THEN** the failure names that directory and the anchor
- **AND** no test method needs a checked exception clause because of the location

### Requirement: A private repository locator SHALL be refused

The architecture suite MUST fail when a test source other than a named owner declares a method that derives a
repository location by climbing from the working directory, whatever the method is called, and MUST name the file and
the method. A climb that serves another purpose MUST be listed by name with its reason.

#### Scenario: A new suite pastes the locator

- **WHEN** a test class is added with its own `repositoryRoot()` climbing from the working directory
- **THEN** the architecture suite fails and names the file and the method

#### Scenario: A renamed copy

- **WHEN** the pasted method is given a name none of the existing copies used
- **THEN** the suite fails in the same way

#### Scenario: A fixture locator

- **WHEN** a test class climbs from the working directory to find the shared fixtures instead of asking its module's
  owner
- **THEN** the suite fails in the same way

### Requirement: The owners of different modules SHALL stay in step

Where two test modules each have an owner, the architecture suite MUST fail when their anchors or their failure
wording differ.

#### Scenario: One owner is edited alone

- **WHEN** the anchor of one module's owner is changed and the other is not
- **THEN** the architecture suite fails and names both owners
