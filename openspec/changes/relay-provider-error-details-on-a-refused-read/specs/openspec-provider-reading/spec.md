# Spec delta : openspec-provider-reading

## ADDED Requirements

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
