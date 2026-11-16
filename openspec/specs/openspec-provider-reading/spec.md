# openspec-provider-reading Specification

## Purpose
Define which OpenSpec workspaces and document dialects the OpenSpec provider reads, how it reports content it
cannot read, and to which file each failure is attributed.

Documentation durable (vue consolidée, diagrammes, traçabilité) : [docs/developer/specifications/openspec-provider-reading.md](../../../docs/developer/specifications/openspec-provider-reading.md).

## Requirements

### Requirement: The probe only claims what the reader can read

The provider SHALL report a schema as SUPPORTED only if a workspace written with that schema's default templates is
read without a FAILED category; a category for which that dialect has no source MUST be reported ABSENT, never
invented.

#### Scenario: A workspace produced by the OpenSpec CLI is read

- **WHEN** the provider reads a workspace whose changes were written from the `spec-driven` templates of a supported
  OpenSpec CLI version
- **THEN** CURRENT_SPECIFICATIONS, REQUIREMENTS, SCENARIOS, CHANGES, REQUIREMENT_DELTAS and IMPLEMENTATION_TASKS are
  READ, and CONSTRAINTS and DESIGN_DECISIONS, which those templates do not structure, are ABSENT

#### Scenario: An unreadable dialect is not reported as supported

- **WHEN** the probe inspects a workspace whose proposals use a dialect the reader does not read
- **THEN** the probe does not report the schema as SUPPORTED without a diagnostic naming the unreadable dialect

### Requirement: Failures are attributed and contained

A malformed document SHALL produce a FAILED category with an INVALID_SOURCE diagnostic whose source is the
workspace-relative path of that document, and the provider MUST NOT let an exception escape `read` for content it
reads.

#### Scenario: A requirement duplicated in a delta

- **WHEN** a change delta declares the same requirement twice under ADDED Requirements
- **THEN** REQUIREMENT_DELTAS is FAILED, the diagnostic names the delta file relative to the workspace, and no
  exception escapes the read

#### Scenario: One malformed change among valid ones

- **WHEN** one change of a workspace has a malformed proposal and the others are valid
- **THEN** the diagnostic names that change's proposal and the categories report the outcome explicitly instead of
  silently dropping the valid changes

### Requirement: Encoding variants are accepted

The provider SHALL accept a leading UTF-8 byte order mark in every file it reads, and a `schema:` value written as a
quoted YAML scalar.

#### Scenario: A BOM before the schema declaration

- **WHEN** `openspec/config.yaml` starts with a UTF-8 BOM followed by `schema: spec-driven`
- **THEN** the probe reports the `spec-driven` schema

#### Scenario: A BOM before a specification title

- **WHEN** a `spec.md` starts with a UTF-8 BOM followed by its title
- **THEN** CURRENT_SPECIFICATIONS is READ

### Requirement: Section boundaries follow Markdown structure

A requirement of a current specification SHALL end at the next requirement or at the next level-two heading, and
headings inside a fenced code block MUST NOT start or end a requirement.

#### Scenario: A requirement example inside a code fence

- **WHEN** a specification shows `### Requirement: X` inside a fenced code block of its Purpose section
- **THEN** no requirement X is published

#### Scenario: Notes after the last requirement

- **WHEN** a `## Notes` section follows the last requirement of a specification
- **THEN** the notes are not part of that requirement's statement

### Requirement: A project schema is read only when it declares that it derives from spec-driven
The probe SHALL report a schema other than `spec-driven` as SUPPORTED only when `openspec/schemas/<schema>/schema.yaml` exists in the workspace and declares `derives: spec-driven`; it MUST then report the declared schema name and the capabilities of `spec-driven`. Any other schema MUST stay UNSUPPORTED with an `UNSUPPORTED_PROVIDER_SCHEMA` diagnostic whose details name the refusal reason.

#### Scenario: A derived schema that declares its parent
- **GIVEN** a workspace whose `openspec/config.yaml` declares `schema: detailed-uml` and whose `openspec/schemas/detailed-uml/schema.yaml` declares `derives: spec-driven`, with specifications and changes written in the `spec-driven` structure
- **WHEN** the provider probes and reads the workspace
- **THEN** the probe is SUPPORTED, reports the schema `detailed-uml` and the same capabilities as for `spec-driven`
- **AND** the categories are read as they are for the same content declared `spec-driven`, with no diagnostic

