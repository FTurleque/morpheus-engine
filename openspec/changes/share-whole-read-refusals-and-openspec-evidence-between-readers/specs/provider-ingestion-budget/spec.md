# Spec delta : provider-ingestion-budget

## ADDED Requirements

### Requirement: The synthetic and structured Markdown providers word a refusal of a whole read the same way
When the synthetic or the structured Markdown provider refuses a whole read, because its ingestion budget is exceeded or because its source cannot be read, every requested category MUST fail with the same diagnostic code, the same detail keys and the same category reason in both providers; only the provider identifier, the group and the relayed cause MAY differ.

#### Scenario: A budget exceeded by either provider
- **GIVEN** a synthetic workspace and a structured Markdown workspace that each exceed the ingestion budget
- **WHEN** each provider reads its workspace
- **THEN** each read fails every requested category with `INVALID_SOURCE` and the reason `provider ingestion budget exceeded`, and each diagnostic carries the detail keys `provider`, `group` and `exception`
- **AND** no content is returned and nothing is published

#### Scenario: A source that cannot be read by either provider
- **GIVEN** a synthetic workspace and a structured Markdown workspace whose source file cannot be parsed
- **WHEN** each provider reads its workspace
- **THEN** each read fails every requested category with `INVALID_SOURCE` and the same category reason, and each diagnostic carries the detail keys `provider` and `source`
- **AND** no content is returned and nothing is published
