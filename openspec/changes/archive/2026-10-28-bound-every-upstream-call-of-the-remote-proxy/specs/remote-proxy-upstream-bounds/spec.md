# Spec Delta

## Purpose

Define the bounds on every call the remote proxy makes to the local server.

## ADDED Requirements

### Requirement: Every upstream call SHALL have a read timeout

The remote proxy MUST bound the wait for the upstream response on every route, with one stated duration for reads and
one, longer, for writes and administration.

#### Scenario: A write that never answers ends

- **WHEN** the upstream does not answer a WRITE request
- **THEN** the facade answers within the writes' timeout and releases the request's permits

#### Scenario: The privileged ceiling recovers

- **WHEN** as many WRITE requests as the privileged ceiling have timed out
- **THEN** a new WRITE request is admitted

### Requirement: Every proxied response SHALL be read within the memory budget

A response to a WRITE or ADMIN request MUST take a response slot and be bounded in size as a response to a READ is.

#### Scenario: An oversized response to a write is refused

- **WHEN** the upstream answers a WRITE request with a body beyond the bound
- **THEN** the facade refuses it by name and buffers no more than the bound

### Requirement: A timed-out write SHALL be answered as an unknown outcome

When a WRITE or ADMIN request times out upstream, the answer MUST carry a code distinct from a timed-out read and MUST
state that the operation may have completed.

#### Scenario: The client is told to read before retrying

- **WHEN** a WRITE request times out
- **THEN** the code is the writes' own and the message says the state must be read before a retry
