# suppressed-failure-trace Specification

## Purpose
Define what the product leaves behind when it deliberately does not propagate a failure.

## Requirements

### Requirement: A deliberately swallowed failure SHALL be counted under a named family

Every `catch` of the product that does not rethrow MUST either record the failure - increment the counter of its family
and emit an event when a sink is active - or appear in a closed list with the reason it is not a failure.

#### Scenario: A broken optional adapter is visible

- **WHEN** a reasoning adapter present on the class path fails to load
- **THEN** the adapter is absent from the list as today, and the counter of the adapter-discovery family has moved by
  one

#### Scenario: The counter moves with logs off

- **WHEN** a failure is swallowed while the operational log mode is off
- **THEN** the counter of its family is incremented all the same

#### Scenario: A new silent catch fails the build

- **WHEN** a `catch` that neither rethrows nor records is added outside the closed list
- **THEN** the architecture suite fails and names the file and the line

### Requirement: The trace SHALL name the family and the type and SHALL NOT carry the message

The event of a suppressed failure MUST carry its family and the simple name of the failure type, and MUST NOT carry the
failure's message.

#### Scenario: A message that names a location stays out

- **WHEN** a swallowed failure has a message naming a server path
- **THEN** the emitted event contains the family and the type, and the path appears nowhere in it

### Requirement: The counters SHALL be served with the metrics the product already exposes

The counters of suppressed failures MUST be part of the metrics the local and the remote servers already serve, without
a new route.

#### Scenario: An operator reads them

- **WHEN** the metrics are requested after a swallowed failure
- **THEN** the response contains the counter of that family with its value
