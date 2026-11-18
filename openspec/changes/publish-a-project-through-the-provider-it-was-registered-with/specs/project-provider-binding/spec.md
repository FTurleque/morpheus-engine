# Spec delta : project-provider-binding

## Purpose

Define what a registered project keeps of the specification provider it was registered with, how that provider is
determined at registration, and how the write capability of the project is resolved from it. Durable page:
`docs/developer/specifications/project-provider-binding.md`.

## ADDED Requirements

### Requirement: A registered project SHALL keep the provider it was registered with

The registration of a project MUST record the identifier of the provider determined for its workspace, and every read
of the project from a store MUST return that identifier. The memory and SQLite stores MUST return the same identifier
for the same registration.

#### Scenario: A Structured Markdown workspace is registered
- **GIVEN** a workspace that only the Structured Markdown provider supports
- **WHEN** it is registered through `projects add` or `POST /api/v1/projects`
- **THEN** the registration succeeds and returns the project identifier
- **AND** the project read back from the store carries the provider `markdown`

#### Scenario: The two stores agree
- **GIVEN** the same registration applied to the memory store and to the SQLite store
- **WHEN** the project is read back from each
- **THEN** both return the same provider identifier

#### Scenario: The same root is registered again
- **GIVEN** a project already registered on a workspace root with the provider `openspec`
- **WHEN** the same root is registered again
- **THEN** the existing project identifier is returned
- **AND** its recorded provider is unchanged and no second project is persisted

### Requirement: Registration SHALL refuse a workspace that no packaged provider supports

The registration MUST probe the workspace with every provider packaged by the launcher and MUST refuse, by the
refusal `NO_PROVIDER_FOUND`, a workspace that none of them reports as supported. A refused registration MUST persist
nothing.

#### Scenario: A directory without any specification source
- **GIVEN** an existing directory that holds neither an OpenSpec tree nor a Structured Markdown source
- **WHEN** it is registered through the CLI or over HTTP
- **THEN** the registration is refused with `NO_PROVIDER_FOUND`
- **AND** no project is persisted

### Requirement: Registration SHALL report the provider it retains among several supporting ones

For a workspace that more than one packaged provider supports, the registration MUST retain the provider of highest
priority declared by the launcher, MUST report the retained provider and every other supporting provider in its
response, and MUST NOT choose by the order of provider identifiers.

#### Scenario: A workspace with an OpenSpec tree and a Structured Markdown source
- **GIVEN** a workspace that both the OpenSpec and the Structured Markdown providers support, OpenSpec being declared
  with the higher priority
- **WHEN** it is registered
- **THEN** the response reports the provider `openspec` as retained and `markdown` as another supporting provider
- **AND** the project is persisted with the provider `openspec`

#### Scenario: Two supporting providers with the same declared priority
- **GIVEN** a launcher that declares two providers with the same priority, both supporting the workspace
- **WHEN** it is registered
- **THEN** the registration is refused with `MULTIPLE_PROVIDER_MATCHES`, naming both providers
- **AND** no project is persisted

### Requirement: A project registered before its provider was recorded SHALL read back with the OpenSpec provider

A project persisted by a release that recorded no provider MUST read back, after the schema upgrade, with the provider
`openspec`, the only provider such a release published with, and its identity, root and snapshots MUST be unchanged.

#### Scenario: A database written by the last released schema is upgraded
- **GIVEN** a database at the last released schema level that holds a registered project with an active snapshot
- **WHEN** the current program opens and upgrades it
- **THEN** the project reads back with the provider `openspec`
- **AND** its identifier, its root and its active snapshot are the ones it had before

### Requirement: The write capability of a project SHALL be resolved from its registered provider

The resolution of `WRITE_CHANGE` for a project MUST probe the provider recorded for the project, and only it. The
capability MUST be granted only when that provider supports the workspace and declares `WRITE_CHANGE`; otherwise it
MUST be denied with a reason that names the provider.

#### Scenario: The registered provider does not declare the capability
- **GIVEN** a project registered with the provider `openspec`, which declares no `WRITE_CHANGE`
- **WHEN** a lifecycle transition is applied to one of its changes
- **THEN** the transition is refused because the write capability is denied, and the reason names `openspec`
- **AND** no lifecycle mutation is persisted

#### Scenario: Another provider supports the same root
- **GIVEN** a project registered with the provider `openspec` whose workspace also holds a Structured Markdown source
- **WHEN** the write capability of the project is resolved
- **THEN** only the `openspec` provider is probed, and the denial does not report several providers
