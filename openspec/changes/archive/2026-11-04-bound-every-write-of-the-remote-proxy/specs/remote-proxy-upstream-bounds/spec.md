# Spec Delta

## Purpose

Define the bounds on every call the remote proxy makes to the local server.

## ADDED Requirements

### Requirement: Every upstream call SHALL have a read timeout

The remote proxy MUST bound the wait for the upstream response on every route: sixty seconds for a READ route, three
hundred seconds for a WRITE or ADMIN route.

#### Scenario: A write that never answers ends

- **WHEN** the upstream does not answer a WRITE request
- **THEN** the facade answers within the writes' timeout and releases the request's permits

#### Scenario: The privileged ceiling recovers

- **WHEN** as many WRITE requests as the privileged ceiling have timed out
- **THEN** a new WRITE request is admitted

#### Scenario: Every role has the deadline of its role

- **WHEN** any route and method of the HTTP route table is authorized
- **THEN** a READ route carries the reads' deadline and a WRITE or ADMIN route the writes' deadline

### Requirement: Every proxied response SHALL be read within the memory budget

A response to a WRITE or ADMIN request MUST take a response slot and be bounded in size as a response to a READ is. A
call that may change state takes its slot once the upstream has answered, and waits for it rather than being refused.

#### Scenario: An oversized response to a write is refused

- **WHEN** the upstream answers a WRITE request with a body beyond the bound
- **THEN** the facade refuses it by name, writes nothing downstream and gives its slot back

### Requirement: A timed-out write SHALL be answered as an unknown outcome

When a WRITE or ADMIN request times out upstream, the answer MUST carry a code distinct from a timed-out read and MUST
state that the operation may have completed.

#### Scenario: The client is told to read before retrying

- **WHEN** a WRITE request times out
- **THEN** the status is 504, the code is `UPSTREAM_WRITE_TIMEOUT` and the message says the state must be read before a
  retry
