# Spec Delta

## Purpose

Define the record the remote server keeps of who was authorized to do what.

## ADDED Requirements

### Requirement: Every authorized remote request SHALL leave a record that attributes it

The remote server MUST record, for each request it authorizes, the time, the request identifier it returns, the
principal, the role held and the role required, the method, the route template and the status.

#### Scenario: A write is attributed

- **WHEN** a principal with the WRITE role registers a project
- **THEN** the journal holds a record with that principal, `POST`, the template `projects`, the status and the
  identifier returned in `X-Request-Id`

#### Scenario: The request identifier joins the two sides

- **WHEN** a client reports the `X-Request-Id` of a response
- **THEN** exactly the records of that request carry it

### Requirement: A refused authorization SHALL be recorded and a failed authentication SHALL NOT

A request refused for want of a role MUST be recorded with its principal. A request that fails authentication MUST NOT
create a record.

#### Scenario: A reader who tries to write is recorded

- **WHEN** a principal with the READ role calls a WRITE route
- **THEN** the journal holds a record with that principal and the status 403

#### Scenario: An unauthenticated caller cannot fill the journal

- **WHEN** requests arrive without a valid token
- **THEN** the journal does not grow

### Requirement: A record SHALL carry no secret and no caller-chosen text

A record MUST NOT contain a token, a token fingerprint, a path parameter, a query string, a header or a body.

#### Scenario: The template, not the path

- **WHEN** a request is made on a path containing identifiers
- **THEN** the record contains the route template and none of the identifiers

#### Scenario: No token in any form

- **WHEN** the journal is read after authenticated traffic
- **THEN** it contains neither a token nor a fingerprint of one

### Requirement: The journal SHALL be bounded, private and separate from the identity file

The journal MUST be a file of its own, with a bound in size and in generations, created with the permissions of a
private file, and MUST NOT be written into the identity file or its credential audit.

#### Scenario: Rotation at the bound

- **WHEN** the journal reaches its size bound
- **THEN** it is rotated and the number of generations kept does not exceed its bound

### Requirement: A record that cannot be written SHALL have a decided outcome

When a record cannot be written, the server MUST apply the policy the decision record states, MUST count the lost
record, and MUST NOT continue as if it had been written.

#### Scenario: The loss is visible

- **WHEN** the journal cannot be written
- **THEN** the counter of lost records moves, and the request is refused or served as the policy says
