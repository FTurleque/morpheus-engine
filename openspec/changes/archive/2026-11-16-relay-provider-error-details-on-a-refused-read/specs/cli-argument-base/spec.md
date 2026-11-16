# Spec delta : cli-argument-base

## ADDED Requirements

### Requirement: The error line of a refused project read SHALL relay the provider's error diagnostics
When a CLI command is refused because a project read is incomplete, the one writer of the error line MUST relay the error diagnostics the refusal carries, in the format of a published `sync`: in text, one `diagnostic=` line per diagnostic after the error line; under `--json`, a `diagnostics` member in the one JSON object of the error. The error line, the exit code and the error object of every other failure MUST NOT change.

#### Scenario: A sync refused for an unsupported schema, in text
- **GIVEN** a registered workspace whose OpenSpec schema declares no parent
- **WHEN** `sync` is called without `--json`
- **THEN** the error stream carries the line `MORPHEUS error [4]: <message>` followed by one line `diagnostic=ERROR UNSUPPORTED_PROVIDER_SCHEMA` that shows the details `reason`, `schema` and `supportedSchema`, and the exit code is the state exit code
- **AND** the sync is recorded as failed and nothing is published

#### Scenario: The same refusal under the JSON output
- **GIVEN** the same workspace
- **WHEN** `sync` is called with `--json`
- **THEN** the error stream carries one JSON object with `exitCode`, `code`, `message` and `diagnostics`, whose items carry the code and the details of the diagnostic
- **AND** nothing is published

#### Scenario: A refused read whose provider reported no error
- **GIVEN** a project read refused without any provider error diagnostic
- **WHEN** the command is refused
- **THEN** the error stream carries the error line alone in text, and under `--json` an object whose `diagnostics` has no item

#### Scenario: Any other failure
- **WHEN** a command fails for another reason than an incomplete project read
- **THEN** its error line and its JSON object are the ones it had, with `exitCode`, `code` and `message` only
