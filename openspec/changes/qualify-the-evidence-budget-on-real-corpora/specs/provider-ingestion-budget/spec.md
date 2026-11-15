# Spec delta : provider-ingestion-budget

## ADDED Requirements

### Requirement: The default evidence budget is qualified on measured corpora
The default ingestion budget MUST admit every corpus of the qualification set, which holds the specification corpus of this repository and a deterministic corpus larger than the largest measured project, while keeping the declared margin under its evidence limit. A corpus beyond the limit MUST still be refused explicitly, with nothing published.

#### Scenario: The qualification corpus larger than the largest measured project
- **GIVEN** a deterministic OpenSpec workspace whose current specifications exceed in volume the largest project measured by the qualification
- **WHEN** the provider reads it with the default budget
- **THEN** every requested category is READ with no budget diagnostic
- **AND** the evidence counted stays under the limit by at least the declared margin

#### Scenario: The specification corpus of this repository
- **GIVEN** the `openspec/` directory of this repository, as committed
- **WHEN** the provider reads it with the default budget
- **THEN** no category fails on a budget refusal

#### Scenario: A corpus beyond the limit
- **GIVEN** a deterministic workspace whose counted evidence exceeds the default evidence limit
- **WHEN** the provider reads it
- **THEN** the read is refused with an `INVALID_SOURCE` diagnostic whose message names the evidence quantity, the total and the maximum
- **AND** every requested category is FAILED and nothing is published