#### Scenario: A schema that declares no parent
- **GIVEN** a workspace that declares `schema: detailed-uml` and whose `openspec/schemas/detailed-uml/schema.yaml` has no `derives` key
- **WHEN** the provider probes the workspace
- **THEN** the probe is UNSUPPORTED with an `UNSUPPORTED_PROVIDER_SCHEMA` diagnostic naming the schema and `spec-driven` as the supported schema
- **AND** no category is read and nothing is published

#### Scenario: A declared schema whose directory is missing
- **GIVEN** a workspace that declares `schema: detailed-uml` and has no `openspec/schemas/detailed-uml/schema.yaml`
- **WHEN** the provider probes the workspace
- **THEN** the probe is UNSUPPORTED with an `UNSUPPORTED_PROVIDER_SCHEMA` diagnostic whose details say the schema definition is missing
- **AND** nothing is published

#### Scenario: A schema that derives from an unknown parent
- **GIVEN** a workspace whose project schema declares `derives: another-schema`
- **WHEN** the provider probes the workspace
- **THEN** the probe is UNSUPPORTED with an `UNSUPPORTED_PROVIDER_SCHEMA` diagnostic naming the declared parent
- **AND** nothing is published

#### Scenario: A schema definition that cannot be read
- **GIVEN** a workspace whose `openspec/schemas/detailed-uml/schema.yaml` is not a readable regular file, or is a symbolic link
- **WHEN** the provider probes the workspace
- **THEN** the probe is INVALID with a diagnostic naming `openspec/schemas/detailed-uml/schema.yaml` relative to the workspace
- **AND** no exception escapes the probe

### Requirement: The refusal of a project schema says what would make it readable
The message of an `UNSUPPORTED_PROVIDER_SCHEMA` diagnostic MUST name the schema definition file the probe expects, relative to the workspace, and the declaration `derives: spec-driven` that makes a project schema readable, for every refusal reason. It MUST name the declared schema when that name is a valid schema name of at most 32 characters, and MUST NOT copy any other declared value. A refused read that relays this message MUST stay relayable to a caller outside the machine.

#### Scenario: A schema that declares no parent
- **GIVEN** a workspace that declares `schema: detailed-uml` and whose `openspec/schemas/detailed-uml/schema.yaml` has no `derives` key
- **WHEN** the provider probes the workspace
- **THEN** the diagnostic message names `detailed-uml`, `openspec/schemas/detailed-uml/schema.yaml` and `derives: spec-driven`
- **AND** nothing is published

#### Scenario: A declared schema whose definition is missing
- **GIVEN** a workspace that declares `schema: detailed-uml` and has no `openspec/schemas/detailed-uml/schema.yaml`
- **WHEN** the provider probes the workspace
- **THEN** the diagnostic message says that `openspec/schemas/detailed-uml/schema.yaml` must exist and declare `derives: spec-driven`
- **AND** nothing is published

#### Scenario: A schema that derives from another parent
- **GIVEN** a workspace whose project schema declares `derives: another-schema`
- **WHEN** the provider probes the workspace
- **THEN** the diagnostic message says that the definition declares another parent and that only `derives: spec-driven` is read, the parent itself staying in the detail `declaredParent`
- **AND** nothing is published

#### Scenario: A declared name that is not a schema name, or is too long to repeat
- **GIVEN** a workspace whose `openspec/config.yaml` declares a schema whose name is not a valid schema name, or a valid name longer than 32 characters whose definition declares no parent
- **WHEN** the provider probes the workspace
- **THEN** the diagnostic message does not contain the declared name, names `openspec/schemas/<schema>/schema.yaml` and `derives: spec-driven`, and the name stays in the detail `schema`
- **AND** nothing is published

#### Scenario: The refused read stays relayable
- **GIVEN** a workspace refused for each reason in turn, with a schema name of 32 characters
- **WHEN** a publication reads it through the provider port and refuses it
- **THEN** the text of the refusal is accepted by the filter that guards a caller outside the machine, and contains the message of the diagnostic
- **AND** nothing is published
