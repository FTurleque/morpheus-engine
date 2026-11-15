# provider-ingestion-budget Specification

## Purpose
Bound the work a provider does to ingest one workspace, and word each refusal so that the operator can tell which
quantity was exceeded and where. Durable page: /docs/developer/specifications/provider-ingestion-budget.md.

## Requirements

### Requirement: A cumulative budget refusal names the whole read, not the file being read
When a quantity counted over the whole ingestion session exceeds its budget, the refusal MUST state that the total of the whole read exceeds the budget, with that total and the maximum, and MUST name the file being read only as the place where the read stopped. A refusal of a per-document quantity MUST name the document as the one that exceeds the budget.

#### Scenario: Evidence accumulated over many small files
- **GIVEN** a workspace whose cited evidence exceeds the evidence budget only when summed over many specifications, the last one read being smaller than the budget
- **WHEN** the provider reads the workspace
- **THEN** the refusal says that evidence bytes for the whole read exceed the budget, gives the session total and the maximum, and names the last file as where the read stopped
- **AND** the refusal does not say that this file exceeds the budget, and nothing is published

#### Scenario: One document larger than the per-document budget
- **GIVEN** a workspace with one document larger than the per-document byte budget
- **WHEN** the provider reads that document
- **THEN** the refusal names that document as exceeding the document budget, with the maximum, and with its size only when the size was measured
- **AND** nothing is published

#### Scenario: A bounded read that stops before the total is known
- **GIVEN** a read stopped by its byte bound before the document was fully measured, while a cumulative budget has less room left than that document
- **WHEN** the refusal is written
- **THEN** it says that the total of the whole read would be more than the maximum, without printing a measured value it does not have
