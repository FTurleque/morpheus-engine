# Spec delta : project-ingestion-port

## ADDED Requirements

### Requirement: A refused read SHALL carry the error diagnostics of the provider
The refusal of an incomplete read MUST carry every distinct diagnostic of severity ERROR the provider reported, with its code, its details and its source, in the order the provider reported them, so that a surface can relay what the provider knew. The text of the refusal MUST keep relaying the messages only, bounded as before.

#### Scenario: A refusal whose provider reported an error with details
- **GIVEN** a provider that returns no content and one ERROR diagnostic with details, and one WARNING diagnostic
- **WHEN** a publication reads the workspace through the provider port
- **THEN** the read is refused, the refusal carries the ERROR diagnostic with its details, and does not carry the WARNING
- **AND** nothing is published

#### Scenario: More errors than the text relays
- **GIVEN** a provider that reports more distinct errors than the text of the refusal relays
- **WHEN** the read is refused
- **THEN** the text relays the first messages and counts the rest, and the refusal carries every distinct ERROR diagnostic
- **AND** nothing is published

#### Scenario: A refusal without any provider error
- **GIVEN** a provider that reports a failed category and no diagnostic
- **WHEN** the read is refused
- **THEN** the refusal carries no diagnostic and its text is the one it had before
- **AND** nothing is published
