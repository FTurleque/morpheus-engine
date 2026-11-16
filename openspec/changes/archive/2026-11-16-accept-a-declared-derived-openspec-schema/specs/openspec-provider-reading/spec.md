# Spec delta : openspec-provider-reading

## ADDED Requirements

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
