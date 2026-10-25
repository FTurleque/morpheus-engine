# project-ingestion-port Specification

## Purpose

Define how the content of a project reaches a published snapshot, and what is refused on the way.

## Requirements

### Requirement: The publication path SHALL read a project through the provider port

Every capability that reads the content of a workspace to publish or to analyse it MUST obtain that content through
`SpecificationContentReader`. A transport adapter MUST NOT depend on a provider's type that implements neither
`SpecificationContentReader` nor `SpecificationProvider`.

#### Scenario: A sync reads through the port

- **WHEN** a project is synced from the CLI or over HTTP
- **THEN** its content is the one the provider's `SpecificationContentReader` returned

#### Scenario: A transport depends on a reader outside the port

- **WHEN** a class of `api`, `cli` or `mcp` comes to depend on a provider type that implements neither port
- **THEN** the architecture suite fails and names the class

#### Scenario: The same path with another provider

- **WHEN** the publication use case is given the OpenSpec, the Markdown and the synthetic reader in turn on their
  fixtures
- **THEN** each produces a published snapshot, and the test that proves it is part of the anti-lock-in suite

### Requirement: An incomplete read SHALL NOT be published

A sync MUST refuse, by name, a read result that carries no content, in which a requested category failed, or in which
a requested category was read partially because a source was rejected. The refusal MUST name the categories, the sync
MUST be recorded as failed, and the active snapshot MUST be unchanged. A category read partially for another reason,
or one the provider does not support, MUST be published with a diagnostic that names it.

#### Scenario: One group of sources cannot be read

- **WHEN** a sync reads a workspace whose changes cannot be parsed while its specifications can
- **THEN** the sync is refused with a named refusal that lists the categories not read, and the project's active
  snapshot is the one it had before

#### Scenario: A category that is simply absent

- **WHEN** a workspace has no change at all and everything else is read
- **THEN** the sync publishes

#### Scenario: A requirement the provider could not normalize

- **WHEN** a sync reads a workspace in which one requirement of a delta follows a fence that is never closed
- **THEN** the sync publishes, and its diagnostics name the requirement and the fence

#### Scenario: A category the provider does not support

- **WHEN** the provider reports a published category as unsupported
- **THEN** the content is published and a warning names the category

#### Scenario: The refusal over HTTP discloses no server path

- **WHEN** the refused sync was requested through the remote server
- **THEN** the response names the categories and carries no path of the server

### Requirement: A complete read SHALL be published as the port returned it

A workspace whose published categories are all read MUST be published with exactly the content the provider port
returned, so that a re-sync of an unchanged workspace publishes the same business content.

#### Scenario: A project is synced again without any source change

- **WHEN** a published project is synced again without any source change
- **THEN** the sync reports no requirement, scenario, change or traceability difference

### Requirement: The scanned directories SHALL be stated by the provider module

The directories a sync scans for a project MUST be stated once, in the module of the provider that reads them. A
transport adapter MUST NOT name a provider's source directory.

#### Scenario: A transport names the directory

- **WHEN** the literal of a provider's source directory appears in a source file of `api`, `cli` or `mcp`
- **THEN** the architecture suite fails and names the file
