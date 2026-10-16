# third-party-log-capture Specification

## Purpose
Define what happens to what the libraries embedded in MORPHEUS log.

## Requirements

### Requirement: Third-party warnings and errors SHALL reach the operational sink when one is active

When the operational log mode is active, a record logged at warning level or above by a library embedded in the product
MUST be emitted through the operational sink, redacted like any other event. When the mode is off, nothing MUST be
written.

#### Scenario: A failed MCP handshake is heard

- **WHEN** the operational log mode is active and the MCP client reports a failure through its logger
- **THEN** one event carries the logger name, the level and the redacted message

#### Scenario: Off stays silent

- **WHEN** the operational log mode is off
- **THEN** no third-party record is written anywhere, as before this change

#### Scenario: The protocol stream stays clean

- **WHEN** the product runs as an MCP STDIO server with logs active
- **THEN** standard output carries protocol frames only

### Requirement: The product SHALL ship exactly one logging binding

The distributed product MUST carry one SLF4J provider, and that provider MUST be the one that routes to the operational
sink.

#### Scenario: No second binding

- **WHEN** the packaged application's class path is listed
- **THEN** it contains one SLF4J provider and no `slf4j-nop`
