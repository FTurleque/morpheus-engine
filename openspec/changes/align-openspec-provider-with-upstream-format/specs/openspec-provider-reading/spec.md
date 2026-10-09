# Spec Delta

## Purpose

Define which OpenSpec workspaces and document dialects the OpenSpec provider reads, how it reports content it
cannot read, and to which file each failure is attributed.

## ADDED Requirements

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
