# Spec Delta

## Purpose

Define what the local server does with more simultaneous requests than the database can serve.

## ADDED Requirements

### Requirement: The local server SHALL admit a bounded number of requests at once

The local server MUST serve at most a stated number of requests concurrently, and MUST make a request beyond that number
wait for at most a stated time before refusing it.

#### Scenario: A burst queues

- **WHEN** more requests arrive at once than the ceiling and each completes quickly
- **THEN** all of them are answered, none with a database contention failure

#### Scenario: A request that waited too long is refused by name

- **WHEN** a request has waited for the stated bound without being admitted
- **THEN** it is answered `429` with a named code and a `Retry-After`

#### Scenario: A peak does not become contention failures

- **WHEN** the ceiling is reached while a writer holds the database
- **THEN** no request fails with a database busy failure that admission would have prevented

### Requirement: The probes SHALL answer under saturation

The health and readiness routes MUST be admitted apart from the main ceiling.

#### Scenario: Health under a saturated ceiling

- **WHEN** the main ceiling is saturated and requests are waiting
- **THEN** a health request is answered without waiting for a permit

### Requirement: The local and remote ceilings SHALL be consistent

In remote mode the local ceiling MUST NOT be lower than the number of requests the facade admits.

#### Scenario: An admitted remote request does not queue twice

- **WHEN** the facade has admitted a request
- **THEN** the local server admits it without waiting
