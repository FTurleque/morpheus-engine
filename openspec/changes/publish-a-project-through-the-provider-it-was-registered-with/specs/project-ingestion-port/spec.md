# Spec delta : project-ingestion-port

## ADDED Requirements

### Requirement: A publication SHALL read a project through the reader of its registered provider

A sync and a change analysis MUST read the content of a project through the `SpecificationContentReader` of the
provider recorded for the project, from the CLI and over HTTP alike. A project whose recorded provider is not packaged
by the launcher MUST be refused by name, before any read, and nothing MUST be published.

#### Scenario: A Structured Markdown project is synced
- **GIVEN** a project registered with the provider `markdown`
- **WHEN** it is synced through `sync` or `POST /api/v1/projects/{projectId}/sync`
- **THEN** a snapshot is published whose content is the one the Structured Markdown reader returned
- **AND** the project's active snapshot is the new one

#### Scenario: An OpenSpec project is synced as before
- **GIVEN** a project registered with the provider `openspec`
- **WHEN** it is synced
- **THEN** the published content is the one the OpenSpec reader returned, unchanged from a sync before this change

#### Scenario: A change of a Structured Markdown project is analysed
- **GIVEN** a project registered with the provider `markdown` that has an active snapshot
- **WHEN** `analyze-change` is run on one of its changes
- **THEN** the proposal is read through the Structured Markdown reader
- **AND** nothing is published

#### Scenario: The recorded provider is not packaged
- **GIVEN** a project whose recorded provider is not packaged by the running launcher
- **WHEN** it is synced
- **THEN** the sync is refused with a refusal that names the provider
- **AND** no snapshot is published and the sync state records the failure

### Requirement: A sync SHALL scan the directories its registered provider states

The source inventory of a sync MUST be scanned under the directories that the provider recorded for the project
states, and under no directory that another provider states.

#### Scenario: A Structured Markdown project changes its source
- **GIVEN** a synced project registered with the provider `markdown`
- **WHEN** its Structured Markdown source is edited and the project is synced again
- **THEN** the sync reports the source change and publishes a new snapshot

#### Scenario: An OpenSpec project keeps its scanned directory
- **GIVEN** a project registered with the provider `openspec`
- **WHEN** it is synced
- **THEN** the inventory holds the sources under the directories the OpenSpec provider states, as before this change

### Requirement: No transport class SHALL construct a specification provider

A class of `com.morpheus.api`, `com.morpheus.cli` or `com.morpheus.mcp` MUST NOT construct a type of a provider
module, except the process wiring that the architecture suite names. The architecture suite MUST fail and name the
class that does.

#### Scenario: A command constructs a reader
- **GIVEN** a CLI command class that calls the constructor of `OpenSpecSpecificationContentReader`
- **WHEN** the architecture suite runs
- **THEN** it fails and names that class and the constructed type

#### Scenario: The process wiring builds the catalogue
- **GIVEN** the named process wiring that constructs every packaged provider and its reader
- **WHEN** the architecture suite runs
- **THEN** the rule passes, and it fails if the named wiring no longer constructs any provider
